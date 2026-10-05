package com.kadikular.quantimium.superposition;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.RelayModuleBlockEntity;
import com.kadikular.quantimium.block.entity.SuperpositionPodBlockEntity;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.item.SophonBinding;
import com.kadikular.quantimium.network.OpenPodScreenPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Relay: a hub pod reaching pods that hold no double. A pod with a Relay module in its cradle
 * offers, besides your doubles, every pod its Tesseracts are bound to. Picking an empty one sends a
 * spare double there (the nearest on its network that is listed and not your Anchor), which takes
 * {@value #FORMING_TICKS} ticks to form; then you swap into it, unless you have stepped out of the hub,
 * in which case the double stays where it went. The hub pays for both the sending and the swap.
 *
 * <p>It is a hub and nothing more: the bound pods cannot use it. Coming back is an ordinary swap, since
 * the body you left in the hub waits there.
 *
 * <p>From the module itself, a double in a bound pod can be recalled into its Sophon slot, and a Sophon
 * in the slot sent out to an empty bound pod, each for what a swap between the hub and that pod costs.
 */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class Relay {

    /** How long a sent double takes to form in the pod it is sent to. */
    public static final int FORMING_TICKS = 100;

    private record Trip(GlobalPos hub, GlobalPos target, UUID sophon, long ends, String targetName) {}

    private static final Map<UUID, Trip> TRIPS = new HashMap<>();

    private Relay() {}

    // ---- finding things ----

    /** The Relay module in {@code pod}'s cradle, if it is a whole pod with one. */
    @Nullable
    public static RelayModuleBlockEntity hubOf(SuperpositionPodBlockEntity pod) {
        if (!pod.isFormed() || !pod.hasModule(PodModule.RELAY) || pod.getLevel() == null) return null;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (pod.getLevel().getBlockEntity(pod.getBlockPos().below().relative(side)) instanceof RelayModuleBlockEntity relay) {
                return relay;
            }
        }
        return null;
    }

    /** The pod a Tesseract bound to either of its halves means, loading it if it has to. */
    @Nullable
    public static SuperpositionPodBlockEntity podAt(MinecraftServer server, @Nullable GlobalPos bound) {
        if (bound == null) return null;
        ServerLevel level = server.getLevel(bound.dimension());
        if (level == null) return null;
        level.getChunk(bound.pos());
        if (level.getBlockEntity(bound.pos()) instanceof SuperpositionPodBlockEntity pod) return pod;
        return level.getBlockEntity(bound.pos().below()) instanceof SuperpositionPodBlockEntity pod ? pod : null;
    }

    /** {@code owner}'s double in the pod a Tesseract is bound to (either half), if one waits there. */
    @Nullable
    public static UUID doubleAt(SophonRegistry registry, UUID owner, GlobalPos bound) {
        GlobalPos below = GlobalPos.of(bound.dimension(), bound.pos().below());
        for (SophonRegistry.Sophon sophon : registry.of(owner)) {
            if (!sophon.entry().inPod()) continue;
            GlobalPos at = sophon.entry().pod().get();
            if (at.equals(bound) || at.equals(below)) return sophon.id();
        }
        return null;
    }

    /**
     * The pods on the hub's network: those its Tesseracts are bound to. A Tesseract may be bound to either
     * half of a pod, so both positions are kept; only a pod's lower half ever holds a double.
     */
    private static Set<GlobalPos> network(RelayModuleBlockEntity relay) {
        Set<GlobalPos> network = new HashSet<>();
        for (int slot = 0; slot < RelayModuleBlockEntity.LINKS; slot++) {
            GlobalPos bound = relay.link(slot);
            if (bound == null) continue;
            network.add(bound);
            network.add(GlobalPos.of(bound.dimension(), bound.pos().below()));
        }
        return network;
    }

    /**
     * The double a trip to {@code target} would send: the nearest of {@code owner}'s on the hub's network
     * that is listed, not the Anchor's, and not already at the target. Doubles off the network are never
     * touched. Other dimensions come last.
     */
    @Nullable
    private static SophonRegistry.Sophon spare(SophonRegistry registry, UUID owner, Set<GlobalPos> network, GlobalPos target) {
        GlobalPos anchor = registry.anchor(owner);
        SophonRegistry.Sophon best = null;
        double bestDistance = Double.MAX_VALUE;
        for (SophonRegistry.Sophon sophon : registry.of(owner)) {
            SophonRegistry.Entry entry = sophon.entry();
            if (!entry.inPod() || !entry.listed()) continue;
            GlobalPos at = entry.pod().get();
            if (!network.contains(at) || at.equals(anchor) || at.equals(target)) continue;
            double distance = at.dimension().equals(target.dimension())
                    ? at.pos().distSqr(target.pos()) : Double.MAX_VALUE / 2;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = sophon;
            }
        }
        return best;
    }

    /** The id a relay destination goes by on the pod's screen: stable, and not any Sophon's. */
    public static UUID destinationId(GlobalPos pod) {
        return UUID.nameUUIDFromBytes(("relay:" + pod.dimension().identifier() + ":" + pod.pos().asLong())
                .getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The empty pods this hub reaches, for its screen: each bound pod not holding one of {@code player}'s
     * doubles, with the cost of sending a double there and swapping into it.
     */
    public static List<OpenPodScreenPayload.Destination> destinations(ServerPlayer player, SuperpositionPodBlockEntity hub) {
        RelayModuleBlockEntity relay = hubOf(hub);
        if (relay == null) return List.of();
        SophonRegistry registry = SophonRegistry.get(player.level().getServer());
        GlobalPos here = hub.globalPos();
        Set<GlobalPos> network = network(relay);
        List<OpenPodScreenPayload.Destination> list = new ArrayList<>();
        for (int slot = 0; slot < RelayModuleBlockEntity.LINKS; slot++) {
            SuperpositionPodBlockEntity target = podAt(player.level().getServer(), relay.link(slot));
            if (target == null || target == hub || !target.canUse(player)) continue;
            GlobalPos there = target.globalPos();
            if (doubleAt(registry, player.getUUID(), there) != null) continue;
            SophonRegistry.Sophon spare = spare(registry, player.getUUID(), network, there);
            int flags = OpenPodScreenPayload.FLAG_RELAY | (spare == null ? OpenPodScreenPayload.FLAG_NO_SPARE : 0);
            int cost = Superposition.cost(here, there)
                    + (spare == null ? 0 : Superposition.cost(spare.entry().pod().get(), there));
            int distance = there.dimension().equals(here.dimension()) ? (int) Math.sqrt(there.pos().distSqr(here.pos())) : -1;
            list.add(new OpenPodScreenPayload.Destination(destinationId(there), target.displayName().getString(),
                    there.dimension().identifier().toString(), there.pos(), distance, cost, flags));
        }
        return list;
    }

    // ---- trips ----

    /** Sends a spare double to the relay destination {@code destination}, and swaps {@code player} in when it has formed. */
    public static void startTrip(ServerPlayer player, BlockPos hubPos, UUID destination) {
        if (!(player.level().getBlockEntity(hubPos) instanceof SuperpositionPodBlockEntity hub)) return;
        RelayModuleBlockEntity relay = hubOf(hub);
        String refusal = null;
        if (!hub.canUse(player)) refusal = "not_your_pod";
        else if (!hub.contains(player)) refusal = "step_inside";
        else if (hub.hasDouble()) refusal = "occupied";
        else if (relay == null) refusal = "no_relay";
        else if (TRIPS.containsKey(player.getUUID())) refusal = "trip_underway";
        if (refusal != null) {
            refuse(player, refusal);
            return;
        }
        SuperpositionPodBlockEntity target = null;
        for (int slot = 0; slot < RelayModuleBlockEntity.LINKS && target == null; slot++) {
            SuperpositionPodBlockEntity bound = podAt(player.level().getServer(), relay.link(slot));
            if (bound != null && destinationId(bound.globalPos()).equals(destination)) target = bound;
        }
        SophonRegistry registry = SophonRegistry.get(player.level().getServer());
        if (target == null || !target.canUse(player)) {
            refuse(player, "no_double");
            return;
        }
        if (!target.isFormed()) {
            refuse(player, "target_unformed");
            return;
        }
        if (target.hasDouble()) {
            refuse(player, "occupied_there");
            return;
        }
        GlobalPos here = hub.globalPos();
        GlobalPos there = target.globalPos();
        SophonRegistry.Sophon spare = spare(registry, player.getUUID(), network(relay), there);
        if (spare == null) {
            refuse(player, "no_spare");
            return;
        }
        SuperpositionPodBlockEntity source = podAt(player.level().getServer(), spare.entry().pod().get());
        if (source == null || !spare.id().equals(source.occupant())) {
            refuse(player, "no_spare");
            return;
        }
        int send = Superposition.cost(source.globalPos(), there);
        int swap = Superposition.cost(here, there);
        if (hub.getEnergyStorage().getEnergyStored() < send + swap) {
            refuse(player, "no_power");
            return;
        }

        hub.getEnergyStorage().consume(send);
        source.releaseDouble();
        target.claim(player);
        target.receiveDouble(spare.id());
        registry.placed(spare.id(), there, target.displayName().getString(), target.modules(), target.isListed());
        Superposition.podEffect((ServerLevel) source.getLevel(), source.getBlockPos(), Superposition.EFFECT_FOLD);
        Superposition.podEffect((ServerLevel) target.getLevel(), target.getBlockPos(), Superposition.EFFECT_FORMING);
        long ends = player.level().getServer().overworld().getGameTime() + FORMING_TICKS;
        TRIPS.put(player.getUUID(), new Trip(here, there, spare.id(), ends, target.displayName().getString()));
        player.sendOverlayMessage(Component.translatable("message.quantimium.relay.sent", target.displayName())
                .withStyle(ChatFormatting.AQUA));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (TRIPS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, Trip>> trips = TRIPS.entrySet().iterator();
        while (trips.hasNext()) {
            Map.Entry<UUID, Trip> entry = trips.next();
            Trip trip = entry.getValue();
            ServerPlayer player = player(server, entry.getKey());
            SuperpositionPodBlockEntity hub = podAt(server, trip.hub());
            boolean inside = player != null && hub != null && player.level() == hub.getLevel() && hub.contains(player);
            if (!inside) {
                // Stepping out calls the swap off; the double has already gone, and stays where it went.
                if (player != null) player.sendSystemMessage(Component.translatable("message.quantimium.relay.cancelled",
                        trip.targetName()).withStyle(ChatFormatting.GOLD));
                trips.remove();
                continue;
            }
            if (now < trip.ends()) {
                if ((trip.ends() - now) % 20 == 0) {
                    player.sendOverlayMessage(Component.translatable("message.quantimium.relay.forming",
                            trip.targetName(), (trip.ends() - now) / 20).withStyle(ChatFormatting.AQUA));
                }
                continue;
            }
            trips.remove();
            // The trip was asked for from the hub, so it goes ahead even if that pod is unlisted.
            Superposition.swap(player, hub.getBlockPos(), trip.sophon(), true);
        }
    }

    /** A player by id: an online one, or failing that one in a level but not on the list (a fake one). */
    @Nullable
    private static ServerPlayer player(MinecraftServer server, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) return player;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getPlayerByUUID(id) instanceof ServerPlayer found) return found;
        }
        return null;
    }

    // ---- recall and send, from the module ----

    /** Folds {@code player}'s double in the pod slot {@code slot} leads to into the Relay's Sophon slot. */
    public static void recall(ServerPlayer player, RelayModuleBlockEntity relay, int slot) {
        SuperpositionPodBlockEntity hub = hubPod(player, relay);
        if (hub == null) return;
        if (!relay.getInventory().getStackInSlot(RelayModuleBlockEntity.SOPHON_SLOT).isEmpty()) {
            refuse(player, "relay_full");
            return;
        }
        SuperpositionPodBlockEntity target = podAt(player.level().getServer(), relay.link(slot));
        SophonRegistry registry = SophonRegistry.get(player.level().getServer());
        if (target == null || !target.hasDouble() || !target.canUse(player)
                || registry.entry(target.occupant()) == null
                || !registry.entry(target.occupant()).owner().equals(player.getUUID())) {
            refuse(player, "no_double");
            return;
        }
        int cost = Superposition.cost(hub.globalPos(), target.globalPos());
        if (hub.getEnergyStorage().getEnergyStored() < cost) {
            refuse(player, "no_power");
            return;
        }
        hub.getEnergyStorage().consume(cost);
        ItemStack sophon = target.foldOut();
        relay.getInventory().setStackInSlot(RelayModuleBlockEntity.SOPHON_SLOT, sophon);
        Superposition.podEffect((ServerLevel) target.getLevel(), target.getBlockPos(), Superposition.EFFECT_FOLD);
    }

    /** Unfolds the Sophon in the Relay's slot into the empty pod slot {@code slot} leads to. */
    public static void send(ServerPlayer player, RelayModuleBlockEntity relay, int slot) {
        SuperpositionPodBlockEntity hub = hubPod(player, relay);
        if (hub == null) return;
        ItemStack sophon = relay.getInventory().getStackInSlot(RelayModuleBlockEntity.SOPHON_SLOT);
        SophonBinding binding = sophon.get(ModDataComponents.SOPHON.get());
        if (binding == null) {
            refuse(player, "relay_empty");
            return;
        }
        SuperpositionPodBlockEntity target = podAt(player.level().getServer(), relay.link(slot));
        if (target == null) {
            refuse(player, "no_double");
            return;
        }
        int cost = Superposition.cost(hub.globalPos(), target.globalPos());
        if (hub.getEnergyStorage().getEnergyStored() < cost) {
            refuse(player, "no_power");
            return;
        }
        ItemStack sent = sophon.copy();
        target.unfold(player, sent);
        if (!sent.isEmpty()) return;
        hub.getEnergyStorage().consume(cost);
        relay.getInventory().setStackInSlot(RelayModuleBlockEntity.SOPHON_SLOT, ItemStack.EMPTY);
    }

    /** The whole pod the Relay sits in, if {@code player} may use it. */
    @Nullable
    private static SuperpositionPodBlockEntity hubPod(ServerPlayer player, RelayModuleBlockEntity relay) {
        SuperpositionPodBlockEntity hub = PodStructure.podOver(relay.getLevel(), relay.getBlockPos());
        if (hub == null || !hub.isFormed()) {
            refuse(player, "unformed");
            return null;
        }
        if (!hub.canUse(player)) {
            refuse(player, "not_your_pod");
            return null;
        }
        return hub;
    }

    private static void refuse(ServerPlayer player, String reason) {
        player.sendOverlayMessage(Component.translatable("message.quantimium.pod." + reason)
                .withStyle(ChatFormatting.RED));
    }
}

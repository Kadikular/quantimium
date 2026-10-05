package com.kadikular.quantimium.superposition;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import java.util.Set;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.server.players.NameAndId;
import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.QuantimiumAdvancements;
import com.kadikular.quantimium.block.SuperpositionPodBlock;
import com.kadikular.quantimium.block.entity.SuperpositionPodBlockEntity;
import com.kadikular.quantimium.entity.FieldDouble;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModEntities;
import com.kadikular.quantimium.item.TetherItem;
import com.kadikular.quantimium.network.OpenPodScreenPayload;
import com.kadikular.quantimium.network.PodActionPayload;
import com.kadikular.quantimium.network.PodEffectPayload;
import com.kadikular.quantimium.network.SuperpositionFlashPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Moving between bodies. You are always in exactly one: a swap moves you into a double waiting in
 * another pod, and the body you stood in stays behind in the pod you left, as a double. Bodies are
 * never made by travelling, only moved; the Unfolding Array is the only place a new one comes from.
 *
 * <p>Your health, hunger, effects, inventory and XP go with you. A double holds nothing.
 *
 * <p>The death save is the same move, forced: with an Anchor (a pod with a Rescue module holding one of
 * your doubles), a hit that would kill you instead wakes you in that double. The body that died is let
 * go, so that Sophon is spent; the pod has to be given a new one before it can save you again.
 */
public final class Superposition {

    /** Same-dimension swaps cost this much per block, up to {@link #MAX_LOCAL_COST}. */
    public static final int FE_PER_BLOCK = 100;
    public static final int MAX_LOCAL_COST = 500_000;
    /** A swap into another dimension costs this, however far. */
    public static final int CROSS_DIMENSION_COST = 1_000_000;

    public static final int EFFECT_DEPART = 0;
    public static final int EFFECT_ARRIVE = 1;
    public static final int EFFECT_UNFOLD = 2;
    public static final int EFFECT_FOLD = 3;
    /** Where a rescued body fell: it lets go. */
    public static final int EFFECT_ECHO = 4;
    /** A double sent by a Relay, forming over {@link Relay#FORMING_TICKS}. */
    public static final int EFFECT_FORMING = 5;

    private Superposition() {}

    /** What a swap from {@code from} into a double at {@code to} costs. */
    public static int cost(GlobalPos from, GlobalPos to) {
        if (!from.dimension().equals(to.dimension())) return CROSS_DIMENSION_COST;
        double distance = Math.sqrt(from.pos().distSqr(to.pos()));
        return (int) Math.min(MAX_LOCAL_COST, Math.ceil(distance * FE_PER_BLOCK));
    }

    // ---- the screen ----

    public static void openScreen(ServerPlayer player, SuperpositionPodBlockEntity pod) {
        if (!pod.canUse(player)) {
            refuse(player, "not_your_pod");
            return;
        }
        pod.claim(player);
        PacketDistributor.sendToPlayer(player, view(player, pod));
    }

    private static OpenPodScreenPayload view(ServerPlayer player, SuperpositionPodBlockEntity pod) {
        SophonRegistry registry = SophonRegistry.get(player.level().getServer());
        GlobalPos here = pod.globalPos();
        List<OpenPodScreenPayload.Destination> destinations = destinations(registry, player, here);
        destinations.addAll(Relay.destinations(player, pod));
        return new OpenPodScreenPayload(pod.getBlockPos(), pod.displayName().getString(),
                pod.getEnergyStorage().getEnergyStored(), pod.getEnergyStorage().getMaxEnergyStored(),
                pod.isFormed(), pod.modules(), here.equals(registry.anchor(player.getUUID())), pod.contains(player),
                pod.hasDouble(), registry.count(player.getUUID()), Config.maxDoubles(), registry.hasStash(player.getUUID()),
                pod.isListed(), false, destinations);
    }

    /** The Tether's screen: the same list, seen from where its holder stands, paid for from the Tether. */
    private static OpenPodScreenPayload tetherView(ServerPlayer player, ItemStack tether) {
        SophonRegistry registry = SophonRegistry.get(player.level().getServer());
        GlobalPos here = GlobalPos.of(player.level().dimension(), player.blockPosition());
        return new OpenPodScreenPayload(player.blockPosition(), "", TetherItem.energy(tether), TetherItem.CAPACITY,
                true, 0, false, true, false, registry.count(player.getUUID()), Config.maxDoubles(),
                registry.hasStash(player.getUUID()), true, true, destinations(registry, player, here));
    }

    /**
     * {@code player}'s doubles as seen from {@code here}: those in listed pods, field doubles, Sophons
     * lying where they were knocked out (to recover, not to swap into), and folded ones.
     */
    private static List<OpenPodScreenPayload.Destination> destinations(SophonRegistry registry, ServerPlayer player, GlobalPos here) {
        GlobalPos anchor = registry.anchor(player.getUUID());
        List<OpenPodScreenPayload.Destination> destinations = new ArrayList<>();
        for (SophonRegistry.Sophon sophon : registry.of(player.getUUID())) {
            SophonRegistry.Entry entry = sophon.entry();
            if (entry.pod().isEmpty()) {
                destinations.add(new OpenPodScreenPayload.Destination(sophon.id(), "", "", BlockPos.ZERO, -1, 0,
                        OpenPodScreenPayload.FLAG_FOLDED));
                continue;
            }
            GlobalPos there = entry.pod().get();
            int flags = 0;
            if (entry.inField()) {
                flags |= OpenPodScreenPayload.FLAG_FIELD;
            } else if (entry.dropped()) {
                flags |= OpenPodScreenPayload.FLAG_DROPPED;
            } else if (entry.taken()) {
                flags |= OpenPodScreenPayload.FLAG_TAKEN;
            } else {
                // An unlisted pod keeps its double for its modules, so it is not offered as somewhere to go.
                if (there.equals(here) || !entry.listed()) continue;
                if (there.equals(anchor)) flags |= OpenPodScreenPayload.FLAG_ANCHOR;
                if (entry.hasModule(PodModule.RESCUE)) flags |= OpenPodScreenPayload.FLAG_RESCUE;
            }
            int distance = there.dimension().equals(here.dimension()) ? (int) Math.sqrt(there.pos().distSqr(here.pos())) : -1;
            destinations.add(new OpenPodScreenPayload.Destination(sophon.id(), entry.podName(),
                    there.dimension().identifier().toString(), there.pos(), distance, cost(here, there), flags));
        }
        return destinations;
    }

    // ---- swapping ----

    /** Moves {@code player}, standing in the pod at {@code podPos}, into their double {@code target}. */
    public static void swap(ServerPlayer player, BlockPos podPos, UUID target) {
        swap(player, podPos, target, false);
    }

    /** As above; {@code viaRelay} lets a Relay's trip land in a pod that is not listed. */
    public static void swap(ServerPlayer player, BlockPos podPos, UUID target, boolean viaRelay) {
        if (!(player.level().getBlockEntity(podPos) instanceof SuperpositionPodBlockEntity from)) return;
        MinecraftServer server = player.level().getServer();
        SophonRegistry registry = SophonRegistry.get(server);
        SophonRegistry.Entry entry = registry.entry(target);

        String refusal = null;
        if (!from.canUse(player)) refusal = "not_your_pod";
        else if (!from.contains(player)) refusal = "step_inside";
        else if (!from.isFormed()) refusal = "unformed";
        else if (from.hasDouble()) refusal = "occupied";
        else if (entry == null || !entry.owner().equals(player.getUUID())) refusal = "no_double";
        if (refusal != null) {
            refuse(player, refusal);
            return;
        }
        Target to = target(player, entry, target, viaRelay);
        if (to == null) return;
        GlobalPos there = to.where();
        ServerLevel targetLevel = to.level();
        int cost = cost(from.globalPos(), there);
        if (from.getEnergyStorage().getEnergyStored() < cost) {
            refuse(player, "no_power");
            return;
        }

        ServerLevel fromLevel = player.level();
        from.getEnergyStorage().consume(cost);
        from.claim(player);
        to.takeOver();
        from.receiveDouble(target);
        registry.placed(target, from.globalPos(), from.displayName().getString(), from.modules(), from.isListed());

        // Flux at both ends; the tear is at the far one, so that is where the anomaly comes through.
        QuantumFlux.emitFromEnergy(fromLevel, podPos, cost / 2, 1.0);
        QuantumFlux.emitFromEnergy(targetLevel, there.pos(), cost / 2);

        podEffect(fromLevel, podPos, EFFECT_DEPART);
        fromLevel.playSound(null, podPos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 1.6f);
        arrive(player, to);
        QuantimiumAdvancements.award(player, "here_and_there", "swapped");
    }

    /**
     * A double found and ready to be swapped into: one waiting in a pod, or a field double standing out
     * in the world.
     */
    private record Target(ServerLevel level, GlobalPos where, @Nullable SuperpositionPodBlockEntity pod,
                          @Nullable FieldDouble field) {

        /** The swapper takes the double over: its pod stands empty, or the field double is gone. */
        void takeOver() {
            if (pod != null) pod.releaseDouble();
            if (field != null) field.discard();
        }
    }

    /** Finds {@code entry}'s double to swap into, or says why not and returns null. */
    @Nullable
    private static Target target(ServerPlayer player, SophonRegistry.Entry entry, UUID id, boolean viaRelay) {
        if (entry.dropped()) {
            refuse(player, "knocked_out_target");
            return null;
        }
        if (entry.taken()) {
            refuse(player, "taken");
            return null;
        }
        if (entry.pod().isEmpty()) {
            refuse(player, "no_double");
            return null;
        }
        GlobalPos there = entry.pod().get();
        ServerLevel level = player.level().getServer().getLevel(there.dimension());
        if (level == null) {
            refuse(player, "no_double");
            return null;
        }
        if (entry.inField()) {
            level.getChunk(there.pos());
            Entity found = entry.entity().map(level::getEntity).orElse(null);
            if (!(found instanceof FieldDouble field)) {
                // Its chunk is loaded but its entities not yet, straight after a restart or a long way off.
                refuse(player, "not_reachable");
                return null;
            }
            return new Target(level, there, null, field);
        }
        SuperpositionPodBlockEntity pod = podAt(level, there.pos());
        if (pod == null || !id.equals(pod.occupant())) {
            // The registry lost track of it somehow; better to say so than to swap into nothing.
            refuse(player, "no_double");
            return null;
        }
        if (!pod.isFormed()) {
            refuse(player, "target_unformed");
            return null;
        }
        if (!pod.isListed() && !viaRelay) {
            refuse(player, "unlisted");
            return null;
        }
        return new Target(level, there, pod, null);
    }

    /** {@code player} wakes in the double: into its pod, or where the field double stood. */
    private static void arrive(ServerPlayer player, Target to) {
        if (to.pod() != null) {
            moveInto(player, to.level(), to.pod());
        } else if (to.field() != null) {
            FieldDouble field = to.field();
            place(player, to.level(), field.getX(), field.getY(), field.getZ(), field.getYRot());
        }
        podEffect(to.level(), to.where().pos(), EFFECT_ARRIVE);
        to.level().playSound(null, to.where().pos(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 1.0f, 1.3f);
        PacketDistributor.sendToPlayer(player, new SuperpositionFlashPayload(SuperpositionFlashPayload.SWAP));
    }

    /** A Sophon for {@code sophon} as an item, bound to its owner, for dropping it back into the world. */
    public static ItemStack sophonFor(MinecraftServer server, UUID sophon) {
        SophonRegistry.Entry entry = SophonRegistry.get(server).entry(sophon);
        UUID owner = entry == null ? net.minecraft.util.Util.NIL_UUID : entry.owner();
        String name = server.services().nameToIdCache().get(owner).map(NameAndId::name).orElse("");
        return com.kadikular.quantimium.item.SophonItem.of(sophon, owner, name);
    }

    // ---- the Tether ----

    /** Opens the Tether's screen for {@code player}, holding one. */
    public static void openTether(ServerPlayer player, ItemStack tether) {
        PacketDistributor.sendToPlayer(player, tetherView(player, tether));
    }

    /**
     * Swaps {@code player} into their double {@code target} from wherever they stand, paid for from the
     * Tether in their hand. The body they leave stays standing there as a field double: no pod round it,
     * nothing guarding it.
     */
    public static void tetherSwap(ServerPlayer player, UUID target) {
        ItemStack tether = TetherItem.held(player);
        if (tether.isEmpty()) {
            refuse(player, "no_tether");
            return;
        }
        SophonRegistry registry = SophonRegistry.get(player.level().getServer());
        SophonRegistry.Entry entry = registry.entry(target);
        if (entry == null || !entry.owner().equals(player.getUUID())) {
            refuse(player, "no_double");
            return;
        }
        // From one body in the open to another: the Tether only reaches doubles kept in pods.
        if (entry.inField()) {
            refuse(player, "field_from_tether");
            return;
        }
        Target to = target(player, entry, target, false);
        if (to == null) return;
        ServerLevel fromLevel = player.level();
        GlobalPos here = GlobalPos.of(fromLevel.dimension(), player.blockPosition());
        int cost = cost(here, to.where());
        boolean free = player.getAbilities().instabuild;
        if (!free && TetherItem.energy(tether) < cost) {
            refuse(player, "tether_power");
            return;
        }

        FieldDouble left = ModEntities.FIELD_DOUBLE.get().create(fromLevel, EntitySpawnReason.COMMAND);
        if (left == null) return;
        left.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0f);
        left.become(player, target);
        if (!free) TetherItem.setEnergy(tether, TetherItem.energy(tether) - cost);
        to.takeOver();
        fromLevel.addFreshEntity(left);
        registry.inField(target, here, left.getUUID());

        QuantumFlux.emitFromEnergy(fromLevel, player.blockPosition(), cost / 2, 1.0);
        QuantumFlux.emitFromEnergy(to.level(), to.where().pos(), cost / 2);
        podEffect(fromLevel, player.blockPosition(), EFFECT_DEPART);
        fromLevel.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.6f);
        arrive(player, to);
        QuantimiumAdvancements.award(player, "out_of_body", "tethered");
    }

    /** Puts {@code player} into the pod: standing on its floor, facing out of its open front. */
    private static void moveInto(ServerPlayer player, ServerLevel level, SuperpositionPodBlockEntity pod) {
        BlockPos pos = pod.getBlockPos();
        float yaw = pod.getBlockState().getValue(SuperpositionPodBlock.FACING).toYRot();
        place(player, level, pos.getX() + 0.5, pos.getY() + 0.0625, pos.getZ() + 0.5, yaw);
    }

    private static void place(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw) {
        player.stopRiding();
        player.teleportTo(level, x, y, z, Set.of(), yaw, 0.0f, true);
        // A fake player (another mod's, or a test's) has no connection to move it, so place it outright.
        if (player.level() == level && player.distanceToSqr(x, y, z) > 0.01) player.snapTo(x, y, z, yaw, 0.0f);
        player.setDeltaMovement(0, 0, 0);
        player.resetFallDistance();
    }

    /** Something done on a pod's screen other than swapping: the Anchor, the listing, a new name, or folding. */
    public static void act(ServerPlayer player, BlockPos podPos, int action) {
        if (!(player.level().getBlockEntity(podPos) instanceof SuperpositionPodBlockEntity pod)) return;
        if (!pod.canUse(player)) {
            refuse(player, "not_your_pod");
            return;
        }
        switch (action) {
            case PodActionPayload.TOGGLE_ANCHOR -> {
                toggleAnchor(player, podPos);
                return;
            }
            case PodActionPayload.TOGGLE_LISTED -> pod.toggleListed();
            case PodActionPayload.FOLD -> {
                if (pod.hasDouble()) pod.fold(player);
            }
            default -> {
                return;
            }
        }
        PacketDistributor.sendToPlayer(player, view(player, pod));
    }

    public static void rename(ServerPlayer player, BlockPos podPos, String name) {
        if (!(player.level().getBlockEntity(podPos) instanceof SuperpositionPodBlockEntity pod) || !pod.canUse(player)) return;
        pod.rename(name);
        PacketDistributor.sendToPlayer(player, view(player, pod));
    }

    // ---- the stash ----

    /**
     * Opens {@code player}'s stash: one per player, reachable from anywhere while a pod with a Stash
     * module holds one of their doubles.
     */
    public static void openStash(ServerPlayer player) {
        SophonRegistry registry = SophonRegistry.get(player.level().getServer());
        if (!registry.hasStash(player.getUUID())) {
            refuse(player, "no_stash");
            return;
        }
        StashContainer stash = new StashContainer(registry.stash(player.getUUID()));
        player.openMenu(new SimpleMenuProvider((id, inventory, who) -> ChestMenu.threeRows(id, inventory, stash),
                Component.translatable("container.quantimium.stash")));
        player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.AMETHYST_BLOCK_CHIME),
                SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 0.8f, 0.6f, player.getRandom().nextLong()));
    }

    // ---- the Anchor ----

    /** Makes the pod its owner's Anchor, or stops it being one. Needs a Rescue module. */
    public static void toggleAnchor(ServerPlayer player, BlockPos podPos) {
        if (!(player.level().getBlockEntity(podPos) instanceof SuperpositionPodBlockEntity pod)) return;
        if (!pod.canUse(player)) {
            refuse(player, "not_your_pod");
            return;
        }
        if (!pod.hasModule(PodModule.RESCUE)) {
            refuse(player, "no_rescue");
            return;
        }
        SophonRegistry registry = SophonRegistry.get(player.level().getServer());
        boolean clearing = pod.globalPos().equals(registry.anchor(player.getUUID()));
        registry.setAnchor(player.getUUID(), clearing ? null : pod.globalPos());
        player.sendOverlayMessage(Component.translatable(clearing ? "message.quantimium.pod.anchor_cleared"
                : "message.quantimium.pod.anchor_set", pod.displayName()).withStyle(ChatFormatting.AQUA));
        PacketDistributor.sendToPlayer(player, view(player, pod));
    }

    /**
     * Saves {@code player} from a death if they have an Anchor to wake in: the Anchor they set, or
     * failing that any pod of theirs with a Rescue module and a double in it. Returns whether it did.
     */
    public static boolean rescue(ServerPlayer player) {
        SophonRegistry registry = SophonRegistry.get(player.level().getServer());
        SuperpositionPodBlockEntity anchor = findAnchor(player, registry);
        if (anchor == null) return false;
        ServerLevel fell = player.level();
        BlockPos fellAt = player.blockPosition();

        UUID spent = anchor.occupant();
        anchor.releaseDouble();
        registry.remove(spent);

        // A body that has never been hurt: whatever was killing the old one stays with it.
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.clearFire();
        player.setTicksFrozen(0);
        player.setAirSupply(player.getMaxAirSupply());

        podEffect(fell, fellAt, EFFECT_ECHO);
        ServerLevel level = (ServerLevel) anchor.getLevel();
        moveInto(player, level, anchor);
        podEffect(level, anchor.getBlockPos(), EFFECT_ARRIVE);
        level.playSound(null, anchor.getBlockPos(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
        PacketDistributor.sendToPlayer(player, new SuperpositionFlashPayload(SuperpositionFlashPayload.RESCUE));
        player.sendSystemMessage(Component.translatable("message.quantimium.pod.rescued", anchor.displayName())
                .withStyle(ChatFormatting.AQUA));
        QuantimiumAdvancements.award(player, "who_was_that", "rescued");
        return true;
    }

    @Nullable
    private static SuperpositionPodBlockEntity findAnchor(ServerPlayer player, SophonRegistry registry) {
        GlobalPos chosen = registry.anchor(player.getUUID());
        if (chosen != null) return usableAnchor(player, chosen);
        for (SophonRegistry.Sophon sophon : registry.of(player.getUUID())) {
            if (!sophon.entry().inPod() || !sophon.entry().hasModule(PodModule.RESCUE)) continue;
            SuperpositionPodBlockEntity pod = usableAnchor(player, sophon.entry().pod().get());
            if (pod != null) return pod;
        }
        return null;
    }

    @Nullable
    private static SuperpositionPodBlockEntity usableAnchor(ServerPlayer player, GlobalPos where) {
        ServerLevel level = player.level().getServer().getLevel(where.dimension());
        if (level == null) return null;
        SuperpositionPodBlockEntity pod = podAt(level, where.pos());
        if (pod == null || !pod.isFormed() || !pod.hasModule(PodModule.RESCUE) || !pod.hasDouble()) return null;
        SophonRegistry.Entry entry = SophonRegistry.get(player.level().getServer()).entry(pod.occupant());
        return entry != null && entry.owner().equals(player.getUUID()) ? pod : null;
    }

    /** The pod at {@code pos}, loading its chunk if it has to. */
    @Nullable
    private static SuperpositionPodBlockEntity podAt(ServerLevel level, BlockPos pos) {
        level.getChunk(pos);
        return level.getBlockEntity(pos) instanceof SuperpositionPodBlockEntity pod ? pod : null;
    }

    // ---- effects ----

    public static void podEffect(ServerLevel level, BlockPos pos, int kind) {
        PacketDistributor.sendToPlayersTrackingChunk(level, ChunkPos.containing(pos), new PodEffectPayload(pos, kind));
    }

    private static void refuse(ServerPlayer player, String reason) {
        player.sendOverlayMessage(Component.translatable("message.quantimium.pod." + reason)
                .withStyle(ChatFormatting.RED));
    }

    /**
     * Whether the front of an empty pod's glass slides open: someone is close by and not yet inside.
     * Once they step in it closes behind them; a pod holding a double stays sealed.
     */
    public static boolean doorShouldOpen(Level level, SuperpositionPodBlockEntity pod) {
        if (pod.hasDouble() || !pod.isFormed()) return false;
        BlockPos pos = pod.getBlockPos();
        for (Player player : level.players()) {
            if (player.isSpectator() || pod.contains(player)) continue;
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 2.6 * 2.6) return true;
        }
        return false;
    }
}

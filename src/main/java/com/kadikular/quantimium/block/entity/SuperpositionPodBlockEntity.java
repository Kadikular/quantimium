package com.kadikular.quantimium.block.entity;

import net.minecraft.server.players.NameAndId;
import com.kadikular.quantimium.util.LegacyEnergy;
import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import com.kadikular.quantimium.block.SuperpositionPodBlock;
import com.kadikular.quantimium.entity.MirrorEndermite;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.item.SophonBinding;
import com.kadikular.quantimium.item.SophonItem;
import com.kadikular.quantimium.superposition.PodModule;
import com.kadikular.quantimium.superposition.PodStructure;
import com.kadikular.quantimium.superposition.Recovery;
import com.kadikular.quantimium.superposition.SophonRegistry;
import com.kadikular.quantimium.superposition.Superposition;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A Superposition Pod: holds one body, a double of its owner while they are elsewhere, or nobody while
 * they stand in it. The swapping itself is {@link Superposition}'s; this keeps the pod's own state:
 * its power, the double it holds, whether its cradle is whole and what modules the cradle carries.
 *
 * <p>It pays for swaps out of it, from a large buffer filled slowly, so a long trip leaves it needing
 * time to recharge.
 */
public class SuperpositionPodBlockEntity extends BlockEntity implements QuantumEnergyHost, FluxMeterReadout {

    public static final int ENERGY_CAPACITY = 2_000_000;
    public static final int ENERGY_MAX_RECEIVE = 1_000;
    /** How often the cradle is checked, besides whenever one of its blocks changes. */
    private static final int CHECK_INTERVAL = 40;
    public static final int MAX_NAME = 32;
    public static final int CHARGE_FE_PER_TICK = 2_000;
    /** The modules do their work once a second. */
    private static final int MODULE_INTERVAL = 20;
    public static final int REGENERATION_FE_PER_TICK = 200;
    public static final int HARDENING_FE_PER_TICK = 200;
    public static final int WARD_FE_PER_TICK = 50;
    /** How long a buff lasts once given; renewed each second, so it only runs out when the pod stops. */
    public static final int BUFF_TICKS = 50;
    /** The buffs give out from this band of anomaly where the owner stands. */
    public static final FluxBand BUFFS_DECOHERE_AT = FluxBand.CRITICAL;
    /** How far a hostile has to be to notice an unguarded double, and come for it. */
    public static final double PROWL_RANGE = 12.0;
    /** Seconds of something at the glass before an unguarded double is knocked out of its pod. */
    public static final int KNOCK_OUT_STRIKES = 10;

    private final QuantumEnergyStorage energyStorage = new QuantumEnergyStorage(ENERGY_CAPACITY, ENERGY_MAX_RECEIVE, 0) {
        @Override
        protected void onReceived() {
                setChanged();
                if (!powered) sync();
            
        }
    };

    @Nullable
    private UUID owner;
    private String ownerName = "";
    /** The Sophon whose double stands in the pod, or null when it is empty. */
    @Nullable
    private UUID occupant;
    @Nullable
    private Component customName;
    private boolean formed;
    private int modules;
    /** Last synced: has any power at all, which is what lights the rings. */
    private boolean powered;
    /**
     * Whether other pods offer this one's double as somewhere to swap to. Unlisted, it is kept for its
     * modules: swapping into it would take the double, and its buffs with it.
     */
    private boolean listed = true;

    /** Seconds something has spent at the glass of an unguarded double, easing off when nothing is. */
    private int strikes;
    /** Whether the cradle has been checked since the pod loaded. */
    private boolean checked;
    /** Players sneaking inside at the last check, so the screen opens once per crouch. */
    private final Set<UUID> crouched = new HashSet<>();

    // Client-side animation state.
    private float doorOpen;
    private float doorOpenPrevious;
    private long effectStart = Long.MIN_VALUE;
    private int effectKind;

    public SuperpositionPodBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUPERPOSITION_POD_BE.get(), pos, state);
    }

    // ---- state ----

    @Override
    public QuantumEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public boolean isFormed() {
        return formed;
    }

    public int modules() {
        return modules;
    }

    public boolean hasModule(PodModule module) {
        return (modules & module.bit()) != 0;
    }

    public boolean hasDouble() {
        return occupant != null;
    }

    @Nullable
    public UUID occupant() {
        return occupant;
    }

    public boolean isPowered() {
        return powered;
    }

    public GlobalPos globalPos() {
        return GlobalPos.of(level.dimension(), worldPosition);
    }

    /** Whether {@code player} may use this pod: its owner, or anyone while it has none. */
    public boolean canUse(Player player) {
        return owner == null || owner.equals(player.getUUID());
    }

    /** The pod becomes {@code player}'s, if nobody has it yet. */
    public void claim(Player player) {
        if (owner != null) return;
        owner = player.getUUID();
        ownerName = player.getGameProfile().name();
        if (level instanceof ServerLevel server) {
            SophonRegistry.get(server.getServer()).recoveryPod(owner, globalPos(), formed && hasModule(PodModule.RECOVERY));
        }
        sync();
    }

    public boolean isListed() {
        return listed;
    }

    /** Lists the pod as somewhere to swap to, or takes it off the list. */
    public void toggleListed() {
        listed = !listed;
        describe();
        sync();
    }

    /** Renames the pod; a blank name goes back to the default. */
    public void rename(String name) {
        String trimmed = name.strip();
        setCustomName(trimmed.isEmpty() ? null : Component.literal(trimmed.length() > MAX_NAME ? trimmed.substring(0, MAX_NAME) : trimmed));
    }

    public void setCustomName(@Nullable Component name) {
        customName = name;
        describe();
        sync();
    }

    public Component displayName() {
        return customName != null ? customName
                : Component.translatable("block.quantimium.superposition_pod.default_name", worldPosition.getX(), worldPosition.getZ());
    }

    /** The space a body stands in: the capsule's two blocks. */
    public AABB interior() {
        return new AABB(worldPosition).expandTowards(0, 1, 0).deflate(0.05);
    }

    /** Whether {@code player} is standing in this pod. */
    public boolean contains(Player player) {
        return player.blockPosition().equals(worldPosition);
    }

    // ---- the cradle ----

    /** Checks the cradle and lights it; called when one of its blocks changes, and now and then anyway. */
    public void revalidate() {
        if (level == null || level.isClientSide()) return;
        PodStructure.Scan scan = PodStructure.scan(level, worldPosition);
        boolean first = !checked;
        checked = true;
        if (scan.formed() == formed && scan.modules() == modules) {
            // Once after loading, light the cradle anyway, so one built before a change to its look catches up.
            if (first) PodStructure.light(level, worldPosition, formed);
            return;
        }
        formed = scan.formed();
        modules = scan.modules();
        PodStructure.light(level, worldPosition, formed);
        BlockState state = getBlockState();
        if (state.getValue(SuperpositionPodBlock.FORMED) != formed) {
            level.setBlock(worldPosition, state.setValue(SuperpositionPodBlock.FORMED, formed), Block.UPDATE_ALL);
        }
        describe();
        if (owner != null && level instanceof ServerLevel server) {
            SophonRegistry.get(server.getServer()).recoveryPod(owner, globalPos(), formed && hasModule(PodModule.RECOVERY));
        }
        sync();
    }

    /** Tells the registry this pod's name and modules, which is what the swap list shows. */
    private void describe() {
        if (level instanceof ServerLevel server && occupant != null) {
            SophonRegistry.get(server.getServer()).describe(occupant, displayName().getString(), modules, listed);
        }
    }

    // ---- the double ----

    /**
     * Unfolds {@code stack}'s double into this pod. Only its owner can, into an empty whole pod nobody
     * is standing in.
     */
    public void unfold(ServerPlayer player, ItemStack stack) {
        SophonBinding binding = stack.get(ModDataComponents.SOPHON.get());
        if (binding == null || !(level instanceof ServerLevel server)) return;
        String refusal = null;
        if (!binding.owner().equals(player.getUUID())) refusal = "not_yours";
        else if (!canUse(player)) refusal = "not_your_pod";
        else if (!formed) refusal = "unformed";
        else if (occupant != null) refusal = "occupied";
        else if (!server.getEntitiesOfClass(Player.class, interior()).isEmpty()) refusal = "standing";
        SophonRegistry registry = SophonRegistry.get(server.getServer());
        if (refusal == null && registry.entry(binding.id()) == null) refusal = "lost";
        if (refusal != null) {
            player.sendOverlayMessage(Component.translatable("message.quantimium.pod." + refusal)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        claim(player);
        occupant = binding.id();
        registry.placed(binding.id(), globalPos(), displayName().getString(), modules, listed);
        stack.shrink(1);
        Superposition.podEffect(server, worldPosition, Superposition.EFFECT_UNFOLD);
        sync();
    }

    /** Folds the double back into its Sophon and hands it to its owner. */
    public void fold(ServerPlayer player) {
        if (occupant == null || !(level instanceof ServerLevel server)) return;
        if (!canUse(player)) {
            player.sendOverlayMessage(Component.translatable("message.quantimium.pod.not_your_pod")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        ItemStack sophon = release(server);
        if (!player.getInventory().add(sophon)) player.drop(sophon, false);
        Superposition.podEffect(server, worldPosition, Superposition.EFFECT_FOLD);
    }

    /** Folds the double out and returns its Sophon, for a Relay recalling it. */
    public ItemStack foldOut() {
        return level instanceof ServerLevel server && occupant != null ? release(server) : ItemStack.EMPTY;
    }

    /** The pod was broken: its double folds up and drops, so no Sophon is ever lost with a pod. */
    public void broken() {
        if (!(level instanceof ServerLevel server)) return;
        if (owner != null) SophonRegistry.get(server.getServer()).recoveryPod(owner, globalPos(), false);
        if (occupant == null) return;
        ItemStack sophon = release(server);
        Containers.dropItemStack(server, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5, sophon);
    }

    private ItemStack release(ServerLevel server) {
        UUID id = occupant;
        occupant = null;
        SophonRegistry registry = SophonRegistry.get(server.getServer());
        SophonRegistry.Entry entry = registry.entry(id);
        registry.folded(id);
        sync();
        // The Sophon's own record says whose it is; the pod's owner is only who placed the pod.
        UUID whose = entry != null ? entry.owner() : owner != null ? owner : Util.NIL_UUID;
        String name = whose.equals(owner) ? ownerName : nameOf(server, whose);
        return SophonItem.of(id, whose, name);
    }

    private static String nameOf(ServerLevel server, UUID player) {
        return server.getServer().services().nameToIdCache().get(player).map(NameAndId::name).orElse("");
    }

    /** The owner left: this pod now holds the body they left, as the double {@code sophon}. */
    public void receiveDouble(UUID sophon) {
        occupant = sophon;
        describe();
        sync();
    }

    /** The owner took the double over: it is their body now, and the pod stands empty. */
    public void releaseDouble() {
        occupant = null;
        sync();
    }

    // ---- ticking ----

    public static void serverTick(Level level, BlockPos pos, BlockState state, SuperpositionPodBlockEntity pod) {
        if (!(level instanceof ServerLevel server)) return;
        if (server.getGameTime() % CHECK_INTERVAL == pos.hashCode() % CHECK_INTERVAL) pod.revalidate();
        boolean powered = pod.energyStorage.getEnergyStored() > 0;
        if (powered != pod.powered) pod.sync();
        if (server.getGameTime() % 2 == 0) pod.watchCrouches(server);
        if (server.getGameTime() % MODULE_INTERVAL == 0 && pod.formed && pod.occupant != null) {
            pod.support(server);
            pod.guard(server);
        }
    }

    // ---- modules ----

    /**
     * Regeneration and Hardening: the double keeps its owner topped up, anywhere in this dimension, while
     * this pod's chunk is loaded and it has the power. The buffs do not stack: one already running (from
     * another pod, or something stronger) is left be, and this pod pays nothing for it.
     */
    private void support(ServerLevel server) {
        boolean regeneration = hasModule(PodModule.REGENERATION);
        boolean hardening = hasModule(PodModule.HARDENING);
        boolean charge = hasModule(PodModule.CHARGE);
        if ((!regeneration && !hardening && !charge) || owner == null) return;
        ServerPlayer player = server.getServer().getPlayerList().getPlayer(owner);
        if (player == null && server.getPlayerByUUID(owner) instanceof ServerPlayer here) player = here;
        if (player == null || player.level() != server || player.isSpectator() || !player.isAlive()) return;
        if (QuantumFlux.chunk(server, player.blockPosition()).anomalyBand().ordinal() >= BUFFS_DECOHERE_AT.ordinal()) return;
        int spent = 0;
        if (regeneration) spent += keep(player, MobEffects.REGENERATION, REGENERATION_FE_PER_TICK);
        if (hardening) spent += keep(player, MobEffects.RESISTANCE, HARDENING_FE_PER_TICK);
        if (charge) spent += charge(player);
        if (spent > 0) {
            QuantumFlux.emitFromEnergy(server, worldPosition, spent);
            setChanged();
        }
    }

    /** Renews {@code effect} on {@code player} for a second's FE, unless something already keeps it up. */
    private int keep(ServerPlayer player, Holder<MobEffect> effect, int fePerTick) {
        MobEffectInstance running = player.getEffect(effect);
        if (running != null && (running.getAmplifier() > 0 || running.getDuration() > BUFF_TICKS - MODULE_INTERVAL)) return 0;
        int cost = fePerTick * MODULE_INTERVAL;
        if (energyStorage.getEnergyStored() < cost) return 0;
        energyStorage.consume(cost);
        player.addEffect(new MobEffectInstance(effect, BUFF_TICKS, 0, true, false, true));
        return cost;
    }

    /**
     * Charge: tops up whatever holds FE in its owner's inventory (armour, offhand and all), up to
     * {@value #CHARGE_FE_PER_TICK} FE/t between them, as an Entangled Dock does for one item.
     */
    private int charge(ServerPlayer player) {
        int budget = Math.min(CHARGE_FE_PER_TICK * MODULE_INTERVAL, energyStorage.getEnergyStored());
        int moved = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && moved < budget; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            IEnergyStorage battery = LegacyEnergy.item(stack);
            if (battery == null || !battery.canReceive()) continue;
            moved += battery.receiveEnergy(budget - moved, false);
        }
        if (moved > 0) energyStorage.consume(moved);
        return moved;
    }

    /**
     * An unguarded double draws what prowls: hostiles nearby come for the pod, and one at the glass for
     * {@value #KNOCK_OUT_STRIKES} seconds knocks the double out of it, to lie on the floor folded up as its
     * Sophon. Nothing is lost, but the way back is. A powered Ward keeps them off entirely.
     */
    private void guard(ServerLevel server) {
        if (hasModule(PodModule.WARD) && energyStorage.getEnergyStored() >= WARD_FE_PER_TICK * MODULE_INTERVAL) {
            energyStorage.consume(WARD_FE_PER_TICK * MODULE_INTERVAL);
            strikes = 0;
            setChanged();
            return;
        }
        Vec3 centre = Vec3.atBottomCenterOf(worldPosition).add(0, 1, 0);
        // A mite still in the mirror is not in this world, where the double is; one that has breached is.
        List<Monster> near = server.getEntitiesOfClass(Monster.class, new AABB(worldPosition).inflate(PROWL_RANGE, 4, PROWL_RANGE),
                monster -> monster.isAlive() && !(monster instanceof MirrorEndermite mite && !mite.isBreached()));
        boolean atGlass = false;
        for (Monster monster : near) {
            if (monster.distanceToSqr(centre) < 2.4 * 2.4) {
                atGlass = true;
                monster.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            } else if (monster.getTarget() == null) {
                monster.getNavigation().moveTo(centre.x, worldPosition.getY(), centre.z, 1.0);
            }
        }
        if (!atGlass) {
            strikes = Math.max(0, strikes - 1);
            return;
        }
        strikes++;
        server.playSound(null, worldPosition, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.6f, 1.6f);
        server.sendParticles(ParticleTypes.CRIT, centre.x, centre.y, centre.z, 6, 0.3, 0.5, 0.3, 0.1);
        ServerPlayer owning = owner == null ? null : server.getServer().getPlayerList().getPlayer(owner);
        if (strikes >= KNOCK_OUT_STRIKES) {
            strikes = 0;
            UUID knocked = occupant;
            ItemStack sophon = release(server);
            SophonBinding binding = sophon.get(ModDataComponents.SOPHON.get());
            Containers.dropItemStack(server, centre.x, worldPosition.getY() + 0.5, centre.z, sophon);
            SophonRegistry.get(server.getServer()).dropped(knocked, globalPos());
            Superposition.podEffect(server, worldPosition, Superposition.EFFECT_FOLD);
            if (owning != null) owning.sendSystemMessage(Component.translatable("message.quantimium.pod.knocked_out",
                    displayName()).withStyle(ChatFormatting.RED));
            // A Recovery pod elsewhere may bring it straight back; not this one, where it would be knocked out again.
            if (binding != null) Recovery.afterKnockOut(server.getServer(), binding.owner(), knocked, globalPos());
        } else if (owning != null && strikes % 3 == 1) {
            owning.sendOverlayMessage(Component.translatable("message.quantimium.pod.under_attack", displayName())
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    /** Sneaking inside opens the swap screen, once per crouch. */
    private void watchCrouches(ServerLevel server) {
        List<ServerPlayer> inside = server.getEntitiesOfClass(ServerPlayer.class, interior(), this::contains);
        Set<UUID> now = new HashSet<>();
        for (ServerPlayer player : inside) {
            if (!player.isShiftKeyDown()) continue;
            now.add(player.getUUID());
            if (!crouched.contains(player.getUUID()) && player.containerMenu == player.inventoryMenu) {
                Superposition.openScreen(player, this);
            }
        }
        crouched.clear();
        crouched.addAll(now);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, SuperpositionPodBlockEntity pod) {
        pod.doorOpenPrevious = pod.doorOpen;
        float target = Superposition.doorShouldOpen(level, pod) ? 1.0f : 0.0f;
        pod.doorOpen += (target - pod.doorOpen) * 0.18f;
        if (Math.abs(target - pod.doorOpen) < 0.002f) pod.doorOpen = target;
    }

    /** How far the front of the glass has slid open, 0 to 1. */
    public float doorOpen(float partialTick) {
        return doorOpenPrevious + (doorOpen - doorOpenPrevious) * partialTick;
    }

    /** Starts a swap or fold effect in the renderer, from a server message. */
    public void playEffect(int kind) {
        effectKind = kind;
        effectStart = level == null ? 0 : level.getGameTime();
    }

    public int effectKind() {
        return effectKind;
    }

    public long effectStart() {
        return effectStart;
    }

    // ---- readouts, saving and syncing ----

    @Override
    public MutableComponent fluxMeterLine() {
        return Component.translatable("item.quantimium.flux_meter.pod", displayName(),
                        occupant != null ? Component.translatable("item.quantimium.flux_meter.pod.double", ownerName)
                                : Component.translatable("item.quantimium.flux_meter.pod.empty"),
                        String.format("%,d", energyStorage.getEnergyStored()))
                .withStyle(ChatFormatting.DARK_AQUA);
    }

    private void sync() {
        powered = energyStorage.getEnergyStored() > 0;
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        CompoundTag tag = new CompoundTag();
        saveLegacy(tag, NbtCompat.registries(level));
        NbtCompat.write(out, tag);
    }

    private void saveLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Energy", energyStorage.getEnergyStored());
        writeShared(tag, registries);
    }

    private void writeShared(CompoundTag tag, HolderLookup.Provider registries) {
        if (owner != null) tag.store("Owner", net.minecraft.core.UUIDUtil.CODEC, owner);
        tag.putString("OwnerName", ownerName);
        if (occupant != null) tag.store("Occupant", net.minecraft.core.UUIDUtil.CODEC, occupant);
        if (customName != null) tag.putString("CustomName", NbtCompat.componentToJson(customName, registries));
        tag.putBoolean("Formed", formed);
        tag.putInt("Modules", modules);
        tag.putBoolean("Powered", powered);
        tag.putBoolean("Listed", listed);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        loadLegacy(NbtCompat.read(in), NbtCompat.lookup(in));
    }

    private void loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Energy")) energyStorage.setEnergy(tag.getIntOr("Energy", 0));
        owner = com.kadikular.quantimium.util.NbtCompat.hasUUID(tag, "Owner") ? com.kadikular.quantimium.util.NbtCompat.getUUID(tag, "Owner") : null;
        ownerName = tag.getStringOr("OwnerName", "");
        occupant = com.kadikular.quantimium.util.NbtCompat.hasUUID(tag, "Occupant") ? com.kadikular.quantimium.util.NbtCompat.getUUID(tag, "Occupant") : null;
        customName = tag.contains("CustomName") ? NbtCompat.componentFromJson(tag.getStringOr("CustomName", ""), registries) : null;
        formed = tag.getBooleanOr("Formed", false);
        modules = tag.getIntOr("Modules", 0);
        powered = tag.getBooleanOr("Powered", false);
        listed = !tag.contains("Listed") || tag.getBooleanOr("Listed", false);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeShared(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (state.getValue(SuperpositionPodBlock.HALF) == DoubleBlockHalf.LOWER) {
            broken();
        }
    }
}

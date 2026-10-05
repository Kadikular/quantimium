package com.kadikular.quantimium.block.entity;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import com.kadikular.quantimium.util.RestrictedItems;
import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.Containers;
import com.kadikular.quantimium.block.DecoherenceProjectorBlock;
import com.kadikular.quantimium.block.SuperpositionPodBlock;
import com.kadikular.quantimium.client.ClientPhaseState;
import com.kadikular.quantimium.entity.MirrorEndermite;
import com.kadikular.quantimium.entity.Veiled;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.init.ModSounds;
import com.kadikular.quantimium.item.AnomaliteCellItem;
import com.kadikular.quantimium.phase.FluxRift;
import com.kadikular.quantimium.phase.FluxRiftManager;
import com.kadikular.quantimium.recipe.EntangledLinks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.items.IItemHandler;
import com.kadikular.quantimium.util.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Picks the most pressing thing in the mirror within range and holds a beam on it: the Veiled
 * first, then mites, then a rift's anchor. A small draw keeps it watching; a far larger one runs the
 * beam, so a Projector with nothing to do is cheap and one fighting is not.
 *
 * <p>Against the Veiled it freezes and wears it down without aggravating it — there is nobody
 * for it to turn on — and beside a powered hall that makes it a trap. Rifts it drains at under half
 * the Lance's rate, so a big wound still wants a player.
 */
public class DecoherenceProjectorBlockEntity extends BlockEntity implements QuantumEnergyHost, FluxMeterReadout {

    public static final int ENERGY_CAPACITY = 100_000;
    public static final int MAX_RECEIVE = 1_000;
    /** Watching: scanning the mirror for something to beam. */
    public static final int IDLE_FE_PER_TICK = 8;
    /** Beaming. */
    public static final int ACTIVE_FE_PER_TICK = 120;
    /** Anomaly burst out of a Projector when a rift's lightning comes down its beam. */
    public static final double OVERLOAD_ANOMALY = 20.0;
    public static final double RANGE = 16.0;
    public static final double RIFT_DRAIN_PER_TICK = 2.0;
    private static final float MITE_DAMAGE = 3.0f;
    private static final int MITE_PULSE = 10;
    private static final int RETARGET_TICKS = 10;
    /** The orb floats this far out from the face it's mounted on. */
    public static final double ORB_HEIGHT = 1.6;
    /** Cell charge a tick on the Veiled: a full Cell lasts about one full pin. */
    public static final int VEILED_CHARGE_PER_TICK = 6;
    /** Cell charge for each shot at a mite, every half-second. */
    public static final int MITE_CHARGE_PER_SHOT = 5;
    /** Cell charge a tick on a rift. */
    public static final int RIFT_CHARGE_PER_TICK = 1;

    /** FE spent since flux was last emitted for it. */
    private long unemittedFe;
    /** Charge left in the Cell it's burning; the next loads from its slot when this runs out. */
    private int charge;

    /** Its Cells: a stack of them, or a bound Tesseract to draw them through. */
    private final ItemStackHandler cells = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(ModItems.ANOMALITE_CELL.get()) || EntangledLinks.isBound(stack);
        }
    };

    /** What a pipe or hopper sees: Cells go in; nothing comes out. */
    private final ResourceHandler<ItemResource> cellInput =
            RestrictedItems.insertOnly(cells, resource -> resource.is(ModItems.ANOMALITE_CELL.get()));

    private final QuantumEnergyStorage energyStorage =
            new QuantumEnergyStorage(ENERGY_CAPACITY, MAX_RECEIVE, 0) {
                @Override
                protected void onReceived() {
                    setChanged();
                }
            };

    /** Server-side lock. */
    @Nullable
    private UUID targetRift;
    private int targetEntityId = -1;
    /** Synced: what the mirror's beam points at — an entity by id, or a rift's centre. */
    private int syncedEntityId = -1;
    /** Knocked out by a rift's lightning until this tick. */
    private long overloadedUntil;
    @Nullable
    private Vec3 syncedPoint;
    /** Re-light this cell once per load: it was a full cube before it became a pedestal. Transient. */
    private boolean relightPending = true;

    public DecoherenceProjectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DECOHERENCE_PROJECTOR_BE.get(), pos, state);
    }

    @Override
    public QuantumEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public ItemStackHandler getCells() {
        return cells;
    }

    public ResourceHandler<ItemResource> getCellInput() {
        return cellInput;
    }

    /** Charge left in the Cell it's burning. */
    public int getCharge() {
        return charge;
    }

    /** The way it points out from what it's mounted on. */
    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(DecoherenceProjectorBlock.FACING) ? state.getValue(DecoherenceProjectorBlock.FACING) : Direction.UP;
    }

    public Vec3 orb() {
        Direction facing = facing();
        return Vec3.atCenterOf(worldPosition).add(Vec3.atLowerCornerOf(facing.getUnitVec3i()).scale(ORB_HEIGHT - 0.5));
    }

    /**
     * Whether {@code point} is in front of the face it's mounted on: anything above the floor it stands
     * on, below the ceiling it hangs from, out from its wall. It never beams back through its own mount.
     */
    private boolean inFront(Vec3 point) {
        Vec3 normal = Vec3.atLowerCornerOf(facing().getUnitVec3i());
        Vec3 mount = Vec3.atCenterOf(worldPosition).subtract(normal.scale(0.5));
        return point.subtract(mount).dot(normal) > 0.0;
    }

    /**
     * Burns {@code amount} of Cell charge, loading the next Cell when the one it's burning runs out: from
     * its slot, or through a bound Tesseract there. False if there isn't enough.
     */
    private boolean burn(ServerLevel level, int amount) {
        while (charge < amount) {
            int loaded = loadCell(level);
            if (loaded <= 0) return false;
            charge += loaded;
        }
        charge -= amount;
        setChanged();
        return true;
    }

    /** Whether it has Cells to load, or a Tesseract to draw them through: between a mite's shots. */
    private boolean hasCells() {
        ItemStack held = cells.getStackInSlot(0);
        return held.is(ModItems.ANOMALITE_CELL.get()) || EntangledLinks.isLink(held);
    }

    /** Takes one Cell and returns its charge; 0 if there's none to take. */
    private int loadCell(ServerLevel level) {
        ItemStack held = cells.getStackInSlot(0);
        if (held.is(ModItems.ANOMALITE_CELL.get())) {
            return AnomaliteCellItem.charge(cells.extractItem(0, 1, false));
        }
        if (!EntangledLinks.isLink(held)) return 0;
        IItemHandler linked = EntangledLinks.resolve(level, worldPosition, held);
        if (linked == null) return 0;
        for (int slot = 0; slot < linked.getSlots(); slot++) {
            if (!linked.getStackInSlot(slot).is(ModItems.ANOMALITE_CELL.get())) continue;
            ItemStack taken = linked.extractItem(slot, 1, false);
            if (taken.is(ModItems.ANOMALITE_CELL.get())) return AnomaliteCellItem.charge(taken);
        }
        return 0;
    }

    public boolean isActive() {
        return getBlockState().getValue(DecoherenceProjectorBlock.ACTIVE);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DecoherenceProjectorBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (be.relightPending) {
            be.relightPending = false;
            serverLevel.getChunkSource().getLightEngine().checkBlock(pos);
        }
        be.drawFromPod(serverLevel);
        boolean active = be.work(serverLevel);
        if (state.getValue(DecoherenceProjectorBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(DecoherenceProjectorBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Stood on a Superposition Pod's crown, it runs off the pod's buffer, as fast as it could take power
     * from a cable: a turret keeping the Veiled off the double inside.
     */
    private void drawFromPod(ServerLevel level) {
        if (facing() != Direction.UP) return;
        BlockState below = level.getBlockState(worldPosition.below());
        if (!below.is(ModBlocks.SUPERPOSITION_POD.get()) || below.getValue(SuperpositionPodBlock.HALF) != DoubleBlockHalf.UPPER) return;
        if (!(level.getBlockEntity(worldPosition.below(2)) instanceof SuperpositionPodBlockEntity pod)) return;
        int room = Math.min(MAX_RECEIVE, energyStorage.getMaxEnergyStored() - energyStorage.getEnergyStored());
        if (room <= 0) return;
        int moved = pod.getEnergyStorage().consume(Math.min(room, pod.getEnergyStorage().getEnergyStored()));
        if (moved > 0) {
            energyStorage.setEnergy(energyStorage.getEnergyStored() + moved);
            setChanged();
        }
    }

    /** The orb needs open air in front of it; anything placed there shuts the Projector down. */
    public boolean obstructed() {
        return level != null && !level.getBlockState(worldPosition.relative(facing())).isAir();
    }

    /**
     * A rift's lightning down the beam: every last FE burnt out of it, and knocked out for
     * {@code ticks} on top, so even a Projector on a fast cable cannot shrug it off.
     */
    public void overload(int ticks) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        overloadedUntil = serverLevel.getGameTime() + ticks;
        energyStorage.setEnergy(0);
        setChanged();
        lock(serverLevel, -1, null, null);
        // The rift's own anomaly comes back down the beam and bursts out of it.
        QuantumFlux.emit(serverLevel, worldPosition, 0.0, OVERLOAD_ANOMALY);
        Vec3 orb = orb();
        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, orb.x, orb.y, orb.z, 30, 0.25, 0.25, 0.25, 0.3);
    }

    private boolean work(ServerLevel level) {
        long now = level.getGameTime();
        if (obstructed() || now < overloadedUntil) {
            lock(level, -1, null, null);
            return false;
        }
        if (now % 20L == 0L && unemittedFe > 0) {
            // Like any Quantimium machine, it emits flux for the power it spends, once a second.
            QuantumFlux.emitFromEnergy(level, worldPosition, unemittedFe);
            unemittedFe = 0;
        }
        if (energyStorage.consume(IDLE_FE_PER_TICK) < IDLE_FE_PER_TICK) {
            lock(level, -1, null, null);
            return false;
        }
        unemittedFe += IDLE_FE_PER_TICK;
        setChanged();
        if (now % RETARGET_TICKS == 0 || !lockHolds(level)) retarget(level);
        if (targetEntityId < 0 && targetRift == null) return false;
        if (energyStorage.getEnergyStored() < ACTIVE_FE_PER_TICK) return false;
        // Its light comes from an Anomalite Cell: the Veiled burns one fastest, a mite a shot at a time.
        Entity entity = targetRift != null ? null : level.getEntity(targetEntityId);
        boolean miteShot = entity instanceof MirrorEndermite && now % MITE_PULSE == 0;
        int cost = targetRift != null ? RIFT_CHARGE_PER_TICK
                : entity instanceof Veiled ? VEILED_CHARGE_PER_TICK
                : miteShot ? MITE_CHARGE_PER_SHOT : 0;
        if (cost > 0) {
            if (!burn(level, cost)) return false;
        } else if (charge <= 0 && !hasCells()) {
            return false;
        }
        energyStorage.consume(ACTIVE_FE_PER_TICK);
        unemittedFe += ACTIVE_FE_PER_TICK;

        if (targetRift != null) {
            FluxRiftManager.drainUnattended(level, targetRift, RIFT_DRAIN_PER_TICK, worldPosition, orb());
        } else if (entity instanceof Veiled veiled) {
            veiled.decohere(level);
        } else if (entity instanceof MirrorEndermite mite && miteShot) {
            // No attacking entity, so mirror isolation lets it land on a mite.
            mite.hurt(level.damageSources().magic(), MITE_DAMAGE);
        }
        if (now % 40 == 0) {
            Vec3 orb = orb();
            level.playSound(null, orb.x, orb.y, orb.z, ModSounds.LANCE_BEAM.get(), SoundSource.BLOCKS, 1.1f, 0.8f);
        }
        return true;
    }

    /** Still there, in range, and in clear sight — checked every tick, so it never beams through a wall. */
    private boolean lockHolds(ServerLevel level) {
        Vec3 orb = orb();
        if (targetRift != null) {
            return FluxRiftManager.rifts(level).stream()
                    .anyMatch(r -> r.id().equals(targetRift) && inFront(r.centre()) && clear(level, orb, r.centre()));
        }
        if (targetEntityId < 0) return true;
        Entity entity = level.getEntity(targetEntityId);
        return entity != null && entity.isAlive() && entity.distanceToSqr(orb) <= RANGE * RANGE
                && inFront(centre(entity)) && clear(level, orb, centre(entity));
    }

    /** The Veiled before mites, mites before rifts; nearest first within each, and in clear sight. */
    private void retarget(ServerLevel level) {
        Vec3 orb = orb();
        AABB box = new AABB(orb, orb).inflate(RANGE);
        Entity best = null;
        double bestSq = RANGE * RANGE;
        for (Veiled veiled : level.getEntitiesOfClass(Veiled.class, box,
                u -> u.isAlive() && u.captureHall().isEmpty())) {
            double distanceSq = veiled.distanceToSqr(orb);
            if (distanceSq < bestSq && inFront(centre(veiled)) && clear(level, orb, centre(veiled))) {
                best = veiled;
                bestSq = distanceSq;
            }
        }
        if (best == null) {
            for (MirrorEndermite mite : level.getEntitiesOfClass(MirrorEndermite.class, box, LivingEntity::isAlive)) {
                double distanceSq = mite.distanceToSqr(orb);
                if (distanceSq < bestSq && inFront(centre(mite)) && clear(level, orb, centre(mite))) {
                    best = mite;
                    bestSq = distanceSq;
                }
            }
        }
        if (best != null) {
            lock(level, best.getId(), null, null);
            return;
        }
        // A stabilised rift is someone's residue supply, not a threat.
        FluxRift rift = FluxRiftManager.nearest(level, orb, RANGE, true);
        if (rift != null && inFront(rift.centre()) && clear(level, orb, rift.centre())) {
            lock(level, -1, rift.id(), rift.centre());
            return;
        }
        lock(level, -1, null, null);
    }

    private static Vec3 centre(Entity entity) {
        return entity.position().add(0.0, entity.getBbHeight() * 0.55, 0.0);
    }

    private boolean clear(ServerLevel level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    private void lock(ServerLevel level, int entityId, @Nullable UUID rift, @Nullable Vec3 riftPoint) {
        targetEntityId = entityId;
        targetRift = rift;
        if (syncedEntityId == entityId && Objects.equals(syncedPoint, riftPoint)) return;
        syncedEntityId = entityId;
        syncedPoint = riftPoint;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Where the mirror's beam should end on this client, or null when there is nothing to draw. */
    @Nullable
    public Vec3 beamEnd(float partialTick) {
        if (level == null) return null;
        if (syncedPoint != null) return syncedPoint;
        if (syncedEntityId < 0) return null;
        Entity entity = level.getEntity(syncedEntityId);
        if (entity == null) return null;
        return entity.getPosition(partialTick).add(0.0, entity.getBbHeight() * 0.55, 0.0);
    }

    /** Motes torn off the target and drawn back along the beam, for phased viewers. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, DecoherenceProjectorBlockEntity be) {
        if (!state.getValue(DecoherenceProjectorBlock.ACTIVE) || !ClientPhaseState.isActive()) return;
        Vec3 end = be.beamEnd(1.0f);
        if (end == null) return;
        Vec3 orb = be.orb();
        for (int i = 0; i < 2; i++) {
            Vec3 from = end.add((level.getRandom().nextDouble() - 0.5) * 0.5, (level.getRandom().nextDouble() - 0.5) * 0.5,
                    (level.getRandom().nextDouble() - 0.5) * 0.5);
            // Reverse-portal motes cover about 30.5 × their speed over their life, accelerating.
            Vec3 speed = orb.subtract(from).scale(1.0 / 30.5);
            level.addParticle(ParticleTypes.REVERSE_PORTAL, from.x, from.y, from.z, speed.x, speed.y, speed.z);
        }
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
        tag.putInt("Charge", charge);
        tag.put("Cells", cells.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        loadLegacy(NbtCompat.read(in), NbtCompat.lookup(in));
    }

    private void loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        energyStorage.setEnergy(tag.getIntOr("Energy", 0));
        charge = tag.getIntOr("Charge", 0);
        if (tag.contains("Cells")) cells.deserializeNBT(registries, tag.getCompoundOrEmpty("Cells"));
        if (tag.contains("BeamEntity")) {
            syncedEntityId = tag.getIntOr("BeamEntity", 0);
            syncedPoint = tag.contains("BeamX")
                    ? new Vec3(tag.getDoubleOr("BeamX", 0.0), tag.getDoubleOr("BeamY", 0.0), tag.getDoubleOr("BeamZ", 0.0)) : null;
        }
    }

    /** The beam's target rides only on sync; it is recomputed on the server rather than saved. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveLegacy(tag, registries);
        tag.putInt("BeamEntity", syncedEntityId);
        if (syncedPoint != null) {
            tag.putDouble("BeamX", syncedPoint.x);
            tag.putDouble("BeamY", syncedPoint.y);
            tag.putDouble("BeamZ", syncedPoint.z);
        }
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public MutableComponent fluxMeterLine() {
        MutableComponent line;
        if (isActive()) {
            line = Component.translatable("item.quantimium.flux_meter.projector.firing", FluxMeterReadout.fe(ACTIVE_FE_PER_TICK));
        } else if (energyStorage.getEnergyStored() >= IDLE_FE_PER_TICK) {
            line = Component.translatable("item.quantimium.flux_meter.projector.watching", FluxMeterReadout.fe(IDLE_FE_PER_TICK));
        } else {
            line = Component.translatable("item.quantimium.flux_meter.projector.unpowered");
        }
        int spare = cells.getStackInSlot(0).is(ModItems.ANOMALITE_CELL.get()) ? cells.getStackInSlot(0).getCount() : 0;
        Component cell = charge <= 0 && spare == 0
                ? Component.translatable(EntangledLinks.isLink(cells.getStackInSlot(0))
                        ? "item.quantimium.flux_meter.projector.cells_linked" : "item.quantimium.flux_meter.projector.no_cell")
                : Component.translatable("item.quantimium.flux_meter.projector.cell",
                        Math.round(100.0f * charge / AnomaliteCellItem.CAPACITY), spare);
        return line.append(" · ").append(cell).withStyle(ChatFormatting.DARK_AQUA);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        Level level = this.level;
        if (level == null) return;
        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), getCells().getStackInSlot(0));
    }
}

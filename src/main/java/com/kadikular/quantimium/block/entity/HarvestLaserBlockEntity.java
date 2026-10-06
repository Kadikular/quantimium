package com.kadikular.quantimium.block.entity;

import net.minecraft.util.ARGB;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import com.kadikular.quantimium.util.RestrictedItems;
import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.Containers;
import com.kadikular.quantimium.block.AnomaliteCrystalBlock;
import com.kadikular.quantimium.block.ContainmentHallStructure;
import com.kadikular.quantimium.block.HarvestLaserBlock;
import com.kadikular.quantimium.block.RiftLensBlock;
import com.kadikular.quantimium.client.ClientPhaseState;
import com.kadikular.quantimium.entity.MirrorEndermite;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.flux.MirrorMiteSpawner;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.phase.OverlayBlockEdit;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.items.IItemHandler;
import com.kadikular.quantimium.util.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * Fires through a Rift Lens at an Anomalite crystal up to {@link #REACH} blocks out, however it grew,
 * with the ring anywhere between them and nothing else in the way. It waits for the
 * crystal to grow full, then works on it for {@link #HARVEST_TICKS}; the crystal shatters, gone, and
 * the laser keeps its {@link #SHARDS} shards, the same as breaking it. The host grows a new one.
 *
 * <p>Fired through a lens and one wall of an Anomaly Containment Hall's glass, it reaches the Veiled
 * the hall holds instead, and draws a {@link ModItems#VEIL_THREAD} out of it every
 * {@link #THREAD_TICKS}. One laser to a hall at a time.
 *
 * <p>The tear is a hole in the world, and it leaks: anomaly into the chunk with every crystal, and
 * now and then a mite, never at Low, rarely at Medium, more often the hotter the chunk. Contained,
 * nothing gets out.
 */
public class HarvestLaserBlockEntity extends BlockEntity implements QuantumEnergyHost, FluxMeterReadout {

    public static final int ENERGY_CAPACITY = 50_000;
    public static final int MAX_RECEIVE = 1_000;
    /** While the tear is open. */
    public static final int FE_PER_TICK = 60;
    /** Firing time for one full crystal: fifteen seconds. */
    public static final int HARVEST_TICKS = 300;
    /** While drawing thread out of a held Veiled. */
    public static final int THREAD_FE_PER_TICK = 120;
    /** Drawing time for one thread: three minutes. */
    public static final int THREAD_TICKS = 3_600;
    /** How far out the crystal may be, counting from the laser. */
    public static final int REACH = 6;
    /** Shards from one full crystal, as breaking it gives. */
    public static final int SHARDS = 3;
    /** Anomaly each crystal lets into the chunk. */
    public static final double ANOMALY_PER_HARVEST = 15.0;
    /** Chance in a thousand that a crystal lets a mite through, by the chunk's anomaly band. */
    private static final int[] MITE_PER_MILLE = {0, 30, 115, 270, 490};
    /** No more mites get out while this many are already about the ring, so an unwatched farm can't fill up. */
    public static final int MITE_CAP = 4;
    private static final double MITE_CAP_RADIUS = 32.0;
    /** How often the work done is sent to watchers, for the shimmer and the tear to build. */
    private static final int SYNC_TICKS = 20;
    /** How long the tear stays ragged after something gets through it. */
    public static final int LEAK_TICKS = 40;

    private static final DustParticleOptions SHIMMER =
            new DustParticleOptions(ARGB.colorFromFloat(1.0f, 0.85f, 0.75f, 1.0f), 0.45f);

    /** Why it is or isn't firing, for the Flux Meter. */
    public enum Line {
        FIRING("firing"),
        DRAWING("drawing"),
        NO_RING("no_ring"),
        BLOCKED("blocked"),
        NO_CRYSTAL("no_crystal"),
        GROWING("growing"),
        FULL("full"),
        HALL_BUSY("hall_busy"),
        UNPOWERED("unpowered");

        private final String key;

        Line(String key) {
            this.key = key;
        }
    }

    public static final int SHARD_SLOT = 0;
    public static final int THREAD_SLOT = 1;

    /** Shards in one slot, thread in the other. */
    private final ItemStackHandler output = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(slot == THREAD_SLOT ? ModItems.VEIL_THREAD.get() : ModItems.ANOMALITE_SHARD.get());
        }
    };

    /** What a pipe or hopper sees: shards and thread come out; nothing goes in. */
    private final ResourceHandler<ItemResource> outputPort = RestrictedItems.takeOnly(output);

    private final QuantumEnergyStorage energyStorage =
            new QuantumEnergyStorage(ENERGY_CAPACITY, MAX_RECEIVE, 0) {
                @Override
                protected void onReceived() {
                    setChanged();
                }
            };

    private int progress;
    private long unemittedFe;
    private Line line = Line.NO_RING;
    /** Synced: when something last got through the tear, so it can shudder. */
    private long lastLeak = Long.MIN_VALUE / 2;
    /** The ring it holds open, if any. */
    @Nullable
    private BlockPos holding;
    /** Synced: how far out the ring and the crystal are, 0 when there's no line. */
    private int ringDistance;
    private int crystalDistance;
    /** Synced: the target is a held Veiled, not a crystal. */
    private boolean drawing;

    public HarvestLaserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HARVEST_LASER_BE.get(), pos, state);
    }

    @Override
    public QuantumEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public ItemStackHandler getOutput() {
        return output;
    }

    public ResourceHandler<ItemResource> getOutputPort() {
        return outputPort;
    }

    public Line line() {
        return line;
    }

    public int progress() {
        return progress;
    }

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(HarvestLaserBlock.FACING) ? state.getValue(HarvestLaserBlock.FACING) : Direction.NORTH;
    }

    public boolean isActive() {
        BlockState state = getBlockState();
        return state.hasProperty(HarvestLaserBlock.ACTIVE) && state.getValue(HarvestLaserBlock.ACTIVE);
    }

    /** The ring on its line, or its own position when there is none. */
    public BlockPos ringPos() {
        return worldPosition.relative(facing(), ringDistance);
    }

    /** The crystal on its line, or its own position when there is none. */
    public BlockPos crystalPos() {
        return worldPosition.relative(facing(), crystalDistance);
    }

    public boolean hasLine() {
        return ringDistance > 0 && crystalDistance > ringDistance;
    }

    /** Jumps the work on to {@code ticks} short of done, so a test needn't wait three minutes. */
    public void nearlyDone(int ticks) {
        progress = Math.max(0, workTicks() - ticks);
        setChanged();
    }

    /** Whether it's drawing thread from a hall rather than harvesting a crystal. */
    public boolean isDrawing() {
        return drawing;
    }

    private int workTicks() {
        return drawing ? THREAD_TICKS : HARVEST_TICKS;
    }

    /** How far through the crystal, or the thread, it is, 0 to 1. */
    public float work(float partialTick) {
        return Math.min(1.0f, (progress + (isActive() ? partialTick : 0.0f)) / workTicks());
    }

    /** Where the beam leaves the housing. */
    public Vec3 muzzle() {
        return Vec3.atCenterOf(worldPosition).add(Vec3.atLowerCornerOf(facing().getUnitVec3i()).scale(0.5));
    }

    /** How ragged the tear is: 1 just after a leak, fading to 0. */
    public float leak(float partialTick) {
        if (level == null) return 0.0f;
        float since = level.getGameTime() - lastLeak + partialTick;
        return since < 0.0f || since > LEAK_TICKS ? 0.0f : 1.0f - since / LEAK_TICKS;
    }

    /**
     * Walks out from the muzzle to find its line: one ring turned along the beam, then a crystal, or
     * a hall's glass with a held Veiled right behind it; nothing solid anywhere else. Records where they are, and says why it would or
     * wouldn't fire, before power.
     */
    private Line survey(Level level) {
        Direction facing = facing();
        int ring = 0;
        int crystal = 0;
        boolean veiled = false;
        Line line = Line.NO_CRYSTAL;
        for (int i = 1; i <= REACH; i++) {
            BlockPos at = worldPosition.relative(facing, i);
            BlockState state = level.getBlockState(at);
            if (state.is(ModBlocks.RIFT_LENS.get()) && ring == 0) {
                if (state.getValue(RiftLensBlock.AXIS) != facing.getAxis()) {
                    line = Line.NO_RING;
                    break;
                }
                ring = i;
                continue;
            }
            // Any crystal will do, whichever way it grew: the tear reaches it from the side as well.
            if (state.is(ModBlocks.ANOMALITE_CRYSTAL.get())) {
                crystal = i;
                line = ring == 0 ? Line.NO_RING
                        : state.getValue(AnomaliteCrystalBlock.AGE) < 3 ? Line.GROWING
                        : output.getStackInSlot(SHARD_SLOT).getCount() + SHARDS > output.getSlotLimit(SHARD_SLOT) ? Line.FULL
                        : Line.FIRING;
                break;
            }
            // Through the one wall of a hall's glass, into the cell with a Veiled held in it.
            if (state.is(ModBlocks.QUANTUM_ATTUNED_GLASS.get()) && ring != 0 && i < REACH) {
                ContainmentHallBlockEntity hall = hallHolding(level, at.relative(facing));
                if (hall != null) {
                    crystal = i + 1;
                    veiled = true;
                    line = output.getStackInSlot(THREAD_SLOT).getCount() >= output.getSlotLimit(THREAD_SLOT) ? Line.FULL
                            : !hall.claimThread(worldPosition, level.getGameTime()) ? Line.HALL_BUSY
                            : Line.DRAWING;
                    break;
                }
            }
            if (!state.getCollisionShape(level, at, CollisionContext.empty()).isEmpty()) {
                line = Line.BLOCKED;
                break;
            }
        }
        if (line == Line.NO_CRYSTAL && ring == 0) line = Line.NO_RING;
        boolean found = line == Line.FIRING || line == Line.GROWING || line == Line.FULL || line == Line.DRAWING;
        int ringAt = found ? ring : 0;
        int crystalAt = found ? crystal : 0;
        boolean hall = found && veiled;
        if (ringAt != ringDistance || crystalAt != crystalDistance || hall != drawing) {
            if (hall != drawing) progress = 0;
            ringDistance = ringAt;
            crystalDistance = crystalAt;
            drawing = hall;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return line;
    }

    /** The hall whose cell {@code cell} is, if it's holding a Veiled. */
    @Nullable
    private static ContainmentHallBlockEntity hallHolding(Level level, BlockPos cell) {
        for (int below = 1; below <= ContainmentHallStructure.WALL_HEIGHT; below++) {
            if (level.getBlockEntity(cell.below(below)) instanceof ContainmentHallBlockEntity hall) {
                return hall.isOccupied() ? hall : null;
            }
        }
        return null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HarvestLaserBlockEntity laser) {
        if (!(level instanceof ServerLevel server)) return;
        Line line = laser.survey(level);
        int cost = line == Line.DRAWING ? THREAD_FE_PER_TICK : FE_PER_TICK;
        if ((line == Line.FIRING || line == Line.DRAWING) && laser.energyStorage.getEnergyStored() < cost) line = Line.UNPOWERED;
        laser.line = line;
        boolean firing = line == Line.FIRING || line == Line.DRAWING;

        if (firing) {
            laser.unemittedFe += laser.energyStorage.consume(cost);
            if (++laser.progress >= laser.workTicks()) {
                laser.progress = 0;
                if (line == Line.DRAWING) laser.drawThread(server);
                else laser.harvest(server);
            } else if (laser.progress % SYNC_TICKS == 0) {
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
            }
            laser.setChanged();
        } else if (line != Line.UNPOWERED && laser.progress != 0) {
            // Losing power pauses the work; losing the crystal or the line starts it over.
            laser.progress = 0;
            laser.setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
        if (server.getGameTime() % 20L == 0L && laser.unemittedFe > 0) {
            QuantumFlux.emitFromEnergy(server, pos, laser.unemittedFe);
            laser.unemittedFe = 0;
        }

        if (state.getValue(HarvestLaserBlock.ACTIVE) != firing) {
            level.setBlock(pos, state.setValue(HarvestLaserBlock.ACTIVE, firing), Block.UPDATE_ALL);
        }
        // A ring stays open only while this laser fires through it; it closes only the ring it opened.
        BlockPos hold = firing ? laser.ringPos() : null;
        if (!Objects.equals(hold, laser.holding)) {
            laser.closeRing();
            laser.holding = hold;
            if (hold != null) setOpen(level, hold, true);
        }
    }

    private static void setOpen(Level level, BlockPos ringPos, boolean open) {
        BlockState ring = level.getBlockState(ringPos);
        if (ring.is(ModBlocks.RIFT_LENS.get()) && ring.getValue(RiftLensBlock.OPEN) != open) {
            level.setBlock(ringPos, ring.setValue(RiftLensBlock.OPEN, open), Block.UPDATE_ALL);
        }
    }

    /** One thread out of the held Veiled. */
    private void drawThread(ServerLevel level) {
        output.insertItem(THREAD_SLOT, new ItemStack(ModItems.VEIL_THREAD.get()), false);
        level.playSound(null, ringPos(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 0.6f);
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** The crystal shatters, its shards go in, and whatever leaks out of the tear gets out. */
    private void harvest(ServerLevel level) {
        BlockPos crystalPos = crystalPos();
        BlockPos ringPos = ringPos();
        // The crystal shatters on the mirror's side: the real world hears only the laser.
        OverlayBlockEdit.allow(() -> com.kadikular.quantimium.phase.MirrorSounds.breakQuietly(level, crystalPos));
        output.insertItem(SHARD_SLOT, new ItemStack(ModItems.ANOMALITE_SHARD.get(), SHARDS), false);
        com.kadikular.quantimium.phase.MirrorSounds.play(level, crystalPos, SoundEvents.AMETHYST_CLUSTER_BREAK,
                SoundSource.BLOCKS, 1.0f, 0.8f + level.getRandom().nextFloat() * 0.2f);
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);

        if (QuantumFlux.chunkContained(level, ringPos)) return;
        QuantumFlux.emit(level, ringPos, 0.0, ANOMALY_PER_HARVEST);
        FluxBand band = QuantumFlux.chunkAnomalyBand(level, ringPos);
        if (level.getRandom().nextInt(1000) >= MITE_PER_MILLE[band.ordinal()]) return;
        if (level.getEntitiesOfClass(MirrorEndermite.class, new AABB(ringPos).inflate(MITE_CAP_RADIUS)).size() >= MITE_CAP) return;
        if (MirrorMiteSpawner.spawnNear(level, ringPos, mite -> mite.setBreached(true)) == null) return;
        lastLeak = level.getGameTime();
        level.playSound(null, ringPos, SoundEvents.ENDERMITE_AMBIENT, SoundSource.HOSTILE, 0.8f, 0.7f);
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Shuts the tear it was holding open; for when the laser stops or goes. */
    public void closeRing() {
        if (level == null || level.isClientSide() || holding == null) return;
        setOpen(level, holding, false);
        holding = null;
    }

    /**
     * The real world sees a faint shimmer on the crystal it can't see; the mirror sees the crystal
     * come apart and drift back into the tear. Both build as the work goes on.
     */
    public static void clientTick(Level level, BlockPos pos, BlockState state, HarvestLaserBlockEntity laser) {
        if (!laser.isActive() || !laser.hasLine()) return;
        // Counted here between syncs, so the build-up runs smoothly.
        if (laser.progress < HARVEST_TICKS) laser.progress++;
        float work = laser.work(0.0f);
        RandomSource random = level.getRandom();
        Vec3 crystal = Vec3.atCenterOf(laser.crystalPos());
        Vec3 ring = Vec3.atCenterOf(laser.ringPos());
        if (ClientPhaseState.isActive()) {
            if (random.nextFloat() > 0.3f + 0.7f * work) return;
            // Portal particles fly from their offset back to where they were spawned: the tear.
            Vec3 from = crystal.add((random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.5,
                    (random.nextDouble() - 0.5) * 0.5).subtract(ring);
            level.addParticle(ParticleTypes.PORTAL, ring.x, ring.y, ring.z, from.x, from.y, from.z);
        } else if (random.nextFloat() < 0.1f + 0.5f * work * work) {
            level.addParticle(SHIMMER, crystal.x + (random.nextDouble() - 0.5) * 0.6,
                    crystal.y + (random.nextDouble() - 0.5) * 0.6, crystal.z + (random.nextDouble() - 0.5) * 0.6,
                    0.0, 0.0, 0.0);
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
        tag.putInt("Progress", progress);
        if (holding != null) tag.putLong("Holding", holding.asLong());
        tag.putInt("Ring", ringDistance);
        tag.putInt("Crystal", crystalDistance);
        tag.putBoolean("Drawing", drawing);
        tag.put("Output", output.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        loadLegacy(NbtCompat.read(in), NbtCompat.lookup(in));
    }

    private void loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        energyStorage.setEnergy(tag.getIntOr("Energy", 0));
        progress = tag.getIntOr("Progress", 0);
        holding = tag.contains("Holding") ? BlockPos.of(tag.getLongOr("Holding", 0L)) : null;
        ringDistance = tag.getIntOr("Ring", 0);
        crystalDistance = tag.getIntOr("Crystal", 0);
        drawing = tag.getBooleanOr("Drawing", false);
        if (tag.contains("Output")) {
            output.deserializeNBT(registries, tag.getCompoundOrEmpty("Output"));
            // Saved before it held thread, with a single slot.
            if (output.getSlots() < 2) {
                ItemStack shards = output.getStackInSlot(SHARD_SLOT);
                output.setSize(2);
                output.setStackInSlot(SHARD_SLOT, shards);
            }
        }
        if (tag.contains("LastLeak")) lastLeak = tag.getLongOr("LastLeak", 0L);
    }

    /** The leak time rides only on sync, so the tear shudders when a mite gets through. Where it
     * holds a ring open stays on the server. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveLegacy(tag, registries);
        tag.putLong("LastLeak", lastLeak);
        tag.remove("Holding");
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public MutableComponent fluxMeterLine() {
        MutableComponent status = line == Line.FIRING || line == Line.DRAWING
                ? Component.translatable("item.quantimium.flux_meter.harvest_laser." + line.key,
                        FluxMeterReadout.fe(line == Line.DRAWING ? THREAD_FE_PER_TICK : FE_PER_TICK),
                        Math.round(100.0f * progress / workTicks()))
                : Component.translatable("item.quantimium.flux_meter.harvest_laser." + line.key);
        int shards = output.getStackInSlot(SHARD_SLOT).getCount();
        if (shards > 0) {
            status.append(" · ").append(Component.translatable("item.quantimium.flux_meter.harvest_laser.shards", shards));
        }
        int threads = output.getStackInSlot(THREAD_SLOT).getCount();
        if (threads > 0) {
            status.append(" · ").append(Component.translatable("item.quantimium.flux_meter.harvest_laser.threads", threads));
        }
        return status.withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        Level level = this.level;
        if (level == null) return;
        for (int slot = 0; slot < getOutput().getSlots(); slot++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), getOutput().getStackInSlot(slot));
        }
        closeRing();
    }
}

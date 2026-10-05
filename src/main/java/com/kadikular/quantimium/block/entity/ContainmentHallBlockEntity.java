package com.kadikular.quantimium.block.entity;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import com.kadikular.quantimium.util.RestrictedItems;
import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import com.kadikular.quantimium.block.AnomalyContainmentHallBlock;
import com.kadikular.quantimium.block.ContainmentHallStructure;
import com.kadikular.quantimium.block.QuantumFoundryStructure;
import com.kadikular.quantimium.block.multiblock.MultiblockHost;
import com.kadikular.quantimium.block.multiblock.PartClaim;
import com.kadikular.quantimium.flux.AnomalyEffects;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.init.ModSounds;
import com.kadikular.quantimium.menu.ContainmentHallMenu;
import com.kadikular.quantimium.phase.VeiledManager;
import com.kadikular.quantimium.recipe.EntangledLinks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import com.kadikular.quantimium.util.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RangedWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * Anomaly Containment Hall: the answer to a base you have made too hot to work in.
 *
 * <p>Unlike the single-block shield it actually removes anomaly, and unlike the Flux Suppressor it
 * leaves flux alone — so a deliberately hot field survives while the anomaly it accrued does not.
 * That is what the arms buy, and why the hall costs several times a suppressor's power for the same
 * footprint: it also raises the shield rating high enough to cover Critical and Singularity.
 *
 * <p>Holding an Veiled also burns Rift Residue, fed in by hand or through any of its arms. Run
 * out and the field starts to fail; a short grace later the occupant walks out. A bound Tesseract in
 * the residue slot feeds it from the linked inventory instead, one residue at a time as it is needed:
 * point it at a Rift Anchor and the hall runs off the rift.
 */
public class ContainmentHallBlockEntity extends BlockEntity implements MultiblockHost, QuantumEnergyHost, FluxMeterReadout {

    public static final int ENERGY_CAPACITY = 400_000;
    public static final int MAX_RECEIVE = 4_000;
    /** Cost of holding the field, before arms. */
    public static final int FE_PER_TICK_BASE = 120;
    public static final int FE_PER_ARM = 60;
    /**
     * Anomaly pulled a second, per arm, from its field, spread by {@link QuantumFlux#share}: over a
     * 3×3 its own chunk gets a fifth.
     */
    public static final double ANOMALY_PULL_PER_ARM = 600.0;
    /** Holding an Veiled strengthens the field: the drain is this much stronger while occupied. */
    public static final double OCCUPIED_PULL_MULTIPLIER = 1.5;

    /** Residue the hall keeps on hand. */
    public static final int RESIDUE_CAPACITY = 16;
    /** Five minutes of holding per residue, flat across arm counts. */
    public static final int TICKS_PER_RESIDUE = 6_000;
    /** How long a starved field holds on, visibly failing, before the occupant walks out. */
    public static final int STARVE_GRACE_TICKS = 600;
    private static final int STARVE_WARNING_INTERVAL = 40;

    public static final int STATUS_UNFORMED = 0;
    public static final int STATUS_NO_POWER = 1;
    public static final int STATUS_CONTAINING = 2;
    public static final int STATUS_OVERWHELMED = 3;

    private final QuantumEnergyStorage energyStorage =
            new QuantumEnergyStorage(ENERGY_CAPACITY, MAX_RECEIVE, 0) {
                @Override
                protected void onReceived() {
                    setChanged();
                }
            };

    private final ItemStackHandler residue = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(ModItems.RIFT_RESIDUE.get()) || EntangledLinks.isBound(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return RESIDUE_CAPACITY;
        }
    };

    /** Residue the linked inventory holds, re-counted each second. Zero without a link. */
    private int linkedResidue;

    /** What the arms expose: residue goes in, nothing comes back out. */
    private final ResourceHandler<ItemResource> residuePort = RestrictedItems.insertOnly(residue, resource -> true);

    /** Holding time left on the residue already burnt. */
    private int burnTicks;
    /** Ticks held with nothing left to burn. Saved and synced: the renderer shows the field failing. */
    private int starvingTicks;
    private int armMask;
    /**
     * Set on load so a hall re-checks its own lighting once per chunk load.
     *
     * <p>Stored light is trusted on load and never recomputed, so a shell built while the glass
     * still blocked light keeps that baked shadow for the life of the save. Deliberately not
     * persisted: it needs to fire again after every load, not once ever.
     */
    private boolean relightPending = true;
    private int statusCode = STATUS_UNFORMED;
    /** Holding an Veiled. Saved and synced: the renderer draws the occupant only when there is one. */
    private boolean occupied;
    /** Ticks without power while occupied; the field holds on through a short outage, not a long one. */
    private int unpoweredTicks;
    /** How long a held occupant waits in a slack field before it walks out. */
    private static final int RELEASE_AFTER_TICKS = 200;
    private int currentPowerUse;
    private int averagePowerUse;
    private int powerSampleIndex;
    private final int[] powerSamples = new int[20];
    private int syncedAnomalyOrdinal;
    private int syncedSurchargePercent;
    private int syncedPassiveDrain;
    private int syncedLoadPercent;
    /** Anomaly pulled in the last second, for the screen between pulls. */
    private double lastPulled;
    /** Anomaly actually pulled last tick, scaled by 100 so it survives the container-data short. */
    private int syncedPullScaled;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energyStorage.getEnergyStored() & 0xFFFF;
                case 1 -> (energyStorage.getEnergyStored() >> 16) & 0xFFFF;
                case 2 -> energyStorage.getMaxEnergyStored() & 0xFFFF;
                case 3 -> (energyStorage.getMaxEnergyStored() >> 16) & 0xFFFF;
                case 4 -> statusCode;
                case 5 -> currentPowerUse & 0xFFFF;
                case 6 -> (currentPowerUse >>> 16) & 0xFFFF;
                case 7 -> averagePowerUse & 0xFFFF;
                case 8 -> (averagePowerUse >>> 16) & 0xFFFF;
                case 9 -> syncedSurchargePercent;
                case 10 -> syncedAnomalyOrdinal;
                case 11 -> syncedPassiveDrain;
                case 12 -> armMask;
                case 13 -> syncedPullScaled;
                case 14 -> residueSecondsLeft();
                case 15 -> starvingTicks;
                case 16 -> Math.min(Short.MAX_VALUE, storedResidue());
                case 17 -> isLinked() ? 1 : 0;
                case 18 -> syncedLoadPercent;
                case 19 -> cell().ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 4) statusCode = value;
        }

        @Override
        public int getCount() {
            return 20;
        }
    };

    public ContainmentHallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ANOMALY_CONTAINMENT_HALL_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ContainmentHallBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (level.getGameTime() % 20L == 0L) {
            be.revalidateStructure();
            be.recountLinked(serverLevel);
            if (be.isFormed()) {
                ContainmentHallStructure.clearCell(level, pos);
                if (be.relightPending) {
                    be.relightPending = false;
                    ContainmentHallStructure.relight(serverLevel, pos);
                }
            }
        }

        FluxBand band = QuantumFlux.chunkAnomalyBand(serverLevel, pos);
        be.syncedAnomalyOrdinal = band.ordinal();
        be.syncedSurchargePercent = AnomalyEffects.surchargePercent(serverLevel, pos);

        int spent = 0;
        int status = STATUS_UNFORMED;
        boolean active = false;
        double pulled = 0.0;

        if (be.isFormed()) {
            status = STATUS_NO_POWER;
            int cost = be.fePerTick();
            if (be.energyStorage.getEnergyStored() >= cost) {
                spent = be.energyStorage.consume(cost);
                if (spent > 0) {
                    if (serverLevel.getGameTime() % 20L == 0L) {
                        QuantumFlux.contain(serverLevel, pos, be.capacity(), be.fieldRadius());
                        be.lastPulled = QuantumFlux.suppressAnomaly(serverLevel, pos, be.pullPerSecond(), be.fieldRadius());
                    }
                    pulled = be.lastPulled;
                    active = true;
                    status = QuantumFlux.chunkContained(serverLevel, pos) ? STATUS_CONTAINING : STATUS_OVERWHELMED;
                    be.setChanged();
                }
            }
        }

        int leak = AnomalyEffects.passiveDrainFePerTick(serverLevel, pos);
        if (leak > 0) {
            leak = be.energyStorage.consume(leak);
            if (leak > 0) be.setChanged();
        }
        be.syncedPassiveDrain = leak;
        be.syncedLoadPercent = (int) Math.min(9_999L, Math.round(QuantumFlux.chunkLoad(serverLevel, pos) * 100.0));
        // Container data rides across as a signed short, so clamp before it wraps.
        be.syncedPullScaled = (int) Math.min(Short.MAX_VALUE, Math.round(pulled));
        be.statusCode = status;
        be.recordPowerUse(spent);

        if (be.occupied) {
            // The slack field is the tell: ten seconds of it and the occupant walks out.
            be.unpoweredTicks = active ? 0 : be.unpoweredTicks + 1;
            if (be.unpoweredTicks >= RELEASE_AFTER_TICKS) {
                be.releaseOccupant(serverLevel);
            } else if (active) {
                be.burnResidue(serverLevel);
            }
        }

        if (state.getValue(AnomalyContainmentHallBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(AnomalyContainmentHallBlock.ACTIVE, active),
                    Block.UPDATE_CLIENTS);
        }
    }

    /** Burns through residue while holding, and lets the occupant go once starved past the grace. */
    private void burnResidue(ServerLevel serverLevel) {
        if (burnTicks <= 0 && takeResidue(serverLevel)) burnTicks = TICKS_PER_RESIDUE;
        if (burnTicks > 0) {
            burnTicks--;
            if (starvingTicks > 0) {
                starvingTicks = 0;
                setChangedAndSync();
            }
            return;
        }
        if (starvingTicks++ == 0) setChangedAndSync();
        if (starvingTicks % STARVE_WARNING_INTERVAL == 1) {
            // Higher as the grace runs out. Volume past 1 only widens the range, so it is doubled an
            // octave down to carry: an alarm, not a hum you could miss over machinery.
            float urgency = (float) starvingTicks / STARVE_GRACE_TICKS;
            float pitch = 0.8f + 0.5f * urgency;
            serverLevel.playSound(null, worldPosition.above(2), ModSounds.HALL_STARVING.get(), SoundSource.BLOCKS,
                    2.0f, pitch);
            serverLevel.playSound(null, worldPosition.above(2), ModSounds.HALL_STARVING.get(), SoundSource.BLOCKS,
                    2.0f, pitch * 0.5f);
        }
        if (starvingTicks >= STARVE_GRACE_TICKS) releaseOccupant(serverLevel);
    }

    public boolean isLinked() {
        return EntangledLinks.isLink(residue.getStackInSlot(0));
    }

    /** One residue from the slot, or through the link from wherever it points. */
    private boolean takeResidue(ServerLevel serverLevel) {
        if (!isLinked()) return !residue.extractItem(0, 1, false).isEmpty();
        IItemHandler source = EntangledLinks.resolve(serverLevel, worldPosition, residue.getStackInSlot(0));
        if (source == null) return false;
        for (int slot = 0; slot < source.getSlots(); slot++) {
            if (source.getStackInSlot(slot).is(ModItems.RIFT_RESIDUE.get())
                    && !source.extractItem(slot, 1, false).isEmpty()) {
                linkedResidue = Math.max(0, linkedResidue - 1);
                return true;
            }
        }
        return false;
    }

    private void recountLinked(ServerLevel serverLevel) {
        linkedResidue = 0;
        if (!isLinked()) return;
        IItemHandler source = EntangledLinks.resolve(serverLevel, worldPosition, residue.getStackInSlot(0));
        if (source == null) return;
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (stack.is(ModItems.RIFT_RESIDUE.get())) linkedResidue += stack.getCount();
        }
    }

    /** Residue on hand: in the slot, or in the linked inventory. */
    public int storedResidue() {
        return isLinked() ? linkedResidue : residue.getStackInSlot(0).getCount();
    }

    /**
     * Seconds of holding left on residue burnt and stored. Capped to fit the container-data short;
     * a linked chest can hold days of it.
     */
    public int residueSecondsLeft() {
        long seconds = (burnTicks + (long) storedResidue() * TICKS_PER_RESIDUE) / 20;
        return (int) Math.min(Short.MAX_VALUE, seconds);
    }

    public boolean hasResidue() {
        return burnTicks > 0 || storedResidue() > 0;
    }

    public boolean isStarving() {
        return starvingTicks > 0;
    }

    public ItemStackHandler getResidue() {
        return residue;
    }

    /** Fed through the arms. Null unless formed. */
    @Nullable
    public ResourceHandler<ItemResource> residuePort() {
        return isFormed() ? residuePort : null;
    }

    public int armCount() {
        return Integer.bitCount(armMask);
    }

    public int getArmMask() {
        return armMask;
    }

    public int fePerTick() {
        return fePerTick(armCount());
    }

    public static int fePerTick(int arms) {
        return arms <= 0 ? 0 : FE_PER_TICK_BASE + FE_PER_ARM * arms;
    }

    /** Anomaly pulled a second from the whole field. */
    public double pullPerSecond() {
        double pull = ANOMALY_PULL_PER_ARM * armCount();
        return occupied ? pull * OCCUPIED_PULL_MULTIPLIER : pull;
    }

    /** Anomaly removed per second from the hall's own chunk, for readouts. */
    public double suppressionPerSecond() {
        return pullPerSecond() * QuantumFlux.share(0, 0, fieldRadius());
    }

    /** Chunks out its field reaches: the 3×3 with one or two arms, the 5×5 with three or four. */
    public int fieldRadius() {
        return fieldRadius(armCount());
    }

    public static int fieldRadius(int arms) {
        return arms >= 3 ? 2 : 1;
    }

    /** The containment capacity it covers its field with: all of its rated band. */
    public double capacity() {
        return QuantumFlux.capacityFor(shieldCap());
    }

    public FluxBand shieldCap() {
        return shieldCap(armCount());
    }

    /**
     * Its rating: the band it holds a field through. One arm matches the basic shield; two hold
     * Critical, three hold it over a wider field, four hold a Singularity.
     */
    public static FluxBand shieldCap(int arms) {
        return switch (arms) {
            case 0 -> FluxBand.LOW;
            case 1 -> FluxBand.HIGH;
            case 2, 3 -> FluxBand.CRITICAL;
            default -> FluxBand.SINGULARITY;
        };
    }

    @Override
    public BlockPos controllerPos() {
        return worldPosition;
    }

    @Override
    public boolean coversPart(BlockPos part) {
        return ContainmentHallStructure.coversPart(worldPosition, part);
    }

    @Override
    public void revalidateStructure() {
        if (level == null || level.isClientSide()) return;
        PartClaim claim = PartClaim.of(level, worldPosition);
        int next = ContainmentHallStructure.armMask(level, worldPosition, claim);
        ContainmentHallStructure.applyPartStates(level, worldPosition, next, claim);

        BlockState state = getBlockState();
        boolean formed = next != 0;
        if (state.hasProperty(QuantumFoundryStructure.FORMED)
                && state.getValue(QuantumFoundryStructure.FORMED) != formed) {
            level.setBlock(worldPosition, state.setValue(QuantumFoundryStructure.FORMED, formed),
                    Block.UPDATE_CLIENTS);
        }

        if (next == armMask) return;
        ContainmentHallStructure.invalidateArmCapabilities(level, worldPosition, armMask | next);
        armMask = next;
        invalidateCapabilities();
        setChangedAndSync();
    }

    @Override
    public boolean isFormed() {
        return armMask != 0;
    }

    /** The Harvest Laser drawing thread from its occupant, and when it last did. Not saved. */
    @Nullable
    private BlockPos threadedBy;
    private long threadedAt;

    /**
     * Whether the laser at {@code laser} may draw thread from the occupant: one laser at a time, and
     * a laser that stops for a second gives up its turn.
     */
    public boolean claimThread(BlockPos laser, long now) {
        if (threadedBy != null && !threadedBy.equals(laser) && now - threadedAt <= 20L) return false;
        threadedBy = laser;
        threadedAt = now;
        return true;
    }

    public boolean isOccupied() {
        return occupied;
    }

    /** Free, formed, powered and fed. One arm is enough; more arms only make it easier. */
    public boolean canHold() {
        return cell() == Cell.WAITING;
    }

    /**
     * The state of its cell, for a Veiled: waiting (it can take hold of one), taken, dark (no field:
     * unformed or unpowered), armless, or hungry (no Rift Residue). Shown on its screen, and told to
     * a lancer whose Veiled got away because of it.
     */
    public enum Cell { WAITING, TAKEN, DARK, ARMLESS, HUNGRY }

    public Cell cell() {
        if (occupied) return Cell.TAKEN;
        if (statusCode != STATUS_CONTAINING && statusCode != STATUS_OVERWHELMED) return Cell.DARK;
        if (armCount() <= 0) return Cell.ARMLESS;
        if (!hasResidue()) return Cell.HUNGRY;
        return Cell.WAITING;
    }

    /** How far the field can reach for a weakened Veiled: 17 blocks on one arm, 32 on four. */
    public double captureRange() {
        return 12.0 + 5.0 * armCount();
    }

    /** Ticks to draw one in once it has hold: 7 s on one arm down to about 3 s on four. */
    public int captureTicks() {
        return 140 - 25 * (Math.max(1, armCount()) - 1);
    }

    public void contain() {
        occupied = true;
        unpoweredTicks = 0;
        starvingTicks = 0;
        setChangedAndSync();
    }

    private void releaseOccupant(ServerLevel serverLevel) {
        occupied = false;
        unpoweredTicks = 0;
        starvingTicks = 0;
        setChangedAndSync();
        VeiledManager.releaseFromHall(serverLevel, worldPosition);
    }

    /**
     * The controller is being broken: the occupant walks free. Called from the block's onRemove, which
     * only fires for a real block change. Not from setRemoved, which also runs as chunks unload during
     * a save, where touching the world (reading a block state, spawning) can stall shutdown.
     */
    public void releaseBroken(ServerLevel serverLevel) {
        Containers.dropItemStack(serverLevel, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
                worldPosition.getZ() + 0.5, residue.getStackInSlot(0).copy());
        residue.setStackInSlot(0, ItemStack.EMPTY);
        ContainmentHallStructure.invalidateArmCapabilities(serverLevel, worldPosition, armMask);
        if (!occupied) return;
        occupied = false;
        VeiledManager.releaseFromHall(serverLevel, worldPosition);
    }

    @Override
    public QuantumEnergyStorage energyPort() {
        return isFormed() ? energyStorage : null;
    }

    public QuantumEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public ContainerData getData() {
        return data;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public int getAveragePowerUse() {
        return averagePowerUse;
    }

    public boolean isUsableBy(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5) <= 64.0;
    }

    private void recordPowerUse(int amount) {
        currentPowerUse = Math.max(0, amount);
        powerSamples[powerSampleIndex++ % powerSamples.length] = currentPowerUse;
        long total = 0;
        for (int sample : powerSamples) total += sample;
        averagePowerUse = (int) (total / powerSamples.length);
    }

    private void setChangedAndSync() {
        setChanged();
        if (level instanceof ServerLevel server) {
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
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
        tag.putInt("ArmMask", armMask);
        if (occupied) tag.putBoolean("Occupied", true);
        tag.put("Residue", residue.serializeNBT(registries));
        if (burnTicks > 0) tag.putInt("BurnTicks", burnTicks);
        if (starvingTicks > 0) tag.putInt("StarvingTicks", starvingTicks);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        loadLegacy(NbtCompat.read(in), NbtCompat.lookup(in));
    }

    private void loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        energyStorage.setEnergy(tag.getIntOr("Energy", 0));
        armMask = tag.getIntOr("ArmMask", 0);
        occupied = tag.getBooleanOr("Occupied", false);
        if (tag.contains("Residue")) residue.deserializeNBT(registries, tag.getCompoundOrEmpty("Residue"));
        burnTicks = tag.getIntOr("BurnTicks", 0);
        starvingTicks = tag.getIntOr("StarvingTicks", 0);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveLegacy(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        handleUpdateLegacy(NbtCompat.read(input), NbtCompat.lookup(input));
    }

    private void handleUpdateLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        loadLegacy(tag, registries);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        CompoundTag tag = NbtCompat.read(input);
        if (!tag.isEmpty()) handleUpdateLegacy(tag, NbtCompat.lookup(input));
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.anomaly_containment_hall");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ContainmentHallMenu(containerId, playerInventory, this, data);
    }

    @Override
    public MutableComponent fluxMeterLine() {
        return Component.translatable("item.quantimium.flux_meter.containment_hall",
                        FluxMeterReadout.fe(getAveragePowerUse()), FluxMeterReadout.flux(suppressionPerSecond()),
                        Component.translatable("flux.quantimium.band." + shieldCap().getSerializedName()))
                .withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        Level level = this.level;
        if (level == null) return;
        if (level instanceof ServerLevel serverLevel) {
            releaseBroken(serverLevel);
        }
        ContainmentHallStructure.applyPartStates(level, pos, 0, PartClaim.of(level, pos));
    }
}

package com.kadikular.quantimium.block.entity;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import com.kadikular.quantimium.util.RestrictedItems;
import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.Containers;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModTags;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.menu.RiftAnchorMenu;
import com.kadikular.quantimium.phase.FluxRift;
import com.kadikular.quantimium.phase.FluxRiftManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.items.IItemHandler;
import com.kadikular.quantimium.util.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RangedWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;

/**
 * Rift Anchor: the footing of a stabilised rift. A Rift Seed used on it opens a stage 1 wound
 * standing on it, and one slipped under a natural rift takes that one instead.
 *
 * <p>The anchor itself is unpowered. It claims the Rift Stabilisers around it, and while at least
 * {@value #MIN_STABILISERS} of them can see the rift and pay their power, the rift is held: it grows
 * on their power rather than the field's, nothing comes through it, and it only bites at close
 * quarters. Held, it sheds Rift Residue into the anchor at a rate set by stage × stabilisers. Let the
 * array fail and it is an ordinary rift again, at whatever stage it had reached.
 */
public class RiftAnchorBlockEntity extends BlockEntity implements MenuProvider, FluxMeterReadout {

    public static final int MIN_STABILISERS = 3;
    public static final int MAX_STABILISERS = 6;
    /** Stabilisers stand this many blocks out, horizontally, in any direction. */
    public static final int MIN_REACH = 2;
    public static final int MAX_REACH = 6;
    /** ... and within this vertical window of the anchor. */
    public static final int REACH_BELOW = 2;
    public static final int REACH_ABOVE = 4;
    public static final int OUTPUT_CAPACITY = 64;
    /**
     * Work per residue, in stage × stabiliser ticks. A stage 4 rift on four stabilisers makes one
     * every four minutes, a little ahead of one hall's five; on six, one every 2.7.
     */
    public static final long WORK_PER_RESIDUE = 16L * 4_800L;
    private static final int RESCAN_TICKS = 20;

    public static final int STATUS_NO_RIFT = 0;
    /** A rift, but fewer than {@value #MIN_STABILISERS} stabilisers paying for it. */
    public static final int STATUS_LOOSE = 1;
    public static final int STATUS_HELD = 2;
    /** Held, but the residue slot is full, so the work waits. */
    public static final int STATUS_FULL = 3;

    private final ItemStackHandler output = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(ModItems.RIFT_RESIDUE.get());
        }

        @Override
        public int getSlotLimit(int slot) {
            return OUTPUT_CAPACITY;
        }
    };

    /** Take-only: the anchor makes residue, it does not store yours. */
    private final ResourceHandler<ItemResource> automation = RestrictedItems.takeOnly(output);

    /** Claimed stabilisers, nearest first. Re-read every second, and on the first tick after a load. */
    private final List<BlockPos> stabilisers = new ArrayList<>();
    /** Of those, the ones with a clear line to the rift. Refreshed with the claim. */
    private final List<BlockPos> sighted = new ArrayList<>();
    private long work;
    /** From the last rescan, for the status line: in reach but unable to see the rift, and seeing but over six. */
    private int blind;
    private int spare;
    /** Stabilisers that paid this tick, for the status readout. */
    private int holding;
    private boolean scanned;
    /** The rift's stage as of the last tick, 0 with no rift. */
    private int stage;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> status();
                case 1 -> stage;
                case 2 -> holding;
                case 3 -> stabilisers.size();
                case 4 -> sighted.size();
                case 5 -> blind;
                case 6 -> spare;
                case 7 -> (int) (Math.min(work, WORK_PER_RESIDUE) * 1000 / WORK_PER_RESIDUE);
                case 8 -> secondsToNext();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return RiftAnchorMenu.DATA_COUNT;
        }
    };

    public RiftAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RIFT_ANCHOR_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RiftAnchorBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        long now = serverLevel.getGameTime();
        FluxRift rift = FluxRiftManager.riftAbove(serverLevel, pos);
        if (!be.scanned || now % RESCAN_TICKS == 0) be.rescan(serverLevel, rift, now);
        be.hold(serverLevel, rift, now);
    }

    /**
     * Stabilisers pay only when enough of them can see the rift to hold it; two on their own would
     * burn power for nothing. Each paying one beams; the rift is held once three or more do.
     */
    private void hold(ServerLevel level, @Nullable FluxRift rift, long now) {
        int paid = 0;
        boolean enough = rift != null && sighted.size() >= MIN_STABILISERS;
        int cost = rift == null ? 0 : RiftStabiliserBlockEntity.fePerTick(rift.stage());
        for (BlockPos pos : stabilisers) {
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof RiftStabiliserBlockEntity stabiliser)) {
                continue;
            }
            boolean beaming = enough && sighted.contains(pos) && stabiliser.draw(cost);
            if (beaming) paid++;
            stabiliser.show(beaming ? rift.anchor() : null, rift == null ? 0 : rift.stage());
        }
        holding = paid;
        stage = rift == null ? 0 : rift.stage();
        if (rift == null || paid < MIN_STABILISERS) return;
        rift.stabilise(paid, now);
        work += (long) rift.stage() * paid;
        if (work < WORK_PER_RESIDUE) return;
        if (output.insertItem(0, new ItemStack(ModItems.RIFT_RESIDUE.get()), false).isEmpty()) {
            work -= WORK_PER_RESIDUE;
        } else {
            // Full: it waits with a residue's worth ready, rather than banking hours of it.
            work = WORK_PER_RESIDUE;
        }
        setChanged();
    }

    /**
     * Claims the nearest free stabilisers in reach that can see the rift, up to six. One that cannot
     * see it is not claimed at all: counted first, a walled-off stabiliser took a slot, drew no beam,
     * and kept a seventh that could see from joining. Reads the block entity maps of the few chunks
     * the reach overlaps rather than probing every position in it.
     */
    private void rescan(ServerLevel level, @Nullable FluxRift rift, long now) {
        scanned = true;
        List<RiftStabiliserBlockEntity> found = new ArrayList<>();
        int minChunkX = (worldPosition.getX() - MAX_REACH) >> 4;
        int maxChunkX = (worldPosition.getX() + MAX_REACH) >> 4;
        int minChunkZ = (worldPosition.getZ() - MAX_REACH) >> 4;
        int maxChunkZ = (worldPosition.getZ() + MAX_REACH) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (be instanceof RiftStabiliserBlockEntity stabiliser && inReach(stabiliser.getBlockPos())
                            && stabiliser.claimableBy(worldPosition)) {
                        found.add(stabiliser);
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(s -> s.getBlockPos().distSqr(worldPosition)));
        int inReach = found.size();
        if (rift != null) found.removeIf(s -> !clear(level, s.emitter(), rift.centre()));
        blind = inReach - found.size();
        spare = Math.max(0, found.size() - MAX_STABILISERS);
        if (found.size() > MAX_STABILISERS) found.subList(MAX_STABILISERS, found.size()).clear();

        for (BlockPos pos : stabilisers) {
            if (found.stream().noneMatch(s -> s.getBlockPos().equals(pos))
                    && level.getBlockEntity(pos) instanceof RiftStabiliserBlockEntity dropped) {
                dropped.release(worldPosition);
            }
        }
        stabilisers.clear();
        sighted.clear();
        for (RiftStabiliserBlockEntity stabiliser : found) {
            stabiliser.bind(worldPosition, now);
            stabilisers.add(stabiliser.getBlockPos());
            if (rift != null) sighted.add(stabiliser.getBlockPos());
        }
    }

    private boolean inReach(BlockPos pos) {
        int dx = Math.abs(pos.getX() - worldPosition.getX());
        int dz = Math.abs(pos.getZ() - worldPosition.getZ());
        int dy = pos.getY() - worldPosition.getY();
        int reach = Math.max(dx, dz);
        return reach >= MIN_REACH && reach <= MAX_REACH && dy >= -REACH_BELOW && dy <= REACH_ABOVE;
    }

    /**
     * A clear line from an emitter to the rift. Other stabilisers and anchors do not block it: an
     * array is built out of them, and the beams are drawn straight through. Nor do Anomalite crystals:
     * they grow on the array's powered blocks, and are mirror growth showing through rather than
     * real matter, so they drain a stabiliser but never shade it.
     */
    private static boolean clear(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 start = from;
        for (int hops = 0; hops < 16; hops++) {
            BlockHitResult hit = level.clip(new ClipContext(start, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.MISS) return true;
            BlockState state = level.getBlockState(hit.getBlockPos());
            if (!state.is(ModBlocks.RIFT_STABILISER.get()) && !state.is(ModBlocks.RIFT_ANCHOR.get())
                    && !state.is(ModTags.ANOMALITE)) return false;
            // Step just past the part it hit and look again.
            start = hit.getLocation().add(to.subtract(hit.getLocation()).normalize().scale(0.05));
        }
        return false;
    }

    /**
     * Claims and pays at once, rather than on the next tick. Called as a seed opens a rift here, so a
     * ready array holds it before the rift's first second can open a tear or spit a mite.
     */
    public void holdNow(ServerLevel level) {
        FluxRift rift = FluxRiftManager.riftAbove(level, worldPosition);
        rescan(level, rift, level.getGameTime());
        hold(level, rift, level.getGameTime());
    }

    /** The stabilisers pulled back out of range or broken: let them go so another anchor can have them. */
    public void releaseAll() {
        if (level == null) return;
        for (BlockPos pos : stabilisers) {
            if (level.getBlockEntity(pos) instanceof RiftStabiliserBlockEntity stabiliser) stabiliser.release(worldPosition);
        }
        stabilisers.clear();
        sighted.clear();
    }

    /** Work banked towards the next residue, in stage × stabiliser ticks. */
    public long work() {
        return work;
    }

    public int blindCount() {
        return blind;
    }

    public int spareCount() {
        return spare;
    }

    public int claimedCount() {
        return stabilisers.size();
    }

    public int sightedCount() {
        return sighted.size();
    }

    public int holdingCount() {
        return holding;
    }

    public int status() {
        if (stage == 0) return STATUS_NO_RIFT;
        if (holding < MIN_STABILISERS) return STATUS_LOOSE;
        return output.getStackInSlot(0).getCount() >= OUTPUT_CAPACITY ? STATUS_FULL : STATUS_HELD;
    }

    /** Stage × paying stabilisers: the work banked each tick while held, and so the production rate. */
    public int workPerTick() {
        return holding >= MIN_STABILISERS ? stage * holding : 0;
    }

    /** Residue made per hour at {@code workPerTick}. */
    public static double residuePerHour(int workPerTick) {
        return workPerTick * 72_000.0 / WORK_PER_RESIDUE;
    }

    /** Until the next residue at the current rate; -1 while not producing, capped to fit the menu sync. */
    public int secondsToNext() {
        int rate = workPerTick();
        if (rate == 0 || status() == STATUS_FULL) return -1;
        long ticks = (Math.max(0, WORK_PER_RESIDUE - work) + rate - 1) / rate;
        return (int) Math.min(Short.MAX_VALUE, (ticks + 19) / 20);
    }

    public ContainerData getData() {
        return data;
    }

    public ItemStackHandler getOutput() {
        return output;
    }

    public ResourceHandler<ItemResource> getAutomationItemHandler() {
        return automation;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        CompoundTag tag = new CompoundTag();
        saveLegacy(tag, NbtCompat.registries(level));
        NbtCompat.write(out, tag);
    }

    private void saveLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Output", output.serializeNBT(registries));
        if (work > 0) tag.putLong("Work", work);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        loadLegacy(NbtCompat.read(in), NbtCompat.lookup(in));
    }

    private void loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Output")) output.deserializeNBT(registries, tag.getCompoundOrEmpty("Output"));
        work = tag.getLongOr("Work", 0L);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.rift_anchor");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new RiftAnchorMenu(containerId, inventory, this, data);
    }

    /** The rift standing on it: its stage, who holds it, and what it makes. */
    @Override
    public MutableComponent fluxMeterLine() {
        MutableComponent line = switch (status()) {
            case STATUS_NO_RIFT -> Component.translatable("item.quantimium.flux_meter.rift_anchor.empty");
            case STATUS_LOOSE -> Component.translatable("item.quantimium.flux_meter.rift_anchor.loose",
                    stage, holding, MIN_STABILISERS);
            default -> Component.translatable("item.quantimium.flux_meter.rift_anchor.held", stage, holding,
                    MAX_STABILISERS, String.format("%.1f", residuePerHour(workPerTick())));
        };
        return line.withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        Level level = this.level;
        if (level == null) return;
        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                getOutput().getStackInSlot(0).copy());
        releaseAll();
    }
}

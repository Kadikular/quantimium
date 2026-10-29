package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.laser.LaserBeam;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.LaserNode;
import com.kadikular.quantimium.laser.LaserTuning;
import com.kadikular.quantimium.laser.Logistics;
import com.kadikular.quantimium.laser.LogisticsController;
import com.kadikular.quantimium.laser.LogisticsMember;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Logistics Terminal: on a logistics network (a controller's light reaches it), it shows everything the network's
 * storage holds and its providers offer, and takes from it or puts into it for the player, each move paid for by
 * the controller as its receptacles' are. Light ends at it.
 */
public class LogisticsTerminalBlockEntity extends BlockEntity implements LaserNode, MenuProvider {

    public LogisticsTerminalBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.LOGISTICS_TERMINAL_BE.get(), pos, state);
    }

    /** For another block that is a terminal too: a pod's Logistics module. */
    protected LogisticsTerminalBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ---- its crafting grid: kept here, so it's there next time, and one grid for everyone at it ----

    /** What's really in the crafting grid. */
    private final net.minecraft.core.NonNullList<ItemStack> craftItems = net.minecraft.core.NonNullList.withSize(9, ItemStack.EMPTY);
    /**
     * What each grid slot is to have made for it when a craft is taken (one of, none: EMPTY): a "craft" in the slot,
     * shown while the slot is empty and the network can make it or has it.
     */
    private final ItemStack[] craftGhosts = new ItemStack[] {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    /** The menus open on it, told when the grid changes. */
    private final Set<com.kadikular.quantimium.menu.LogisticsTerminalMenu> viewers = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    public net.minecraft.core.NonNullList<ItemStack> craftItems() {
        return craftItems;
    }

    public ItemStack[] craftGhosts() {
        return craftGhosts;
    }

    public void watch(com.kadikular.quantimium.menu.LogisticsTerminalMenu menu, boolean watching) {
        if (watching) viewers.add(menu);
        else viewers.remove(menu);
    }

    /** The grid changed (by {@code by}, or by none): kept, and every other menu on it works its result out again. */
    public void craftGridChanged(@Nullable com.kadikular.quantimium.menu.LogisticsTerminalMenu by) {
        setChanged();
        for (var menu : List.copyOf(viewers)) if (menu != by) menu.gridChangedElsewhere();
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("Channel", channel);
        out.store("CraftGrid", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(craftItems));
        out.store("CraftGhosts", ItemStack.OPTIONAL_CODEC.listOf(), List.of(craftGhosts));
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput in) {
        super.loadAdditional(in);
        channel = com.kadikular.quantimium.laser.Channels.of(in.getIntOr("Channel", 0));
        List<ItemStack> grid = in.read("CraftGrid", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        List<ItemStack> ghosts = in.read("CraftGhosts", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < 9; i++) {
            craftItems.set(i, i < grid.size() ? grid.get(i) : ItemStack.EMPTY);
            craftGhosts[i] = i < ghosts.size() ? ghosts.get(i) : ItemStack.EMPTY;
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) net.minecraft.world.Containers.dropContents(level, pos, craftItems);
    }

    /** Which network it shows: the controller on this channel whose light reaches it. 0 unless set. */
    private int channel;

    public int channel() {
        return channel;
    }

    public void setChannel(int channel) {
        this.channel = com.kadikular.quantimium.laser.Channels.of(channel);
        setChanged();
    }

    /** The controller on its channel whose light reaches it, or null. Server side. */
    @Nullable
    public LogisticsController controller() {
        return level == null ? null : LaserNetworks.logisticsAt(level, worldPosition, channel);
    }

    private List<LogisticsMember> members() {
        LogisticsController controller = controller();
        return controller == null ? List.of() : controller.members();
    }

    /** Everything it can reach: what's really there and what could be made, by item, from the network's offers. */
    public Map<ItemResource, long[]> contents() {
        Map<ItemResource, long[]> counts = new LinkedHashMap<>();
        LogisticsController controller = controller();
        if (controller == null) return counts;
        // Someone's looking: what the network offers is worked out first.
        controller.watched();
        for (var entry : controller.offers().entries()) counts.put(entry.item(), new long[] {entry.real(), entry.craftable()});
        return counts;
    }

    /**
     * Takes up to {@code count} of {@code item} from the network, if the controller pays; what it got. What's stored
     * comes out first, and if any is, that's all: only when none is does a machine make some.
     */
    public ItemStack take(ItemResource item, int count) {
        LogisticsController controller = controller();
        if (controller == null || count <= 0) return ItemStack.EMPTY;
        // Only the slots last seen holding it are looked at, not every slot on the network.
        var spots = controller.spots();
        boolean stored = !Logistics.take(spots, item, count, true, true, false).isEmpty();
        if (!stored && Logistics.take(spots, item, count, true, false, true).isEmpty()) {
            // Listed but not found where things were last seen: looked over again, once, in case it moved since.
            var offers = controller.offers();
            var again = offers.real(item) + offers.craftable(item) > 0 ? controller.spotsAgain() : null;
            if (again != null) {
                spots = again;
                stored = !Logistics.take(spots, item, count, true, true, false).isEmpty();
            }
        }
        if (!stored && Logistics.take(spots, item, count, true, false, true).isEmpty()) {
            makeReady(controller, item);
            // Listed and not to be had: what the network lists is behind; it looks again at once.
            controller.stockMoved();
            return ItemStack.EMPTY;
        }
        if (!controller.pay(LaserTuning.LOGISTICS_FE_PER_MOVE)) return ItemStack.EMPTY;
        controller.stockMoved();
        ItemStack got = ItemStack.EMPTY;
        for (int round = 0; round < 64 && got.getCount() < count; round++) {
            int want = count - got.getCount();
            // What's stored first, every time: a craft's leftovers (put somewhere new, so looked over again) come out
            // before anything more is made.
            ItemStack more = Logistics.take(spots, item, want, false, true, false);
            if (more.isEmpty()) {
                var again = controller.spotsAgain();
                if (again != null) {
                    spots = again;
                    more = Logistics.take(spots, item, want, false, true, false);
                }
            }
            if (more.isEmpty() && !stored) more = Logistics.take(spots, item, want, false, false, true);
            if (more.isEmpty()) break;
            if (got.isEmpty()) got = more;
            else got.grow(more.getCount());
        }
        return got;
    }

    /**
     * The network says {@code item} could be made, but no machine would make it now: a Crafter's own list of what it
     * makes lags behind what the network has worked out (it's worked out in the background). Those the network names
     * work theirs out again first, so asking again shortly gets it.
     */
    private void makeReady(LogisticsController controller, ItemResource item) {
        if (level == null) return;
        var offers = controller.offers();
        if (offers.craftable(item) <= 0) return;
        for (BlockPos pos : offers.makers(item)) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof QuantumCrafterBlockEntity crafter) crafter.refreshSoon();
        }
    }

    /**
     * Makes up to {@code count} of {@code item} (mB for a fluid), at most what the network says it can make, and keeps
     * it in storage: a stack (a bucket's worth) at a time, each made only if storage has room for it. How many it made.
     */
    public long make(ItemResource item, long count) {
        LogisticsController controller = controller();
        if (controller == null || count <= 0) return 0;
        count = Math.min(count, controller.offers().craftable(item));
        List<LogisticsMember> members = controller.members();
        net.neoforged.neoforge.fluids.FluidStack fluid = com.kadikular.quantimium.recipe.FluidEntry.fluidOf(item.toStack(1));
        long made = 0;
        if (!fluid.isEmpty()) {
            while (made < count) {
                int chunk = (int) Math.min(count - made, net.neoforged.neoforge.fluids.FluidType.BUCKET_VOLUME);
                var wanted = fluid.copyWithAmount(chunk);
                if (Logistics.insertFluid(members, wanted, true, false) < chunk || !controller.pay(LaserTuning.LOGISTICS_FE_PER_MOVE)) break;
                var got = Logistics.makeFluid(members, wanted, chunk, false);
                if (got.isEmpty()) break;
                int stored = Logistics.insertFluid(members, got, false, false);
                made += got.getAmount();
                // Storage took less than it said: what's over goes back where it can.
                if (stored < got.getAmount()) Logistics.insertFluid(members, got.copyWithAmount(got.getAmount() - stored), false, true);
            }
        } else {
            int stack = item.toStack(1).getMaxStackSize();
            var spots = controller.spots();
            while (made < count) {
                int chunk = (int) Math.min(count - made, stack);
                if (!Logistics.insert(members, item.toStack(chunk), true, false).isEmpty() || !controller.pay(LaserTuning.LOGISTICS_FE_PER_MOVE)) break;
                ItemStack got = Logistics.take(spots, item, chunk, false, false, true);
                if (got.isEmpty()) {
                    var again = controller.spotsAgain();
                    if (again != null) got = Logistics.take(spots = again, item, chunk, false, false, true);
                }
                if (got.isEmpty()) break;
                made += got.getCount();
                ItemStack over = Logistics.insert(members, got, false, false);
                if (!over.isEmpty()) over = Logistics.insert(members, over, false, true);
                if (!over.isEmpty() && level != null) net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, over);
            }
        }
        if (made > 0) controller.stockMoved();
        else makeReady(controller, item);
        return made;
    }

    /** Takes up to {@code count} of {@code item} from what the network stores, making nothing; what it got. */
    public ItemStack takeStored(ItemResource item, int count) {
        LogisticsController controller = controller();
        if (controller == null || count <= 0) return ItemStack.EMPTY;
        var spots = controller.spots();
        if (Logistics.take(spots, item, count, true, true, false).isEmpty() || !controller.pay(LaserTuning.LOGISTICS_FE_PER_MOVE)) return ItemStack.EMPTY;
        ItemStack got = ItemStack.EMPTY;
        for (int round = 0; round < 64 && got.getCount() < count; round++) {
            ItemStack more = Logistics.take(spots, item, count - got.getCount(), false, true, false);
            if (more.isEmpty()) break;
            if (got.isEmpty()) got = more;
            else got.grow(more.getCount());
        }
        if (!got.isEmpty()) controller.stockShifted();
        return got;
    }

    /** Puts {@code stack} away in the network's storage, if the controller pays; what's left. */
    public ItemStack insert(ItemStack stack) {
        LogisticsController controller = controller();
        if (controller == null || stack.isEmpty()) return stack;
        List<LogisticsMember> members = controller.members();
        if (Logistics.insert(members, stack, true, false).getCount() == stack.getCount() || !controller.pay(LaserTuning.LOGISTICS_FE_PER_MOVE)) {
            return stack;
        }
        controller.stockMoved();
        return Logistics.insert(members, stack, false, false);
    }

    /** Takes up to {@code max} mB of {@code fluid} from the network, if the controller pays; what it got. */
    public net.neoforged.neoforge.fluids.FluidStack takeFluid(net.neoforged.neoforge.fluids.FluidStack fluid, int max) {
        LogisticsController controller = controller();
        if (controller == null || max <= 0) return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        java.util.function.Predicate<net.neoforged.neoforge.fluids.FluidStack> same = each -> net.neoforged.neoforge.fluids.FluidStack.isSameFluidSameComponents(each, fluid);
        List<LogisticsMember> members = controller.members();
        if (Logistics.takeFluid(members, same, max, true).isEmpty() || !controller.pay(LaserTuning.LOGISTICS_FE_PER_MOVE)) {
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }
        controller.stockMoved();
        return Logistics.takeFluid(members, same, max, false);
    }

    /** Pours {@code fluid} into the network's storage, if the controller pays; how much it took. */
    public int insertFluid(net.neoforged.neoforge.fluids.FluidStack fluid) {
        LogisticsController controller = controller();
        if (controller == null || fluid.isEmpty()) return 0;
        List<LogisticsMember> members = controller.members();
        if (Logistics.insertFluid(members, fluid, true, false) <= 0 || !controller.pay(LaserTuning.LOGISTICS_FE_PER_MOVE)) return 0;
        controller.stockMoved();
        return Logistics.insertFluid(members, fluid, false, false);
    }

    // ---- the network: light ends here ----

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    /** It passes nothing on; a relay only so the light reaching it is known. */
    @Override
    public Set<Direction> outputs() {
        return Set.of();
    }

    @Override
    public int range() {
        return LaserTuning.RELAY_RANGE;
    }

    @Override
    public boolean relays() {
        return true;
    }

    @Override
    public double tax() {
        return 0;
    }

    @Override
    public void showBeams(List<LaserBeam> beams) {}

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) LaserNetworks.add(level, this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) LaserNetworks.remove(level, this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null) LaserNetworks.remove(level, this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.logistics_terminal");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new com.kadikular.quantimium.menu.LogisticsTerminalMenu(containerId, inventory, this, false);
    }
}

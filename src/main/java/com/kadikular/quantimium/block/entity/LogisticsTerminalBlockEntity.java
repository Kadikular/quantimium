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

    /** The controller whose light reaches it, or null. Server side. */
    @Nullable
    public LogisticsController controller() {
        return level == null ? null : LaserNetworks.logisticsAt(level, worldPosition);
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

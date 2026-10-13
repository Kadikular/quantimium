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
        super(ModBlockEntities.LOGISTICS_TERMINAL_BE.get(), pos, state);
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

    /** Everything it can reach: what storage holds and what providers let go, by item, with how many, and how many more could be made. */
    public Map<ItemResource, long[]> contents() {
        Map<ItemResource, long[]> counts = new LinkedHashMap<>();
        for (LogisticsMember member : members()) {
            if (member.role() == LogisticsMember.Role.REQUESTER) continue;
            Map<ItemResource, long[]> listing = member.listing();
            if (listing != null) {
                listing.forEach((item, both) -> counts.merge(item, both.clone(), (a, b) -> new long[] {a[0] + b[0], a[1] + b[1]}));
                continue;
            }
            IItemHandler inventory = member.inventory();
            if (inventory == null) continue;
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack.isEmpty() || member.role() == LogisticsMember.Role.PROVIDER && !member.lets(stack)) continue;
                counts.merge(ItemResource.of(stack), new long[] {stack.getCount(), 0}, (a, b) -> new long[] {a[0] + b[0], a[1] + b[1]});
            }
        }
        return counts;
    }

    /** Takes up to {@code count} of {@code item} from the network, if the controller pays; what it got. */
    public ItemStack take(ItemResource item, int count) {
        LogisticsController controller = controller();
        if (controller == null || count <= 0) return ItemStack.EMPTY;
        List<LogisticsMember> members = controller.members();
        if (Logistics.take(members, stack -> ItemResource.of(stack).equals(item), count, true).isEmpty()
                || !controller.pay(LaserTuning.LOGISTICS_FE_PER_MOVE)) {
            return ItemStack.EMPTY;
        }
        ItemStack got = ItemStack.EMPTY;
        while (got.getCount() < count) {
            ItemStack more = Logistics.take(members, stack -> ItemResource.of(stack).equals(item), count - got.getCount(), false);
            if (more.isEmpty()) break;
            if (got.isEmpty()) got = more;
            else got.grow(more.getCount());
        }
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
        return new com.kadikular.quantimium.menu.LogisticsTerminalMenu(containerId, inventory, this);
    }
}

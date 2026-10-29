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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Logistics Materialiser: where a controller's light ends, it offers the whole logistics network to any mod's
 * pipes and storage buses, as the Reactor's Materialiser Port offers the Reactor. One slot for each thing the network's
 * storage holds or its providers offer, with how many; and one empty slot, where what's put in goes into storage.
 * Each transfer costs the controller a move. Light ends at it.
 */
public class LogisticsMaterialiserBlockEntity extends BlockEntity implements LaserNode {

    /** One thing on offer, and how many. */
    private record Entry(ItemResource item, long amount) {}

    /** The list last made, and the offers (and mode) it was made from: a pipe reads it slot by slot. */
    private List<Entry> entries = List.of();
    @Nullable
    private List<com.kadikular.quantimium.laser.OfferTable> entriesFrom;
    private Mode entriesMode;

    private final NetworkHandler handler = new NetworkHandler();

    public LogisticsMaterialiserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOGISTICS_MATERIALISER_BE.get(), pos, state);
    }

    /** What pipes and storage buses see on any face. */
    /** What it offers: what's really there, what machines could make, or both (the default). */
    public enum Mode {
        BOTH, REAL, CRAFTABLE;

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private Mode mode = Mode.BOTH;

    public Mode mode() {
        return mode;
    }

    public void cycleMode() {
        mode = mode.next();
        setChanged();
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("Mode", mode.ordinal());
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput in) {
        super.loadAdditional(in);
        mode = Mode.values()[Math.floorMod(in.getIntOr("Mode", 0), Mode.values().length)];
    }

    public boolean offersReal() {
        return mode != Mode.CRAFTABLE;
    }

    public boolean offersCraftable() {
        return mode != Mode.REAL;
    }

    /** {@code network}'s offers, as this Materialiser's mode lets them out. */
    public com.kadikular.quantimium.laser.OfferTable offersFor(LogisticsController network) {
        return network.offers().only(offersReal(), offersCraftable());
    }

    public ResourceHandler<ItemResource> itemHandler() {
        return handler;
    }

    /** The network's fluids, to pipes and Tesseracts: what its storage and providers hold, and room in its storage. */
    public ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> fluidHandler() {
        return fluids;
    }

    private final NetworkFluids fluids = new NetworkFluids();

    /**
     * The network's fluids as a handler, a tank a fluid. As the item handler, a move happens at once inside the caller's
     * transaction (the members' tanks join it), and the controller is paid a move each when the outermost commits.
     */
    private final class NetworkFluids extends SnapshotJournal<java.util.Map<LogisticsController, Integer>> implements ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> {
        /** Moves made in open transactions, by the network they were made on, paid for when the outermost commits. */
        private java.util.Map<LogisticsController, Integer> moves = new java.util.HashMap<>();
        @Nullable
        private List<java.util.Map.Entry<net.neoforged.neoforge.transfer.fluid.FluidResource, Long>> listed;
        private long listedAt = Long.MIN_VALUE;

        /** The network's fluids, looked over at most once a tick. */
        private List<java.util.Map.Entry<net.neoforged.neoforge.transfer.fluid.FluidResource, Long>> listed() {
            long now = level == null ? 0 : level.getGameTime();
            if (listed == null || listedAt != now) {
                listed = offersReal() ? new ArrayList<>(Logistics.fluids(members()).entrySet()) : List.of();
                listedAt = now;
            }
            return listed;
        }

        @Override
        public int size() {
            return controllers().isEmpty() ? 0 : listed().size() + 1;
        }

        @Override
        public net.neoforged.neoforge.transfer.fluid.FluidResource getResource(int index) {
            var list = listed();
            return index >= 0 && index < list.size() ? list.get(index).getKey() : net.neoforged.neoforge.transfer.fluid.FluidResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            var list = listed();
            return index >= 0 && index < list.size() ? list.get(index).getValue() : 0;
        }

        @Override
        public long getCapacityAsLong(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isValid(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource) {
            return !controllers().isEmpty();
        }

        /** Into each network's storage in turn, those with the power for it. */
        @Override
        public int insert(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource, int amount, TransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) return 0;
            int put = 0;
            for (LogisticsController controller : controllers()) {
                if (put >= amount || !controller.powered()) continue;
                int more = Logistics.insertFluid(membersOf(controller), resource.toStack(amount - put), false, false);
                if (more > 0) moved(controller, transaction);
                put += more;
            }
            return put;
        }

        /** From each network in turn. */
        @Override
        public int extract(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource, int amount, TransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0 || !offersReal()) return 0;
            var wanted = resource.toStack(1);
            int got = 0;
            for (LogisticsController controller : controllers()) {
                if (got >= amount || !controller.powered()) continue;
                int more = Logistics.takeFluid(membersOf(controller), each -> net.neoforged.neoforge.fluids.FluidStack.isSameFluidSameComponents(each, wanted),
                        amount - got, false).getAmount();
                if (more > 0) moved(controller, transaction);
                got += more;
            }
            return got;
        }

        private void moved(LogisticsController controller, TransactionContext transaction) {
            updateSnapshots(transaction);
            moves.merge(controller, 1, Integer::sum);
            listed = null;
        }

        @Override
        protected java.util.Map<LogisticsController, Integer> createSnapshot() {
            return new java.util.HashMap<>(moves);
        }

        @Override
        protected void revertToSnapshot(java.util.Map<LogisticsController, Integer> snapshot) {
            moves = snapshot;
            listed = null;
        }

        @Override
        protected void onRootCommit(java.util.Map<LogisticsController, Integer> originalState) {
            moves.forEach((controller, made) -> {
                for (int i = 0; i < made; i++) controller.pay(LaserTuning.LOGISTICS_FE_PER_MOVE);
                if (made > 0) controller.stockShifted();
            });
            moves = new java.util.HashMap<>();
        }
    }

    /**
     * Asked with no face (other mods' screens, inventory viewers): the same, a stack at most to a slot, since
     * such screens turn each slot into an item stack, and one of thousands can't be sent to a client.
     */
    public ResourceHandler<ItemResource> viewerHandler() {
        return viewer;
    }

    private final ResourceHandler<ItemResource> viewer = new ResourceHandler<>() {
        @Override
        public int size() {
            return handler.size();
        }

        @Override
        public ItemResource getResource(int index) {
            return handler.getResource(index);
        }

        @Override
        public long getAmountAsLong(int index) {
            ItemResource item = handler.getResource(index);
            return item.isEmpty() ? 0 : Math.min(handler.getAmountAsLong(index), item.toStack(1).getMaxStackSize());
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return resource.isEmpty() ? 64 : resource.toStack(1).getMaxStackSize();
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return handler.isValid(index, resource);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            return handler.insert(index, resource, amount, transaction);
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            return handler.extract(index, resource, (int) Math.min(amount, getAmountAsLong(index)), transaction);
        }
    };

    /** Every Logistics Controller whose light reaches it, on any channel: it offers all their networks. */
    public List<LogisticsController> controllers() {
        return level == null ? List.of() : LaserNetworks.logisticsAllAt(level, worldPosition);
    }

    /** The first of {@link #controllers}, or null for none. */
    @Nullable
    public LogisticsController controller() {
        List<LogisticsController> all = controllers();
        return all.isEmpty() ? null : all.getFirst();
    }

    /** Every network's members it offers, never a receptacle on a Logistics Materialiser of the same network. */
    private List<LogisticsMember> members() {
        List<LogisticsMember> all = new ArrayList<>();
        for (LogisticsController controller : controllers()) all.addAll(membersOf(controller));
        return all;
    }

    /**
     * {@code controller}'s members, but one on a Materialiser of its own network (this one among them): that would read
     * the network into itself. Another network's is fine: that's another network's stock.
     */
    private List<LogisticsMember> membersOf(LogisticsController controller) {
        if (level == null) return List.of();
        return controller.members().stream()
                .filter(member -> !(level.getBlockEntity(member.inventoryPos()) instanceof LogisticsMaterialiserBlockEntity other)
                        || !other.controllers().contains(controller))
                .toList();
    }

    /** What it shows pipes and buses: every network's offers, as its mode lets them out, a slot an item. */
    private List<Entry> entries() {
        List<LogisticsController> controllers = controllers();
        if (level == null || controllers.isEmpty()) return List.of();
        List<com.kadikular.quantimium.laser.OfferTable> offers = new ArrayList<>(controllers.size());
        for (LogisticsController controller : controllers) offers.add(controller.offers());
        if (sameTables(offers, entriesFrom) && mode == entriesMode) return entries;
        java.util.Map<ItemResource, Long> merged = new java.util.LinkedHashMap<>();
        for (var table : offers) {
            for (var entry : table.only(offersReal(), offersCraftable()).entries()) {
                // Fluids go through its fluid handler, not as items.
                if (entry.item().is(com.kadikular.quantimium.init.ModItems.FLUID_ENTRY.get())) continue;
                long amount = (offersReal() ? entry.real() : 0) + (offersCraftable() ? entry.craftable() : 0);
                if (amount > 0) merged.merge(entry.item(), amount, Long::sum);
            }
        }
        List<Entry> list = new ArrayList<>();
        merged.forEach((item, amount) -> list.add(new Entry(item, amount)));
        entriesFrom = offers;
        entriesMode = mode;
        return entries = list;
    }

    /** Whether two lists hold the same tables, table for table. */
    private static boolean sameTables(List<com.kadikular.quantimium.laser.OfferTable> a, @Nullable List<com.kadikular.quantimium.laser.OfferTable> b) {
        if (b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }

    /** Everything it shows pipes and buses, and how many of each. */
    public void forEachListed(java.util.function.ObjLongConsumer<ItemResource> each) {
        for (Entry entry : entries()) each.accept(entry.item(), entry.amount());
    }

    /** How many of {@code item} it shows pipes and buses: what's there and what would be made, as its mode says. */
    public long listed(ItemResource item) {
        List<Entry> list = entries();
        if (list != byItemFrom || byItem == null) {
            byItem = new java.util.HashMap<>();
            for (Entry entry : list) byItem.merge(entry.item(), entry.amount(), Long::sum);
            byItemFrom = list;
        }
        return byItem.getOrDefault(item, 0L);
    }

    @Nullable
    private java.util.Map<ItemResource, Long> byItem;
    @Nullable
    private List<Entry> byItemFrom;

    /**
     * Which items it shows and how many, as one hash and one count: what a Crafter drawing on it through a Tesseract
     * checks every so often to see whether to work its preview out again. Kept while what it shows is.
     */
    public com.kadikular.quantimium.recipe.EntangledLinks.RemoteSummary summary() {
        List<Entry> list = entries();
        if (list != summarised || summary == null) {
            java.util.Set<Integer> ids = new java.util.TreeSet<>();
            long count = 0;
            for (Entry entry : list) {
                ItemStack stack = entry.item().toStack(1);
                if (com.kadikular.quantimium.recipe.EntangledLinks.isLink(stack)) continue;
                ids.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(stack.getItem()));
                count = Math.min(Long.MAX_VALUE / 2, count + entry.amount());
            }
            long hash = 1;
            for (int id : ids) hash = hash * 31 + id;
            summary = new com.kadikular.quantimium.recipe.EntangledLinks.RemoteSummary(hash, count);
            summarised = list;
        }
        return summary;
    }

    @Nullable
    private com.kadikular.quantimium.recipe.EntangledLinks.RemoteSummary summary;
    @Nullable
    private List<Entry> summarised;

    /**
     * The network as a handler. A move happens at once, inside the caller's transaction: the members' inventories
     * are reached through {@link com.kadikular.quantimium.util.LegacyItems#legacy}, so they join it, and an abort
     * puts everything back. What it says it moved, it moved. The controller is paid a move each when the
     * outermost transaction commits.
     */
    private final class NetworkHandler extends SnapshotJournal<java.util.Map<LogisticsController, Integer>> implements ResourceHandler<ItemResource> {
        /** Moves made in open transactions, by the network they were made on, paid for when the outermost commits. */
        private java.util.Map<LogisticsController, Integer> moves = new java.util.HashMap<>();

        @Override
        public int size() {
            return controllers().isEmpty() ? 0 : entries().size() + 1;
        }

        @Override
        public ItemResource getResource(int index) {
            List<Entry> list = entries();
            return index >= 0 && index < list.size() ? list.get(index).item() : ItemResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            List<Entry> list = entries();
            return index >= 0 && index < list.size() ? list.get(index).amount() : 0;
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return Long.MAX_VALUE;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return !controllers().isEmpty();
        }

        /** Into each network's storage in turn, those with the power for it. */
        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) return 0;
            int put = 0;
            for (LogisticsController controller : controllers()) {
                if (put >= amount || !controller.powered()) continue;
                ItemStack left = Logistics.insert(membersOf(controller), resource.toStack(amount - put), false, false);
                int more = amount - put - left.getCount();
                if (more > 0) {
                    moved(controller, transaction);
                    controller.stockShifted();
                }
                put += more;
            }
            return put;
        }

        /** From each network in turn. */
        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) return 0;
            int got = 0;
            for (LogisticsController controller : controllers()) {
                if (got < amount && controller.powered()) got += extractFrom(controller, resource, amount - got, transaction);
            }
            return got;
        }

        /** Up to {@code amount} of {@code resource} from {@code controller}'s network. */
        private int extractFrom(LogisticsController controller, ItemResource resource, int amount, TransactionContext transaction) {
            int got = 0;
            // Only where the network last saw it: a storage bus asking about every item each time it looks would
            // otherwise look over every slot of the network for each.
            java.util.Set<LogisticsMember> members = new java.util.HashSet<>(membersOf(controller));
            var spots = controller.spots();
            // A Crafter making something draws on what's stored only: a craft of a craft is a Reactor's to plan.
            boolean made = offersCraftable() && com.kadikular.quantimium.recipe.CraftChain.depth() == 0;
            while (got < amount) {
                ItemStack more = Logistics.take(spots, members, resource, amount - got, false, offersReal(), made);
                if (more.isEmpty()) {
                    // Short of what the network says it has: looked over again, once, in case it's been moved since.
                    var offers = controller.offers();
                    if (offers.real(resource) + (made ? offers.craftable(resource) : 0) <= 0) break;
                    var again = controller.spotsAgain();
                    if (again == null) break;
                    spots = again;
                    more = Logistics.take(spots, members, resource, amount - got, false, offersReal(), made);
                    if (more.isEmpty()) break;
                }
                got += more.getCount();
            }
            if (got > 0) moved(controller, transaction);
            return got;
        }

        private void moved(LogisticsController controller, TransactionContext transaction) {
            updateSnapshots(transaction);
            moves.merge(controller, 1, Integer::sum);
        }

        @Override
        protected java.util.Map<LogisticsController, Integer> createSnapshot() {
            return new java.util.HashMap<>(moves);
        }

        @Override
        protected void revertToSnapshot(java.util.Map<LogisticsController, Integer> snapshot) {
            moves = snapshot;
        }

        @Override
        protected void onRootCommit(java.util.Map<LogisticsController, Integer> originalState) {
            moves.forEach((controller, made) -> {
                for (int i = 0; i < made; i++) controller.pay(LaserTuning.LOGISTICS_FE_PER_MOVE);
                if (made > 0) controller.movedQuietly();
            });
            moves = new java.util.HashMap<>();
        }
    }

    // ---- the network: light ends here ----

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    @Override
    public Set<Direction> outputs() {
        return Set.of();
    }

    @Override
    public int range() {
        return LaserTuning.RELAY_RANGE;
    }

    /** It passes nothing on; a relay only so the light reaching it is known. */
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
}

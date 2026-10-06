package com.kadikular.quantimium.compat.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.GenericStack;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPart;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity;
import com.kadikular.quantimium.block.entity.ReactorPortBlockEntity;
import com.kadikular.quantimium.reactor.ReactorNetwork;
import com.kadikular.quantimium.reactor.ReactorRecipes;
import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ME Superposition Port: a Reactor port on an ME network, in both directions.
 *
 * <ul>
 *   <li><b>What the Reactor holds</b> is stock on the network, to see and take like a drive's.</li>
 *   <li><b>Its recipes</b> are patterns, as the ME Superposition Crafter's are, so AE2 plans with them
 *   from everything on the network, the Reactor's holdings included: Matter in a drive becomes an
 *   anvil through four of them, and AE2 knows exactly how many it can make and what's missing.</li>
 *   <li><b>What the network holds</b> the Reactor counts and uses as its own inputs when asked from its
 *   own screen ({@link ReactorNetwork}).</li>
 * </ul>
 *
 * <p>What it could make is never shown as stock: those counts share their sources, and AE2 would plan
 * to use two of them from one log. With patterns AE2 does the sums itself.
 *
 * <p>None of this can loop back on itself: the core reads and takes from the network only with its
 * own ports turned away ({@link HorizonCoreBlockEntity#isDrawing()}), so what this port offers is never
 * counted as network stock, and a Materialiser Port this network reads through a storage bus goes dark.
 */
public class ReactorMePortBlockEntity extends ReactorPortBlockEntity
        implements IInWorldGridNodeHost, IActionHost, IStorageProvider, ICraftingProvider, ReactorNetwork {

    private static final IGridNodeListener<ReactorMePortBlockEntity> LISTENER = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(ReactorMePortBlockEntity owner, IGridNode node) {
            owner.setChanged();
        }

        @Override
        public void onStateChanged(ReactorMePortBlockEntity owner, IGridNode node, State state) {
            owner.patternsFrom = null;
        }
    };

    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, LISTENER)
            .setVisualRepresentation(Ae2Content.REACTOR_ME_PORT_ITEM.get())
            .setInWorldNode(true)
            .setTagName("node")
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .setIdlePowerUsage(2.0)
            .addService(IStorageProvider.class, this)
            .addService(ICraftingProvider.class, this);

    private final OfferStorage offer = new OfferStorage();
    /** The Reactor's recipes as patterns, and the recipes they were made from. */
    private List<IPatternDetails> patterns = List.of();
    @Nullable
    private ReactorRecipes patternsFrom;
    /** What patterns have made, waiting to go to the network: never from inside a push. */
    private final List<GenericStack> pending = new ArrayList<>();

    public ReactorMePortBlockEntity(BlockPos pos, BlockState state) {
        super(Ae2Content.REACTOR_ME_PORT_BE.get(), pos, state);
    }

    @Nullable
    private IGrid grid() {
        return mainNode.isActive() ? mainNode.getGrid() : null;
    }

    private IActionSource source() {
        return IActionSource.ofMachine(this);
    }

    // ---- the Reactor's side ----

    @Override
    @Nullable
    public Object network() {
        return core() == null ? null : grid();
    }

    @Override
    public Map<ItemResource, Long> stock() {
        IGrid grid = grid();
        if (grid == null) return Map.of();
        Map<ItemResource, Long> stock = new HashMap<>();
        for (var entry : grid.getStorageService().getInventory().getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey item && entry.getLongValue() > 0) {
                stock.merge(item.toResource(), entry.getLongValue(), Long::sum);
            }
        }
        return stock;
    }

    @Override
    public long extract(ItemResource item, long amount, boolean simulate) {
        IGrid grid = grid();
        if (grid == null || amount <= 0) return 0;
        return grid.getStorageService().getInventory().extract(AEItemKey.of(item), amount,
                simulate ? Actionable.SIMULATE : Actionable.MODULATE, source());
    }

    /**
     * Whether a part on this network that mounts storage, a storage bus, sits at {@code pos} on its
     * {@code side}: what it reads is shown to this network.
     */
    @Override
    public boolean readsFrom(Level level, BlockPos pos, Direction side) {
        IGrid grid = grid();
        if (grid == null) return false;
        IPart part = PartHelper.getPart(level, pos, side);
        IGridNode node = part == null ? null : part.getGridNode();
        return node != null && node.getGrid() == grid && node.getService(IStorageProvider.class) != null;
    }

    // ---- the network's side ----

    @Override
    public void mountInventories(IStorageMounts mounts) {
        mounts.mount(offer);
    }

    /** The Reactor's held items as a storage: seen and taken, never filled (Input ports do that). */
    private final class OfferStorage implements MEStorage {
        @Nullable
        private HorizonCoreBlockEntity visibleCore() {
            HorizonCoreBlockEntity horizon = core();
            return horizon == null || horizon.isDrawing() || !horizon.isFormed() ? null : horizon;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            HorizonCoreBlockEntity horizon = visibleCore();
            if (horizon == null) return;
            horizon.getLedger().view().forEach((item, amount) -> out.add(AEItemKey.of(item), amount));
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            HorizonCoreBlockEntity horizon = visibleCore();
            if (horizon == null || !(what instanceof AEItemKey key) || amount <= 0) return 0;
            ItemResource item = key.toResource();
            long available = Math.min(amount, horizon.getLedger().count(item));
            if (available <= 0) return 0;
            return mode == Actionable.MODULATE ? horizon.withdraw(item, available) : available;
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            return 0;
        }

        @Override
        public Component getDescription() {
            return Component.translatable("block.quantimium.reactor_me_port");
        }
    }

    // ---- patterns ----

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return patterns;
    }

    /** One pattern for each of the Reactor's recipes, once; recipes that keep a tool aren't patterns. */
    private void refreshPatterns(HorizonCoreBlockEntity horizon) {
        ReactorRecipes recipes = horizon.getRecipes();
        if (recipes == patternsFrom) return;
        patternsFrom = recipes;
        Map<net.minecraft.resources.Identifier, IPatternDetails> byId = new java.util.LinkedHashMap<>();
        for (ReactorRecipes.Producer producer : recipes.producers()) {
            if (!producer.shape().tools().isEmpty()) continue;
            byId.putIfAbsent(producer.shape().id(),
                    new SuperpositionPattern(producer.shape(), 1, Ae2Content.REACTOR_ME_PORT_ITEM.get()));
        }
        patterns = List.copyOf(byId.values());
        ICraftingProvider.requestUpdate(mainNode);
    }

    /** The network has taken the inputs out of storage for this run: they're used up, and the Reactor pays. */
    @Override
    public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) {
        HorizonCoreBlockEntity horizon = core();
        if (!(details instanceof SuperpositionPattern pattern) || horizon == null || grid() == null
                || !patterns.contains(pattern)) {
            return false;
        }
        if (!horizon.payForRuns(pattern.shape(), pattern.batch())) return false;
        pending.addAll(pattern.getOutputs());
        setChanged();
        return true;
    }

    /** Results it may hold for the network at once; past this it waits for the network to take them. */
    private static final int MAX_PENDING = 1024;

    /**
     * Only when the network has stopped taking results: otherwise it takes as many runs a tick as the
     * network's crafting CPUs send, paying for each, and hands the results over on its next tick. A
     * crafting CPU sends one run each time it has an operation to spare, so co-processors are what
     * make a job go faster.
     */
    @Override
    public boolean isBusy() {
        return pending.size() >= MAX_PENDING;
    }

    /** Hands what patterns made to the network; whatever it can't take waits for the next tick. */
    private void flush(IGrid grid) {
        MEStorage network = grid.getStorageService().getInventory();
        for (int i = 0; i < pending.size(); i++) {
            GenericStack stack = pending.get(i);
            long sent = network.insert(stack.what(), stack.amount(), Actionable.MODULATE, source());
            if (sent >= stack.amount()) pending.remove(i--);
            else if (sent > 0) pending.set(i, new GenericStack(stack.what(), stack.amount() - sent));
        }
        if (!pending.isEmpty()) setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ReactorMePortBlockEntity port) {
        IGrid grid = port.grid();
        if (grid == null) return;
        if (!port.pending.isEmpty()) port.flush(grid);
        HorizonCoreBlockEntity horizon = port.core();
        if (horizon != null && level.getGameTime() % 20 == 0) port.refreshPatterns(horizon);
    }

    /** Whether it's on a network and a Reactor, and how much it offers the network as craftable. */
    @Override
    @Nullable
    public net.minecraft.network.chat.MutableComponent fluxMeterLine() {
        if (core() == null) {
            return Component.translatable("message.quantimium.reactor_port.unlinked").withStyle(net.minecraft.ChatFormatting.GRAY);
        }
        if (grid() == null) {
            return Component.translatable("message.quantimium.reactor_me_port.offline").withStyle(net.minecraft.ChatFormatting.YELLOW);
        }
        return Component.translatable("message.quantimium.reactor_me_port.online",
                String.format(java.util.Locale.ROOT, "%,d", core().getLedger().view().size()),
                String.format(java.util.Locale.ROOT, "%,d", patterns.size())).withStyle(net.minecraft.ChatFormatting.DARK_AQUA);
    }

    // ---- ME node ----

    @Override
    public void onLoad() {
        super.onLoad();
        GridHelper.onFirstTick(this, be -> be.mainNode.create(be.getLevel(), be.getBlockPos()));
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        mainNode.destroy();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        mainNode.destroy();
    }

    @Nullable
    @Override
    public IGridNode getGridNode(Direction dir) {
        return mainNode.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    @Nullable
    @Override
    public IGridNode getActionableNode() {
        return mainNode.getNode();
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        TagValueOutput node = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, NbtCompat.registries(level));
        mainNode.serialize(node);
        CompoundTag tag = new CompoundTag();
        tag.merge(node.buildResult());
        out.store("Grid", CompoundTag.CODEC, tag);
        out.store("Pending", GenericStack.CODEC.listOf(), List.copyOf(pending));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.read("Grid", CompoundTag.CODEC).ifPresent(tag ->
                mainNode.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, NbtCompat.lookup(in), tag)));
        pending.clear();
        pending.addAll(in.read("Pending", GenericStack.CODEC.listOf()).orElse(List.of()));
    }
}

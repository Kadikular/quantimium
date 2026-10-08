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
import com.kadikular.quantimium.reactor.ReactorCounter;
import com.kadikular.quantimium.reactor.ReactorNetwork;
import com.kadikular.quantimium.reactor.ReactorPlanner;
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
 *   <li><b>What it can make</b> is patterns, in one of three {@link Mode}s: by default one pattern a
 *   thing, the whole tree in one run (31 Matter in, an anvil out); or one pattern a recipe, as the ME
 *   Superposition Crafter's are, for AE2 to plan and run step by step; or both. Either way AE2 plans
 *   from everything on the network, the Reactor's holdings included, and knows exactly how many it can
 *   make and what's missing.</li>
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
        implements IInWorldGridNodeHost, IActionHost, IStorageProvider, ICraftingProvider, ReactorNetwork,
        net.minecraft.world.MenuProvider {

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
    private Mode mode = Mode.BOTH;

    /** Entries in each of the filter's two lists: patterns (by what they make), then storage (what's shown). */
    public static final int FILTER_SLOTS = 27;
    public static final int STORAGE_FILTER_START = FILTER_SLOTS;
    /** Ghost stacks, never real items: what to offer patterns for, then which held items the network sees. */
    private final net.minecraft.world.SimpleContainer filter = new net.minecraft.world.SimpleContainer(FILTER_SLOTS * 2) {
        @Override
        public void setChanged() {
            super.setChanged();
            filtersChanged();
        }
    };
    /** Whether each list is a whitelist: patterns start as one (empty, it offers everything), storage too. */
    private boolean patternsAllow = true;
    private boolean storageAllow = true;
    /** The priority of its storage and its patterns, as a storage bus's. */
    private int priority;
    /** Whether the network may store items in the Reactor, as in a drive. Off unless chosen. */
    private boolean acceptsItems;
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
        mounts.mount(offer, priority);
    }

    @Override
    public int getPatternPriority() {
        return priority;
    }

    // ---- settings ----

    public net.minecraft.world.SimpleContainer getFilter() {
        return filter;
    }

    public int priority() {
        return priority;
    }

    public void addPriority(int delta) {
        priority = (int) Math.clamp((long) priority + delta, -999_999L, 999_999L);
        IStorageProvider.requestUpdate(mainNode);
        ICraftingProvider.requestUpdate(mainNode);
        setChanged();
        sync();
    }

    public boolean patternsAllow() {
        return patternsAllow;
    }

    public boolean storageAllow() {
        return storageAllow;
    }

    public boolean acceptsItems() {
        return acceptsItems;
    }

    public void togglePatternsAllow() {
        patternsAllow = !patternsAllow;
        filtersChanged();
    }

    public void toggleStorageAllow() {
        storageAllow = !storageAllow;
        filtersChanged();
    }

    public void toggleAcceptsItems() {
        acceptsItems = !acceptsItems;
        setChanged();
        sync();
    }

    /** Sets entry {@code slot} (patterns 0-26, storage 27-53) to plain {@code stack}, or clears it. */
    public void setFilterSlot(int slot, net.minecraft.world.item.ItemStack stack) {
        if (slot >= 0 && slot < filter.getContainerSize()) filter.setItem(slot, com.kadikular.quantimium.recipe.FilterEntry.of(stack));
    }

    /** Shift-click: steps the entry through its item's tags and back. */
    public void cycleFilterTag(int slot) {
        if (slot < 0 || slot >= filter.getContainerSize()) return;
        var entry = filter.getItem(slot);
        if (!entry.isEmpty()) filter.setItem(slot, com.kadikular.quantimium.recipe.FilterEntry.cycle(entry));
    }

    /** What may be offered patterns for, and which held items the network sees, as the lists stand. */
    private com.kadikular.quantimium.recipe.RecipeFilter patternFilter = com.kadikular.quantimium.recipe.RecipeFilter.NONE;
    private com.kadikular.quantimium.recipe.RecipeFilter storageFilter = com.kadikular.quantimium.recipe.RecipeFilter.NONE;

    private void filtersChanged() {
        patternFilter = com.kadikular.quantimium.recipe.RecipeFilter.of(filter, FILTER_SLOTS, patternsAllow, false);
        // The storage list sits in the second half: read as a filter's outputs, whitelist or blacklist.
        var storage = new net.minecraft.world.SimpleContainer(FILTER_SLOTS);
        for (int i = 0; i < FILTER_SLOTS; i++) storage.setItem(i, filter.getItem(STORAGE_FILTER_START + i));
        storageFilter = com.kadikular.quantimium.recipe.RecipeFilter.of(storage, FILTER_SLOTS, storageAllow, false);
        patternsFrom = null;
        IStorageProvider.requestUpdate(mainNode);
        setChanged();
        sync();
    }

    private boolean shows(ItemResource item) {
        return storageFilter.offers(item.toStack(1));
    }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.reactor_me_port");
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory,
                                                                        net.minecraft.world.entity.player.Player player) {
        return new ReactorMePortMenu(containerId, inventory, this);
    }

    public static final int DATA_COUNT = 7;

    /**
     * What the screen shows: whether it's linked and online, how many kinds held are shown, how many
     * patterns, the priority (two halves), the mode and the switches. Server side.
     */
    public final net.minecraft.world.inventory.ContainerData data = new net.minecraft.world.inventory.ContainerData() {
        @Override
        public int get(int index) {
            HorizonCoreBlockEntity horizon = core();
            return switch (index) {
                case 0 -> horizon == null ? 0 : grid() == null ? 1 : 2;
                case 1 -> horizon == null ? 0 : (int) Math.min(Integer.MAX_VALUE,
                        horizon.getLedger().view().keySet().stream().filter(ReactorMePortBlockEntity.this::shows).count());
                case 2 -> patterns.size();
                case 3 -> priority & 0xFFFF;
                case 4 -> (priority >>> 16) & 0xFFFF;
                case 5 -> mode.ordinal();
                case 6 -> (patternsAllow ? 1 : 0) | (storageAllow ? 2 : 0) | (acceptsItems ? 4 : 0);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    /**
     * The Reactor's held items as a storage, through the storage list: seen and taken, and filled too if
     * the network may store items in it.
     */
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
            horizon.getLedger().view().forEach((item, amount) -> {
                if (shows(item)) out.add(AEItemKey.of(item), amount);
            });
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            HorizonCoreBlockEntity horizon = visibleCore();
            if (horizon == null || !(what instanceof AEItemKey key) || amount <= 0) return 0;
            ItemResource item = key.toResource();
            if (!shows(item)) return 0;
            long available = Math.min(amount, horizon.availableCount(item));
            if (available <= 0) return 0;
            return mode == Actionable.MODULATE ? horizon.withdraw(item, available) : available;
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            HorizonCoreBlockEntity horizon = visibleCore();
            if (!acceptsItems || horizon == null || !horizon.accepts() || !(what instanceof AEItemKey key) || amount <= 0) return 0;
            ItemResource item = key.toResource();
            if (!shows(item)) return 0;
            long fits = Math.min(amount, horizon.room());
            if (fits > 0 && mode == Actionable.MODULATE) horizon.store(item, fits);
            return fits;
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

    /** How the Reactor's making is offered to the network: set by using the port. */
    public enum Mode {
        /** Both (the default): whole trees for speed, and every recipe as a step for AE2 to fall back on. */
        BOTH,
        /** One pattern a thing it can make, the whole tree in one run. */
        TREES,
        /** One pattern a recipe; AE2 plans the tree and runs each step. */
        STEPS;

        boolean trees() {
            return this != STEPS;
        }

        boolean steps() {
            return this != TREES;
        }
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        patternsFrom = null;
        setChanged();
    }

    /** The next mode, or the one before; the patterns follow on the next tick. */
    public Mode cycleMode(boolean back) {
        Mode[] all = Mode.values();
        mode = all[(mode.ordinal() + (back ? all.length - 1 : 1)) % all.length];
        patternsFrom = null;
        setChanged();
        sync();
        return mode;
    }

    /** Things whose trees to plan next, a few a tick, and those already queued. */
    private static final int TREES_PER_TICK = 32;
    private final java.util.ArrayDeque<ItemResource> toPlan = new java.util.ArrayDeque<>();
    private final java.util.Set<ItemResource> queued = new java.util.HashSet<>();
    private final Map<ItemResource, ReactorTreePattern> trees = new java.util.LinkedHashMap<>();
    /** Trees replaced while a crafting job might still be running one: offered until no CPU is busy. */
    private final List<IPatternDetails> retired = new ArrayList<>();
    /** Where the slow look over every tree has got to, so better routes are found in time. */
    private java.util.Iterator<ItemResource> rolling = java.util.Collections.emptyIterator();
    @Nullable
    private ReactorCounter.Counts queuedFrom;
    private boolean patternsChanged;

    private void queue(ItemResource item, boolean first) {
        if (!queued.add(item)) return;
        if (first) toPlan.addFirst(item);
        else toPlan.addLast(item);
    }

    /**
     * Keeps the patterns in step with the Reactor. A recipe change plans everything again. Each thing it
     * newly can make has its tree planned from the stock of the moment; a tree whose inputs have gone
     * from the network is planned again at once, and every tree is looked at again now and then, so a
     * better route turns up. A tree replaced while a crafting job may be running it stays offered until
     * no CPU is busy, so the job still finds it.
     */
    private void refreshPatterns(HorizonCoreBlockEntity horizon, IGrid grid, long gameTime) {
        ReactorRecipes recipes = horizon.getRecipes();
        if (recipes != patternsFrom) {
            patternsFrom = recipes;
            retired.addAll(trees.values());
            trees.clear();
            toPlan.clear();
            queued.clear();
            rolling = java.util.Collections.emptyIterator();
            queuedFrom = null;
            patternsChanged = true;
        }
        if (!retired.isEmpty() && grid.getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy())) {
            retired.clear();
            patternsChanged = true;
        }
        if (mode.trees() && horizon.isActive() && level instanceof net.minecraft.server.level.ServerLevel server) {
            ReactorCounter.Counts counts = horizon.getCounts();
            if (counts != queuedFrom) {
                queuedFrom = counts;
                counts.counts().forEach((item, amount) -> {
                    if (amount > 0 && !trees.containsKey(item) && !recipes.producersOf(item).isEmpty()
                            && patternFilter.offers(item.toStack(1))) {
                        queue(item, false);
                    }
                });
            }
            Map<ItemResource, Long> stock = horizon.stockWithNetworks(server);
            if (gameTime % 20 == 0) {
                trees.forEach((item, tree) -> {
                    if (!tree.findsItsInputsIn(stock)) queue(item, true);
                });
            }
            if (toPlan.isEmpty()) {
                if (!rolling.hasNext()) rolling = List.copyOf(trees.keySet()).iterator();
                if (rolling.hasNext()) queue(rolling.next(), false);
            }
            for (int i = 0; i < TREES_PER_TICK && !toPlan.isEmpty(); i++) {
                ItemResource item = toPlan.poll();
                queued.remove(item);
                if (!patternFilter.offers(item.toStack(1))) {
                    ReactorTreePattern gone = trees.remove(item);
                    if (gone != null) {
                        retired.add(gone);
                        patternsChanged = true;
                    }
                    continue;
                }
                // A tree that can't be planned now keeps its last pattern: AE2 says what's missing.
                ReactorTreePattern tree = planTree(horizon, stock, item);
                if (tree == null) continue;
                ReactorTreePattern old = trees.put(item, tree);
                if (!tree.equals(old)) {
                    if (old != null) retired.add(old);
                    patternsChanged = true;
                }
            }
        }
        // Told at most once a second while trees are still being planned, and at once when they're done.
        if (patternsChanged && (toPlan.isEmpty() || gameTime % 20 == 0)) {
            patternsChanged = false;
            List<IPatternDetails> all = new ArrayList<>();
            if (mode.trees()) all.addAll(trees.values());
            all.addAll(retired);
            if (mode.steps()) {
                Map<net.minecraft.resources.Identifier, IPatternDetails> byId = new java.util.LinkedHashMap<>();
                for (ReactorRecipes.Producer producer : recipes.producers()) {
                    if (!patternFilter.offers(producer.shape().primaryOutput())) continue;
                    byId.putIfAbsent(producer.shape().id(),
                            new SuperpositionPattern(producer.shape(), 1, Ae2Content.REACTOR_ME_PORT_ITEM.get(), true));
                }
                all.addAll(byId.values());
            }
            patterns = List.copyOf(new java.util.LinkedHashSet<>(all));
            ICraftingProvider.requestUpdate(mainNode);
        }
    }

    /**
     * One of {@code item}, planned from {@code stock} without any already made: the whole tree as one
     * pattern. A tool the tree keeps and doesn't make, such as a press, is that item, handed back; a tool
     * it wears, such as a knife, is any that fits, handed back worn.
     */
    @Nullable
    private static ReactorTreePattern planTree(HorizonCoreBlockEntity horizon, Map<ItemResource, Long> stock,
                                               ItemResource item) {
        Map<ItemResource, Long> without = new HashMap<>(stock);
        without.remove(item);
        // A tool the tree would make for itself, it plans as if one were on hand: the pattern then asks
        // for one, which AE2 makes once and hands over run after run, instead of every run making its own.
        ReactorPlanner.Plan plan = null;
        for (int pass = 0; pass < 3; pass++) {
            ReactorPlanner.Result result = horizon.plan(without, item, 1);
            if (!result.planned()) return null;
            plan = result.plan();
            boolean lent = false;
            for (ReactorPlanner.Step step : plan.steps()) {
                for (net.minecraft.world.item.crafting.Ingredient tool : step.shape().tools()) {
                    if (without.keySet().stream().anyMatch(have -> tool.test(have.toStack(1)))) continue;
                    List<net.minecraft.world.item.ItemStack> options = com.kadikular.quantimium.recipe.RecipeCompat.stacks(tool);
                    if (options.isEmpty() || tool.test(item.toStack(1))) continue;
                    without.put(ItemResource.of(options.getFirst()), 1L);
                    lent = true;
                }
            }
            if (!lent) break;
        }
        List<ItemResource> kept = new ArrayList<>();
        List<ReactorTreePattern.Worn> worn = new ArrayList<>();
        for (ReactorPlanner.Step step : plan.steps()) {
            for (net.minecraft.world.item.crafting.Ingredient tool : step.shape().tools()) {
                if (step.shape().wears().stream().anyMatch(wears -> wears == tool)) {
                    // Any knife, handed back worn; one the tree still had to make stays as planned.
                    if (without.keySet().stream().noneMatch(have -> tool.test(have.toStack(1)))) continue;
                    int uses = (int) Math.min(Integer.MAX_VALUE, step.runs());
                    int same = -1;
                    for (int i = 0; i < worn.size(); i++) if (worn.get(i).tool() == tool) same = i;
                    if (same >= 0) worn.set(same, new ReactorTreePattern.Worn(tool, worn.get(same).uses() + uses));
                    else worn.add(new ReactorTreePattern.Worn(tool, uses));
                    continue;
                }
                if (plan.leftovers().keySet().stream().anyMatch(left -> tool.test(left.toStack(1)))) continue; // made
                if (kept.stream().anyMatch(have -> tool.test(have.toStack(1)))) continue;
                ItemResource held = without.keySet().stream().filter(have -> tool.test(have.toStack(1))).findFirst().orElse(null);
                if (held == null) return null;
                kept.add(held);
            }
        }
        return ReactorTreePattern.of(plan, HorizonCoreBlockEntity.feFor(plan), kept, worn);
    }

    /** The network has taken the inputs out of storage for this run: they're used up, and the Reactor pays. */
    @Override
    public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) {
        HorizonCoreBlockEntity horizon = core();
        if (horizon == null || grid() == null || !patterns.contains(details)) return false;
        boolean paid = details instanceof ReactorTreePattern tree ? horizon.pay(tree.fe())
                : details instanceof SuperpositionPattern step && horizon.payForRuns(step.shape(), step.batch());
        if (!paid) return false;
        pending.addAll(details.getOutputs());
        // Tools come back, worn if they wear: AE2 is waiting for them.
        IPatternDetails.IInput[] patternInputs = details.getInputs();
        for (int i = 0; i < patternInputs.length && i < inputs.length; i++) {
            for (var entry : inputs[i]) {
                AEKey left = patternInputs[i].getRemainingKey(entry.getKey());
                if (left != null) pending.add(new GenericStack(left, entry.getLongValue()));
            }
        }
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
        if (horizon != null) port.refreshPatterns(horizon, grid, level.getGameTime());
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
                String.format(java.util.Locale.ROOT, "%,d", patterns.size()),
                Component.translatable("message.quantimium.reactor_me_port.mode." + mode.name().toLowerCase(java.util.Locale.ROOT) + ".short"))
                .withStyle(net.minecraft.ChatFormatting.DARK_AQUA);
    }

    /** Broken, it drops what it made and hadn't yet handed to the network. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) return;
        for (GenericStack stack : pending) {
            if (!(stack.what() instanceof AEItemKey item)) continue;
            long left = stack.amount();
            while (left > 0) {
                int count = (int) Math.min(left, item.getReadOnlyStack().getMaxStackSize());
                net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), item.toStack(count));
                left -= count;
            }
        }
        pending.clear();
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
        out.putString("Mode", mode.name());
        List<net.minecraft.world.item.ItemStack> entries = new ArrayList<>();
        for (int i = 0; i < filter.getContainerSize(); i++) entries.add(filter.getItem(i));
        out.store("Filter", net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.listOf(), entries);
        out.putBoolean("PatternsAllow", patternsAllow);
        out.putBoolean("StorageAllow", storageAllow);
        out.putInt("Priority", priority);
        out.putBoolean("AcceptsItems", acceptsItems);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.read("Grid", CompoundTag.CODEC).ifPresent(tag ->
                mainNode.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, NbtCompat.lookup(in), tag)));
        pending.clear();
        pending.addAll(in.read("Pending", GenericStack.CODEC.listOf()).orElse(List.of()));
        try {
            mode = Mode.valueOf(in.getStringOr("Mode", Mode.BOTH.name()));
        } catch (IllegalArgumentException e) {
            mode = Mode.BOTH;
        }
        List<net.minecraft.world.item.ItemStack> entries =
                in.read("Filter", net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < filter.getContainerSize(); i++) {
            filter.getItems().set(i, i < entries.size() ? entries.get(i) : net.minecraft.world.item.ItemStack.EMPTY);
        }
        patternsAllow = in.getBooleanOr("PatternsAllow", true);
        storageAllow = in.getBooleanOr("StorageAllow", true);
        priority = in.getIntOr("Priority", 0);
        acceptsItems = in.getBooleanOr("AcceptsItems", false);
        patternFilter = com.kadikular.quantimium.recipe.RecipeFilter.of(filter, FILTER_SLOTS, patternsAllow, false);
        var storage = new net.minecraft.world.SimpleContainer(FILTER_SLOTS);
        for (int i = 0; i < FILTER_SLOTS; i++) storage.setItem(i, filter.getItem(STORAGE_FILTER_START + i));
        storageFilter = com.kadikular.quantimium.recipe.RecipeFilter.of(storage, FILTER_SLOTS, storageAllow, false);
    }
}

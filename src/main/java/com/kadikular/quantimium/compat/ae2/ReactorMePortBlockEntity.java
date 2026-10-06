package com.kadikular.quantimium.compat.ae2;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The ME Superposition Port: a Reactor port on an ME network, in both directions.
 *
 * <ul>
 *   <li><b>What the Reactor holds</b> is storage on the network, to see and take like a drive's.</li>
 *   <li><b>What it could make</b> is craftable: AE2 shows it so, and a crafting job that needs one
 *   asks for it. The port makes what's asked through the Reactor's planner and hands it to the job.
 *   Never shown as stock, since the Reactor's counts share their sources and AE2 would add them up.</li>
 *   <li><b>What the network holds</b> the Reactor counts and uses as its own inputs
 *   ({@link ReactorNetwork}).</li>
 * </ul>
 *
 * <p>None of this can loop back on itself: the core reads and takes from the network only with its
 * own ports turned away ({@link HorizonCoreBlockEntity#isDrawing()}), and a Materialiser Port this
 * network reads through a storage bus goes dark.
 */
public class ReactorMePortBlockEntity extends ReactorPortBlockEntity
        implements IInWorldGridNodeHost, IActionHost, ICraftingProvider, IStorageProvider, ReactorNetwork {

    /** How often it answers crafting requests, and how many of one thing it makes at a time. */
    private static final int REQUEST_TICKS = 5;
    private static final long MAX_PER_REQUEST = 4096;
    private static final int MAX_KEYS_PER_CYCLE = 16;

    private static final IGridNodeListener<ReactorMePortBlockEntity> LISTENER = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(ReactorMePortBlockEntity owner, IGridNode node) {
            owner.setChanged();
        }

        @Override
        public void onStateChanged(ReactorMePortBlockEntity owner, IGridNode node, State state) {
            owner.craftablesFrom = null;
        }
    };

    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, LISTENER)
            .setVisualRepresentation(Ae2Content.REACTOR_ME_PORT_ITEM.get())
            .setInWorldNode(true)
            .setTagName("node")
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .setIdlePowerUsage(2.0)
            .addService(ICraftingProvider.class, this)
            .addService(IStorageProvider.class, this);

    private final HeldStorage held = new HeldStorage();
    /** What the Reactor could make, as AE2 keys, and the count they were taken from. */
    private Set<AEKey> craftables = Set.of();
    @Nullable
    private ReactorCounter.Counts craftablesFrom;

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
        mounts.mount(held);
    }

    /** The Reactor's held items as a storage: seen and taken, never filled (Input ports do that). */
    private final class HeldStorage implements MEStorage {
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

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return List.of();
    }

    @Override
    public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) {
        return false;
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    /** Everything the Reactor could make: craftable from nothing, as far as AE2 is concerned. */
    @Override
    public Set<AEKey> getEmitableItems() {
        return craftables;
    }

    /** Follows the Reactor's count: what it could make now is what AE2 may ask it for. */
    private void refreshCraftables(HorizonCoreBlockEntity horizon) {
        ReactorCounter.Counts counts = horizon.getCounts();
        if (counts == craftablesFrom) return;
        craftablesFrom = counts;
        Set<AEKey> keys = new HashSet<>();
        counts.counts().forEach((item, amount) -> {
            if (amount > 0 && !horizon.getRecipes().producersOf(item).isEmpty()) keys.add(AEItemKey.of(item));
        });
        // Told every time it's worked out again, which includes each time the node comes online: an
        // update asked for before the grid was up is lost.
        craftables = Set.copyOf(keys);
        ICraftingProvider.requestUpdate(mainNode);
    }

    /** Makes what crafting jobs are waiting for, and hands it to them through the network. */
    private void answerRequests(HorizonCoreBlockEntity horizon, IGrid grid) {
        ICraftingService crafting = grid.getCraftingService();
        if (!crafting.isRequestingAny()) return;
        MEStorage network = grid.getStorageService().getInventory();
        int answered = 0;
        for (AEKey key : craftables) {
            if (answered >= MAX_KEYS_PER_CYCLE) break;
            long wanted = crafting.getRequestedAmount(key);
            if (wanted <= 0 || !(key instanceof AEItemKey item)) continue;
            answered++;
            long made = horizon.produce(item.toResource(), Math.min(wanted, MAX_PER_REQUEST));
            if (made <= 0) continue;
            long sent = network.insert(key, made, Actionable.MODULATE, source());
            // Whatever the network wouldn't take goes back in the horizon it came from.
            if (sent < made) horizon.restore(item.toResource(), made - sent);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ReactorMePortBlockEntity port) {
        HorizonCoreBlockEntity horizon = port.core();
        IGrid grid = port.grid();
        if (horizon == null || grid == null || level.getGameTime() % REQUEST_TICKS != 0) return;
        port.refreshCraftables(horizon);
        if (horizon.isActive()) port.answerRequests(horizon, grid);
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
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.read("Grid", CompoundTag.CODEC).ifPresent(tag ->
                mainNode.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, NbtCompat.lookup(in), tag)));
    }
}

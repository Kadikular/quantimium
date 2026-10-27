package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.LaserRelayBlock;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.recipe.FilterEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import java.util.ArrayList;
import com.kadikular.quantimium.block.entity.simulation.SideAutomationProfile;
import com.kadikular.quantimium.block.entity.simulation.SideConfig;
import com.kadikular.quantimium.block.entity.simulation.SideMode;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.laser.LaserBeam;
import com.kadikular.quantimium.laser.LaserBeamView;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.LaserNode;
import com.kadikular.quantimium.laser.LaserTuning;
import com.kadikular.quantimium.util.ItemStackHandler;
import com.kadikular.quantimium.util.LegacyItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * A Laser Port: nine slots, which light passes through and out of its face as through a mirror. It catches what
 * comes back up the beam (as its filter allows), sends what it holds down it (to a Placement lens or another port
 * further along; a bound Tesseract in a slot sends from the inventory it links to), or both: a port of seeds in
 * both feeds them straight back down to be sown. Its faces are set as a machine's are: which take items in or hand
 * them out, and which pull from or push to what's beside them, once a second. Pipes and hoppers can only put
 * things in while it sends.
 */
public class LaserPortBlockEntity extends BlockEntity implements LaserNode, FluxMeterReadout, LaserBeamView, MenuProvider, SideConfigurable {

    public static final int SLOTS = 9;
    public static final int FILTER_SLOTS = 9;
    private static final int AUTO_TRANSFER_INTERVAL = 20;
    private static final SideAutomationProfile PROFILE = new SideAutomationProfile(SideAutomationProfile.ALL_SIDES, 0);

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    /** What it does with items: catches what comes back up the beam, sends what it holds down it, or both. */
    public enum Mode implements net.minecraft.util.StringRepresentable {
        COLLECT, SEND, BOTH;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private Mode mode = Mode.COLLECT;
    /** Ghost entries, never real items: what it catches, or with a blacklist, what it lets go on up the beam. */
    private final SimpleContainer filter = new SimpleContainer(FILTER_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            LaserPortBlockEntity.this.setChanged();
        }
    };
    /** Whitelist (catch only these) or blacklist (catch all but these). An empty filter catches everything. */
    private boolean allow = true;
    private final IItemHandler[] sided = new IItemHandler[6];
    private List<SideConfig> sideConfigs;
    private List<LaserBeam> shownBeams = List.of();

    public LaserPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_PORT_BE.get(), pos, state);
        sideConfigs = PROFILE.sanitize(SideConfig.defaults().stream()
                .map(config -> new SideConfig(config.side(), SideMode.BOTH, SideConfig.ALL_ITEM_INPUTS, SideConfig.ALL_ITEM_OUTPUTS,
                        false, false, SideMode.DISABLED, 0, 0, false, false))
                .toList());
        for (Direction side : Direction.values()) sided[side.get3DDataValue()] = new Sided(side);
    }

    public Mode mode() {
        return mode;
    }

    /** Steps to the next of collect, send, both. */
    public void cycleMode() {
        mode = mode.next();
        setChanged();
        showMode();
        sync();
        invalidateCapabilities();
        if (level != null) LaserNetworks.changed(level);
    }

    /** Whether its filter lets it catch {@code stack} when it comes back up the beam. */
    public boolean acceptsItem(ItemStack stack) {
        boolean listed = false;
        boolean any = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack entry = filter.getItem(i);
            if (entry.isEmpty()) continue;
            any = true;
            if (FilterEntry.matches(entry, stack)) listed = true;
        }
        return !any || listed == allow;
    }

    public SimpleContainer getFilter() {
        return filter;
    }

    public boolean allows() {
        return allow;
    }

    /** Flips the filter between allow and deny. */
    public void toggleAllow() {
        allow = !allow;
        setChanged();
        sync();
    }

    public void setFilterSlot(int slot, ItemStack stack) {
        if (slot >= 0 && slot < FILTER_SLOTS) filter.setItem(slot, FilterEntry.of(stack));
    }

    public void cycleFilterTag(int slot) {
        if (slot < 0 || slot >= FILTER_SLOTS) return;
        ItemStack entry = filter.getItem(slot);
        if (!entry.isEmpty()) filter.setItem(slot, FilterEntry.cycle(entry));
    }

    @Override
    public boolean collects() {
        return mode != Mode.SEND;
    }

    @Override
    public ItemStack collect(ItemStack stack, boolean simulate) {
        // What it turns away goes on up the beam, to the next.
        if (!collects() || !acceptsItem(stack)) return stack;
        return insert(passOn(stack, simulate), simulate);
    }

    @Override
    public boolean sends() {
        return mode != Mode.COLLECT;
    }

    /** Up to {@code max} of the first thing {@code wants} accepts: from its slots, or through a bound Tesseract in one. */
    @Override
    public ItemStack send(java.util.function.Predicate<ItemStack> wants, int max, boolean simulate) {
        if (!sends()) return ItemStack.EMPTY;
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            if (com.kadikular.quantimium.recipe.EntangledLinks.isLink(stack)) {
                IItemHandler linked = com.kadikular.quantimium.recipe.EntangledLinks.resolve(level, worldPosition, stack);
                if (linked == null) continue;
                for (int i = 0; i < linked.getSlots(); i++) {
                    ItemStack there = linked.getStackInSlot(i);
                    if (!there.isEmpty() && wants.test(there)) return linked.extractItem(i, max, simulate);
                }
                continue;
            }
            if (wants.test(stack)) return items.extractItem(slot, max, simulate);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack takeBack(ItemStack stack) {
        return insert(stack, false);
    }

    /** Whether pipes and hoppers may put things in, on faces that take them: when it sends. */
    private boolean takesFromOutside() {
        return sends();
    }


    public ItemStackHandler getItems() {
        return items;
    }

    public Direction facing() {
        return getBlockState().getValue(LaserRelayBlock.FACING);
    }

    // ---- automation ----

    /** The quickest it moves things in and out of what's beside it while busy, in ticks. */
    private static final int BUSIEST_INTERVAL = 2;
    /** Ticks till it next moves things in and out, and how long it waits now: shorter while it's moving things. */
    private int transferIn = -1;
    private int transferInterval = AUTO_TRANSFER_INTERVAL;

    public static void serverTick(Level level, BlockPos pos, BlockState state, LaserPortBlockEntity relay) {
        // Staggered so ports don't all move on one tick, then paced: quicker each time something moved, slower each
        // time nothing did, between {@link #BUSIEST_INTERVAL} and once a second.
        if (relay.transferIn < 0) relay.transferIn = Math.floorMod(pos.hashCode(), AUTO_TRANSFER_INTERVAL);
        if (relay.transferIn-- > 0) return;
        boolean moved = false;
        for (Direction side : Direction.values()) {
            SideConfig config = relay.sideConfigs.get(side.get3DDataValue());
            if (!config.autoItemInput() && !config.autoItemOutput()) continue;
            IItemHandler neighbour = LegacyItems.legacy(level.getCapability(Capabilities.Item.BLOCK, pos.relative(side), side.getOpposite()));
            if (neighbour == null) continue;
            if (config.autoItemInput() && config.itemMode().allowsInput() && relay.takesFromOutside()) moved |= relay.pull(neighbour);
            if (config.autoItemOutput() && config.itemMode().allowsOutput()) moved |= relay.push(neighbour);
        }
        relay.transferInterval = moved ? Math.max(BUSIEST_INTERVAL, relay.transferInterval / 2) : Math.min(AUTO_TRANSFER_INTERVAL, relay.transferInterval * 2);
        relay.transferIn = relay.transferInterval - 1;
    }

    /**
     * Puts {@code stack} straight into what it exports to (its faces set to push out), so what comes back up the beam
     * needn't wait in its slots for the next push; what's left.
     */
    private ItemStack passOn(ItemStack stack, boolean simulate) {
        if (level == null || stack.isEmpty()) return stack;
        ItemStack left = stack;
        for (Direction side : Direction.values()) {
            SideConfig config = sideConfigs.get(side.get3DDataValue());
            if (!config.autoItemOutput() || !config.itemMode().allowsOutput()) continue;
            IItemHandler neighbour = LegacyItems.legacy(level.getCapability(Capabilities.Item.BLOCK, worldPosition.relative(side), side.getOpposite()));
            if (neighbour == null) continue;
            left = ItemHandlerHelper.insertItem(neighbour, left, simulate);
            if (left.isEmpty()) break;
        }
        // Moving things on: it pushes out sooner next time too.
        if (!simulate && left.getCount() < stack.getCount()) {
            transferInterval = BUSIEST_INTERVAL;
            transferIn = Math.min(transferIn, BUSIEST_INTERVAL);
        }
        return left;
    }

    /**
     * As {@link #collect}, but for one of several things dropped at once: what's passed on goes into the neighbours
     * inside {@code transaction} (rolled back by the caller), and its own slots are a pretend copy in {@code scratch},
     * so the drop's second thing finds the room its first took gone.
     */
    @Override
    public ItemStack collectAlong(ItemStack stack, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction,
                                  java.util.Map<Object, Object> scratch) {
        if (level == null || !collects() || !acceptsItem(stack)) return stack;
        ItemStack left = stack.copy();
        for (Direction side : Direction.values()) {
            SideConfig config = sideConfigs.get(side.get3DDataValue());
            if (!config.autoItemOutput() || !config.itemMode().allowsOutput()) continue;
            var neighbour = level.getCapability(Capabilities.Item.BLOCK, worldPosition.relative(side), side.getOpposite());
            if (neighbour == null) continue;
            int put = net.neoforged.neoforge.transfer.ResourceHandlerUtil.insertStacking(neighbour,
                    net.neoforged.neoforge.transfer.item.ItemResource.of(left), left.getCount(), transaction);
            left.shrink(put);
            if (left.isEmpty()) return ItemStack.EMPTY;
        }
        ItemStack[] own = (ItemStack[]) scratch.computeIfAbsent(this, key -> {
            ItemStack[] copy = new ItemStack[SLOTS];
            for (int slot = 0; slot < SLOTS; slot++) copy[slot] = items.getStackInSlot(slot).copy();
            return copy;
        });
        // Onto what's like it first, then into empty slots, as its slots take things.
        for (int pass = 0; pass < 2 && !left.isEmpty(); pass++) {
            for (int slot = 0; slot < SLOTS && !left.isEmpty(); slot++) {
                ItemStack there = own[slot];
                if (pass == 0 ? there.isEmpty() || !ItemStack.isSameItemSameComponents(there, left) : !there.isEmpty()) continue;
                if (!items.isItemValid(slot, left)) continue;
                int room = Math.min(items.getSlotLimit(slot), left.getMaxStackSize()) - there.getCount();
                if (room <= 0) continue;
                int moved = Math.min(room, left.getCount());
                own[slot] = there.isEmpty() ? left.copyWithCount(moved) : there.copyWithCount(there.getCount() + moved);
                left.shrink(moved);
            }
        }
        return left;
    }

    /** Takes a stack from {@code from}, as far as its slots take it; whether it took anything. */
    private boolean pull(IItemHandler from) {
        for (int source = 0; source < from.getSlots(); source++) {
            ItemStack offered = from.extractItem(source, 64, true);
            if (offered.isEmpty()) continue;
            int accepted = offered.getCount() - insert(offered, true).getCount();
            if (accepted <= 0) continue;
            insert(from.extractItem(source, accepted, false), false);
            return true;
        }
        return false;
    }

    /** Puts {@code stack} into its slots, as far as they take it; what's left. */
    private ItemStack insert(ItemStack stack, boolean simulate) {
        ItemStack left = stack;
        for (int slot = 0; slot < SLOTS && !left.isEmpty(); slot++) left = items.insertItem(slot, left, simulate);
        return left;
    }

    /** Pushes what its slots hold into {@code into}, every slot, as far as it takes them; whether anything moved. */
    private boolean push(IItemHandler into) {
        boolean moved = false;
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (stack.isEmpty() || com.kadikular.quantimium.recipe.EntangledLinks.isLink(stack)) continue;
            int count = stack.getCount() - ItemHandlerHelper.insertItem(into, stack.copy(), false).getCount();
            if (count > 0) {
                items.extractItem(slot, count, false);
                moved = true;
            }
        }
        return moved;
    }

    /** What a pipe or hopper on {@code side} sees, or null for a face that's off. */
    @Nullable
    public ResourceHandler<ItemResource> automation(@Nullable Direction side) {
        if (side == null) return LegacyItems.of(sided[Direction.UP.get3DDataValue()], items);
        SideConfig config = sideConfigs.get(side.get3DDataValue());
        return config.itemMode() == SideMode.DISABLED ? null : LegacyItems.of(sided[side.get3DDataValue()], items);
    }

    private final class Sided implements IItemHandler {
        private final Direction side;

        Sided(Direction side) {
            this.side = side;
        }

        private SideConfig config() {
            return sideConfigs.get(side.get3DDataValue());
        }

        @Override
        public int getSlots() {
            return SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!takesFromOutside() || !config().itemMode().allowsInput() || !config().allowsInputSlot(slot)) return stack;
            return items.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!config().itemMode().allowsOutput() || !config().allowsOutputSlot(slot)) return ItemStack.EMPTY;
            return items.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return takesFromOutside() && config().itemMode().allowsInput() && items.isItemValid(slot, stack);
        }
    }

    @Override
    public List<SideConfig> getSideConfigs() {
        return List.copyOf(sideConfigs);
    }

    @Override
    public void applySideConfigs(List<SideConfig> configs) {
        sideConfigs = PROFILE.sanitize(configs);
        setChanged();
        invalidateCapabilities();
    }

    @Override
    public boolean isUsableBy(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64;
    }

    @Override
    public SideAutomationProfile sideAutomationProfile() {
        return PROFILE;
    }

    // ---- logistics ----

    /** Its slots as a plain item handler, for the network. */
    private final IItemHandler itemsView = new IItemHandler() {
        @Override
        public int getSlots() {
            return SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return items.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return items.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return items.isItemValid(slot, stack);
        }
    };

    /**
     * How many of {@code item} it holds, with what's in each inventory beside it that it moves items to or from: what a
     * Redstone Lens counts.
     */
    public long count(ItemStack item) {
        long held = 0;
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (ItemStack.isSameItemSameComponents(stack, item)) held += stack.getCount();
        }
        if (level == null) return held;
        for (Direction side : Direction.values()) {
            SideConfig config = sideConfigs.get(side.get3DDataValue());
            if (!config.autoItemInput() && !config.autoItemOutput()) continue;
            IItemHandler neighbour = LegacyItems.legacy(level.getCapability(Capabilities.Item.BLOCK, worldPosition.relative(side), side.getOpposite()));
            if (neighbour == null) continue;
            for (int slot = 0; slot < neighbour.getSlots(); slot++) {
                ItemStack stack = neighbour.getStackInSlot(slot);
                if (ItemStack.isSameItemSameComponents(stack, item)) held += stack.getCount();
            }
        }
        return held;
    }

    /** Its nine slots, whole: what a Logistics Receptacle on it works with, whatever its faces are set to. */
    private final IItemHandler networkView = com.kadikular.quantimium.util.LegacyItems.legacy(
            com.kadikular.quantimium.util.LegacyItems.of(itemsView, items));

    /**
     * Its slots whole, for the logistics network: journaled, so a move inside a transaction (a Logistics
     * Materialiser's) is undone if that transaction is.
     */
    public IItemHandler slots() {
        return networkView;
    }

    /** On a logistics network, what it's caught is offered: a passive provider. */
    private final com.kadikular.quantimium.laser.LogisticsMember asProvider = new com.kadikular.quantimium.laser.LogisticsMember() {
        @Override
        public Role role() {
            return Role.PROVIDER;
        }

        @Override
        public boolean active() {
            return false;
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public boolean lets(ItemStack stack) {
            return true;
        }

        @Override
        public IItemHandler inventory() {
            return networkView;
        }

        @Override
        public BlockPos inventoryPos() {
            return worldPosition;
        }
    };

    @Override
    @Nullable
    public com.kadikular.quantimium.laser.LogisticsMember logistics() {
        return collects() ? asProvider : null;
    }

    // ---- the network ----

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    @Override
    public Set<Direction> outputs() {
        return Set.of(facing());
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
    public void showBeams(List<LaserBeam> beams) {
        boolean same = LaserBeamView.sameLook(shownBeams, beams);
        shownBeams = beams;
        if (!same && level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public List<LaserBeam> syncedBeams() {
        return shownBeams;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            showMode();
            LaserNetworks.add(level, this);
        }
    }

    /** The block shows its mode. */
    private void showMode() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        if (state.hasProperty(com.kadikular.quantimium.block.LaserPortBlock.MODE) && state.getValue(com.kadikular.quantimium.block.LaserPortBlock.MODE) != mode) {
            level.setBlock(worldPosition, state.setValue(com.kadikular.quantimium.block.LaserPortBlock.MODE, mode), Block.UPDATE_CLIENTS);
        }
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
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        for (int slot = 0; slot < SLOTS; slot++) {
            if (level != null) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), items.getStackInSlot(slot));
            items.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        items.serialize(out.child("Items"));
        out.store("SideConfigs", CompoundTag.CODEC, sidesTag());
        out.putInt("RelayMode", mode.ordinal());
        List<ItemStack> entries = new ArrayList<>();
        for (int i = 0; i < FILTER_SLOTS; i++) entries.add(filter.getItem(i));
        out.store("Filter", ItemStack.OPTIONAL_CODEC.listOf(), entries);
        out.putBoolean("Allow", allow);
    }

    private CompoundTag sidesTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("List", SideConfig.saveAll(sideConfigs));
        return tag;
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("Items").ifPresent(items::deserialize);
        mode = Mode.values()[Math.floorMod(in.getIntOr("RelayMode", Mode.COLLECT.ordinal()), Mode.values().length)];
        List<ItemStack> entries = in.read("Filter", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < FILTER_SLOTS; i++) filter.getItems().set(i, i < entries.size() ? entries.get(i) : ItemStack.EMPTY);
        allow = in.getBooleanOr("Allow", true);
        in.read("SideConfigs", CompoundTag.CODEC).ifPresent(tag -> sideConfigs = PROFILE.sanitize(SideConfig.loadAll(tag.getListOrEmpty("List"))));
        in.read("Beams", CompoundTag.CODEC).ifPresent(tag -> shownBeams = LaserBeamView.load(worldPosition, tag));
    }

    /** Sends what the screen shows (the mode, the filter's allow or deny) to the clients watching, at once. */
    private void sync() {
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.put("Beams", LaserBeamView.save(shownBeams));
        return tag;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.laser_port");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new com.kadikular.quantimium.menu.LaserPortMenu(containerId, inventory, this);
    }

    /** What it's set to, and how full it is. */
    @Override
    @Nullable
    public MutableComponent fluxMeterLine() {
        int stacks = 0;
        for (int slot = 0; slot < SLOTS; slot++) if (!items.getStackInSlot(slot).isEmpty()) stacks++;
        return Component.translatable("message.quantimium.laser_port.holds", Component.translatable("message.quantimium.laser_port.mode." + mode.name().toLowerCase(java.util.Locale.ROOT)),
                stacks, SLOTS).withStyle(ChatFormatting.DARK_AQUA);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

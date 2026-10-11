package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.LaserMirrorBlock;
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
 * A laser block with nine slots, which light passes through and out of its face as through a mirror: the
 * Collector and the Sender, each set to catch what comes back up the beam, send what it holds down it, or
 * both (a Collector of seeds in both feeds them straight back down to be sown). Its faces are set as a
 * machine's are: which take items in or hand them out, and which pull from or push to what's beside them,
 * once a second.
 */
public abstract class ItemRelayBlockEntity extends BlockEntity implements LaserNode, FluxMeterReadout, LaserBeamView, MenuProvider, SideConfigurable {

    public static final int SLOTS = 9;
    private static final int AUTO_TRANSFER_INTERVAL = 20;
    private static final SideAutomationProfile PROFILE = new SideAutomationProfile(SideAutomationProfile.ALL_SIDES, 0);

    protected final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return accepts(slot, stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    /** What it does with items: catches what comes back up the beam, sends what it holds down it, or both. */
    public enum Mode {
        COLLECT, SEND, BOTH;

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private Mode mode = startingMode();
    private final IItemHandler[] sided = new IItemHandler[6];
    private List<SideConfig> sideConfigs;
    private List<LaserBeam> shownBeams = List.of();

    protected ItemRelayBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        sideConfigs = PROFILE.sanitize(SideConfig.defaults().stream()
                .map(config -> new SideConfig(config.side(), defaultMode(), SideConfig.ALL_ITEM_INPUTS, SideConfig.ALL_ITEM_OUTPUTS,
                        false, false, SideMode.DISABLED, 0, 0, false, false))
                .toList());
        for (Direction side : Direction.values()) sided[side.get3DDataValue()] = new Sided(side);
    }

    /** How every face starts: a Collector's hand things out, a Sender's take things in. */
    protected abstract SideMode defaultMode();

    /** What it does with items when placed: a Collector collects, a Sender sends. */
    protected abstract Mode startingMode();

    public Mode mode() {
        return mode;
    }

    /** Steps to the next of collect, send, both. */
    public void cycleMode() {
        mode = mode.next();
        setChanged();
        invalidateCapabilities();
        if (level != null) LaserNetworks.changed(level);
    }

    /** Whether it catches {@code stack} when it comes back up the beam: a Collector's filter says. */
    protected boolean acceptsItem(ItemStack stack) {
        return true;
    }

    @Override
    public boolean collects() {
        return mode != Mode.SEND;
    }

    @Override
    public ItemStack collect(ItemStack stack, boolean simulate) {
        // What it turns away goes on up the beam, to the next.
        if (!collects() || !acceptsItem(stack)) return stack;
        return insert(stack, simulate);
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
    protected boolean takesFromOutside() {
        return sends();
    }

    /** Whether {@code stack} may sit in {@code slot}. */
    protected boolean accepts(int slot, ItemStack stack) {
        return true;
    }


    public ItemStackHandler getItems() {
        return items;
    }

    public Direction facing() {
        return getBlockState().getValue(LaserMirrorBlock.FACING);
    }

    // ---- automation ----

    public static void serverTick(Level level, BlockPos pos, BlockState state, ItemRelayBlockEntity relay) {
        if (level.getGameTime() % AUTO_TRANSFER_INTERVAL != Math.floorMod(pos.hashCode(), AUTO_TRANSFER_INTERVAL)) return;
        for (Direction side : Direction.values()) {
            SideConfig config = relay.sideConfigs.get(side.get3DDataValue());
            if (!config.autoItemInput() && !config.autoItemOutput()) continue;
            IItemHandler neighbour = LegacyItems.legacy(level.getCapability(Capabilities.Item.BLOCK, pos.relative(side), side.getOpposite()));
            if (neighbour == null) continue;
            if (config.autoItemInput() && config.itemMode().allowsInput() && relay.takesFromOutside()) relay.pull(neighbour);
            if (config.autoItemOutput() && config.itemMode().allowsOutput()) relay.push(neighbour);
        }
    }

    private void pull(IItemHandler from) {
        for (int source = 0; source < from.getSlots(); source++) {
            ItemStack offered = from.extractItem(source, 64, true);
            if (offered.isEmpty()) continue;
            int accepted = offered.getCount() - insert(offered, true).getCount();
            if (accepted <= 0) continue;
            insert(from.extractItem(source, accepted, false), false);
            return;
        }
    }

    /** Puts {@code stack} into its slots, as far as they take it; what's left. */
    protected ItemStack insert(ItemStack stack, boolean simulate) {
        ItemStack left = stack;
        for (int slot = 0; slot < SLOTS && !left.isEmpty(); slot++) left = items.insertItem(slot, left, simulate);
        return left;
    }

    private void push(IItemHandler into) {
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            int moved = stack.getCount() - ItemHandlerHelper.insertItem(into, stack.copy(), false).getCount();
            if (moved > 0) {
                items.extractItem(slot, moved, false);
                return;
            }
        }
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
        mode = Mode.values()[Math.floorMod(in.getIntOr("RelayMode", startingMode().ordinal()), Mode.values().length)];
        in.read("SideConfigs", CompoundTag.CODEC).ifPresent(tag -> sideConfigs = PROFILE.sanitize(SideConfig.loadAll(tag.getListOrEmpty("List"))));
        in.read("Beams", CompoundTag.CODEC).ifPresent(tag -> shownBeams = LaserBeamView.load(worldPosition, tag));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.put("Beams", LaserBeamView.save(shownBeams));
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

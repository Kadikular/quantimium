package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.CollectorBlock;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.laser.LaserBeam;
import com.kadikular.quantimium.laser.LaserBeamView;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.LaserNode;
import com.kadikular.quantimium.laser.LaserTuning;
import com.kadikular.quantimium.util.ItemStackHandler;
import com.kadikular.quantimium.util.RestrictedItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * A Collector: light passes through it, out of its face, as through a mirror; and what a Pickup lens
 * further along brings back up the beam stops here, in its nine slots, for pipes and hoppers to take.
 * Nothing can be put in from outside.
 */
public class CollectorBlockEntity extends BlockEntity implements LaserNode, FluxMeterReadout, LaserBeamView, net.minecraft.world.MenuProvider {

    public static final int SLOTS = 9;
    public static final int FILTER_SLOTS = 9;

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final ResourceHandler<ItemResource> outside = RestrictedItems.takeOnly(items);
    /** Ghost entries, never real items: what it catches, or with a blacklist, what it lets go on up the beam. */
    private final net.minecraft.world.SimpleContainer filter = new net.minecraft.world.SimpleContainer(FILTER_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            CollectorBlockEntity.this.setChanged();
        }
    };
    /** Whitelist (catch only these) or blacklist (catch all but these). An empty filter catches everything. */
    private boolean allow = true;
    private List<LaserBeam> shownBeams = List.of();

    public CollectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COLLECTOR_BE.get(), pos, state);
    }

    public ItemStackHandler getItems() {
        return items;
    }

    /** What pipes and hoppers see: take only. */
    public ResourceHandler<ItemResource> getOutside() {
        return outside;
    }

    public Direction facing() {
        return getBlockState().getValue(CollectorBlock.FACING);
    }

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
    public boolean collects() {
        return true;
    }

    @Override
    public ItemStack collect(ItemStack stack, boolean simulate) {
        // What its filter turns away goes on up the beam, to the next Collector.
        if (!accepts(stack)) return stack;
        ItemStack left = stack;
        for (int slot = 0; slot < SLOTS && !left.isEmpty(); slot++) left = items.insertItem(slot, left, simulate);
        return left;
    }

    /** Whether its filter lets it catch {@code stack}. */
    public boolean accepts(ItemStack stack) {
        boolean listed = false;
        boolean any = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack entry = filter.getItem(i);
            if (entry.isEmpty()) continue;
            any = true;
            if (com.kadikular.quantimium.recipe.FilterEntry.matches(entry, stack)) listed = true;
        }
        return !any || listed == allow;
    }

    public net.minecraft.world.SimpleContainer getFilter() {
        return filter;
    }

    public boolean allows() {
        return allow;
    }

    public void toggleMode() {
        allow = !allow;
        setChanged();
    }

    public void setFilterSlot(int slot, ItemStack stack) {
        if (slot >= 0 && slot < FILTER_SLOTS) filter.setItem(slot, com.kadikular.quantimium.recipe.FilterEntry.of(stack));
    }

    public void cycleFilterTag(int slot) {
        if (slot < 0 || slot >= FILTER_SLOTS) return;
        ItemStack entry = filter.getItem(slot);
        if (!entry.isEmpty()) filter.setItem(slot, com.kadikular.quantimium.recipe.FilterEntry.cycle(entry));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.collector");
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory,
                                                                         net.minecraft.world.entity.player.Player player) {
        return new com.kadikular.quantimium.menu.CollectorMenu(containerId, inventory, this);
    }

    @Override
    public void showBeams(List<LaserBeam> beams) {
        boolean same = LaserBeamView.sameLook(shownBeams, beams);
        shownBeams = beams;
        if (!same && level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
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
        List<ItemStack> entries = new java.util.ArrayList<>();
        for (int i = 0; i < FILTER_SLOTS; i++) entries.add(filter.getItem(i));
        out.store("Filter", ItemStack.OPTIONAL_CODEC.listOf(), entries);
        out.putBoolean("Allow", allow);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("Items").ifPresent(items::deserialize);
        List<ItemStack> entries = in.read("Filter", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < FILTER_SLOTS; i++) filter.getItems().set(i, i < entries.size() ? entries.get(i) : ItemStack.EMPTY);
        allow = in.getBooleanOr("Allow", true);
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

    /** How full it is. */
    @Override
    @Nullable
    public MutableComponent fluxMeterLine() {
        int stacks = 0;
        for (int slot = 0; slot < SLOTS; slot++) if (!items.getStackInSlot(slot).isEmpty()) stacks++;
        return Component.translatable("message.quantimium.collector.holds", stacks, SLOTS).withStyle(ChatFormatting.DARK_AQUA);
    }
}

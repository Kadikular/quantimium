package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.LensMountBlock;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.item.LensItem;
import com.kadikular.quantimium.laser.LaserBeam;
import com.kadikular.quantimium.laser.LaserBeamView;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.LaserNode;
import com.kadikular.quantimium.laser.LaserTuning;
import com.kadikular.quantimium.laser.Lens;
import com.kadikular.quantimium.menu.LensMountMenu;
import com.kadikular.quantimium.util.ItemStackHandler;
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
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A Lens Mount: a mirror that holds lenses. Every beam that reaches it goes on out of its face, refocused,
 * carrying its lenses in slot order, after any lenses the light met before. The beam is still a beam:
 * it carries power to what it ends on as before, and does what its lenses say along the way.
 */
public class LensMountBlockEntity extends BlockEntity implements LaserNode, MenuProvider, FluxMeterReadout, LaserBeamView {

    public static final int SLOTS = 4;

    private final ItemStackHandler lensSlots = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof LensItem;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide()) LaserNetworks.changed(level);
        }
    };
    private List<LaserBeam> shownBeams = List.of();

    public LensMountBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LENS_MOUNT_BE.get(), pos, state);
    }

    public ItemStackHandler getLensSlots() {
        return lensSlots;
    }

    /** A lens's settings changed where it sits: its light changes, and clients see it. */
    public void lensChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            LaserNetworks.changed(level);
            sync();
        }
    }

    public Direction facing() {
        return getBlockState().getValue(LensMountBlock.FACING);
    }

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    @Override
    public Set<Direction> outputs() {
        return Set.of(facing());
    }

    /** Light goes straight through it: in by the side behind its output, travelling the way it shines. */
    @Override
    public boolean accepts(Direction travel) {
        return travel == facing();
    }

    /** Out of its output, if light comes in straight behind it (or none does: where light would go). */
    @Override
    public Set<Direction> outputsFor(Set<Direction> arriving) {
        return arriving.isEmpty() || arriving.contains(facing()) ? Set.of(facing()) : Set.of();
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
    public List<com.kadikular.quantimium.laser.LensUse> lenses() {
        List<com.kadikular.quantimium.laser.LensUse> lenses = new ArrayList<>(SLOTS);
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = lensSlots.getStackInSlot(slot);
            if (stack.getItem() instanceof LensItem lens) {
                lenses.add(new com.kadikular.quantimium.laser.LensUse(lens.lens(), stack.get(com.kadikular.quantimium.init.ModDataComponents.LENS_FILTER.get()),
                        Boolean.TRUE.equals(stack.get(com.kadikular.quantimium.init.ModDataComponents.INTERACT_HITS.get()))));
            }
        }
        return lenses;
    }

    @Override
    public void showBeams(List<LaserBeam> beams) {
        boolean same = LaserBeamView.sameLook(shownBeams, beams);
        shownBeams = beams;
        if (!same) sync();
    }

    @Override
    public List<LaserBeam> syncedBeams() {
        return shownBeams;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.lens_mount");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new LensMountMenu(containerId, inventory, this);
    }

    // ---- life ----

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
            if (level != null) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), lensSlots.getStackInSlot(slot));
            lensSlots.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    private void sync() {
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        lensSlots.serialize(out.child("Lenses"));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("Lenses").ifPresent(lensSlots::deserialize);
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

    /** Its lenses, and what passes. */
    @Override
    @Nullable
    public MutableComponent fluxMeterLine() {
        List<Lens> lenses = lenses().stream().map(com.kadikular.quantimium.laser.LensUse::lens).toList();
        if (lenses.isEmpty()) return Component.translatable("message.quantimium.lens_mount.empty").withStyle(ChatFormatting.GRAY);
        MutableComponent names = Component.empty();
        for (int i = 0; i < lenses.size(); i++) {
            if (i > 0) names.append(", ");
            names.append(Component.translatable(lenses.get(i).itemKey()));
        }
        return Component.translatable("message.quantimium.lens_mount.holds", names).withStyle(ChatFormatting.DARK_AQUA);
    }
}

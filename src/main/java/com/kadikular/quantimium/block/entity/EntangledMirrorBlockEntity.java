package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.EntangledMirrorBlock;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.laser.LaserBeam;
import com.kadikular.quantimium.laser.LaserBeamView;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.LaserNode;
import com.kadikular.quantimium.laser.LaserTuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * An Entangled Mirror's light. With a Tesseract bound to a laser block (a Relay, say) floating in its window, light
 * reaching that block reaches this one too, at once, however far (in the same dimension, while both are loaded), and
 * leaves by its muzzle, refocused, less the jump's tax. Light from anywhere else stops on it. Off while a redstone
 * signal reaches it.
 */
public class EntangledMirrorBlockEntity extends BlockEntity implements LaserNode, LaserBeamView, com.kadikular.quantimium.flux.FluxMeterReadout {

    private ItemStack tesseract = ItemStack.EMPTY;
    private List<LaserBeam> shownBeams = List.of();
    /** Whether light it draws is leaving it, as last worked out: the Tesseract glows. */
    private boolean jumping;

    public EntangledMirrorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENTANGLED_MIRROR_BE.get(), pos, state);
    }

    public Direction facing() {
        return getBlockState().getValue(EntangledMirrorBlock.FACING);
    }

    // ---- the Tesseract ----

    /** Whether {@code stack} can go in: a bound Tesseract. */
    public static boolean fits(ItemStack stack) {
        return stack.is(ModItems.TESSERACT.get()) && stack.get(ModDataComponents.BOUND_POS.get()) != null;
    }

    public ItemStack tesseract() {
        return tesseract;
    }

    /** Puts one of {@code stack} in; whatever was there comes out. */
    public ItemStack insert(ItemStack stack) {
        ItemStack out = tesseract;
        tesseract = stack.copyWithCount(1);
        changed();
        return out;
    }

    public ItemStack remove() {
        ItemStack out = tesseract;
        tesseract = ItemStack.EMPTY;
        changed();
        return out;
    }

    /** Where its Tesseract points, in this dimension; null with none, or one bound elsewhere. */
    @Nullable
    @Override
    public BlockPos entangledSource() {
        if (tesseract.isEmpty() || level == null) return null;
        GlobalPos bound = tesseract.get(ModDataComponents.BOUND_POS.get());
        if (bound == null || !bound.dimension().equals(level.dimension()) || bound.pos().equals(worldPosition)) return null;
        return bound.pos();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            LaserNetworks.relinked(level, this);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !tesseract.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), tesseract);
        tesseract = ItemStack.EMPTY;
    }

    // ---- light ----

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    @Override
    public Direction entangledFace() {
        return facing();
    }

    /** The faces light leaves by, as the network last found it arriving. */
    private Set<Direction> passing = Set.of();

    @Override
    public Set<Direction> outputs() {
        return passing;
    }

    /** What it draws leaves by its muzzle. */
    @Override
    public Set<Direction> outputsFor(Set<Direction> arriving) {
        Set<Direction> out = arriving.isEmpty() ? EnumSet.noneOf(Direction.class) : EnumSet.of(facing());
        passing = out;
        return out;
    }

    /** Only light it draws through its Tesseract gets in: the rest stops on its housing. */
    @Override
    public boolean accepts(Direction travel) {
        return false;
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
        return LaserTuning.JUMP_TAX;
    }

    @Override
    public boolean enabled() {
        return !getBlockState().getValue(EntangledMirrorBlock.POWERED);
    }

    /** Its beam out of the muzzle; the Tesseract glows while light runs along it. */
    @Override
    public void showBeams(List<LaserBeam> beams) {
        boolean crossing = false;
        for (LaserBeam beam : beams) crossing |= beam.live();
        boolean same = LaserBeamView.sameLook(shownBeams, beams) && crossing == jumping;
        shownBeams = beams;
        jumping = crossing;
        if (level == null || level.isClientSide()) return;
        if (getBlockState().getValue(EntangledMirrorBlock.ACTIVE) != crossing) {
            level.setBlock(worldPosition, getBlockState().setValue(EntangledMirrorBlock.ACTIVE, crossing), Block.UPDATE_CLIENTS);
        } else if (!same) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Its light starts at the Tesseract, a pixel behind the housing's middle, and leaves by the muzzle. */
    @Override
    public net.minecraft.world.phys.Vec3[] endsOf(LaserBeam beam) {
        net.minecraft.world.phys.Vec3[] ends = beam.ends();
        net.minecraft.world.phys.Vec3 back = net.minecraft.world.phys.Vec3.atLowerCornerOf(facing().getUnitVec3i()).scale(-1.0 / 16.0);
        return new net.minecraft.world.phys.Vec3[] {worldPosition.getCenter().add(back), ends[1]};
    }

    @Override
    public List<LaserBeam> syncedBeams() {
        return shownBeams;
    }

    /** Whether light it draws is leaving it. */
    public boolean jumping() {
        return jumping;
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
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (!tesseract.isEmpty()) out.store("Tesseract", ItemStack.OPTIONAL_CODEC, tesseract);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        tesseract = in.read("Tesseract", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        in.read("Beams", CompoundTag.CODEC).ifPresent(tag -> shownBeams = LaserBeamView.load(worldPosition, tag));
        jumping = in.getBooleanOr("Jumping", false);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.put("Beams", LaserBeamView.save(shownBeams));
        tag.putBoolean("Jumping", jumping);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Where its Tesseract points, or that it has none; what's passing. */
    @Override
    public net.minecraft.network.chat.MutableComponent fluxMeterLine() {
        long passing = level instanceof net.minecraft.server.level.ServerLevel server ? LaserNetworks.of(server).throughput(worldPosition) : 0;
        BlockPos source = entangledSource();
        net.minecraft.network.chat.MutableComponent line = source != null
                ? net.minecraft.network.chat.Component.translatable("message.quantimium.entangled_mirror.draws", source.getX(), source.getY(), source.getZ(), passing)
                : !tesseract.isEmpty() ? net.minecraft.network.chat.Component.translatable("message.quantimium.entangled_mirror.elsewhere")
                : net.minecraft.network.chat.Component.translatable("message.quantimium.entangled_mirror.empty");
        return line.withStyle(enabled() ? net.minecraft.ChatFormatting.AQUA : net.minecraft.ChatFormatting.GRAY);
    }
}

package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.EntangledMirrorBlock;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModBlocks;
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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * An Entangled Mirror's light. With a Tesseract bound to another Entangled Mirror floating in its core, light into its
 * face reaches that one at once, however far (in the same dimension, while both are loaded), and leaves by that one's
 * face, refocused. Light reaching it from a partner leaves by its own face; a mirror with no Tesseract only receives.
 * A Tesseract in each makes the pair work both ways. Off while a redstone signal reaches it.
 */
public class EntangledMirrorBlockEntity extends BlockEntity implements LaserNode, LaserBeamView, com.kadikular.quantimium.flux.FluxMeterReadout {

    private ItemStack tesseract = ItemStack.EMPTY;
    private List<LaserBeam> shownBeams = List.of();
    /** Whether light is crossing to its partner, as last worked out: the Tesseract glows. */
    private boolean jumping;

    public EntangledMirrorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENTANGLED_MIRROR_BE.get(), pos, state);
    }

    public Direction facing() {
        return getBlockState().getValue(EntangledMirrorBlock.FACING);
    }

    // ---- the Tesseract ----

    /** Whether {@code stack} can go in: a Tesseract bound to an Entangled Mirror. */
    public static boolean fits(ItemStack stack) {
        if (!stack.is(ModItems.TESSERACT.get())) return false;
        var block = stack.get(ModDataComponents.BOUND_BLOCK.get());
        return stack.get(ModDataComponents.BOUND_POS.get()) != null && block != null
                && block.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(ModBlocks.ENTANGLED_MIRROR.get()));
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
    public BlockPos partner() {
        if (tesseract.isEmpty() || level == null) return null;
        GlobalPos bound = tesseract.get(ModDataComponents.BOUND_POS.get());
        if (bound == null || !bound.dimension().equals(level.dimension()) || bound.pos().equals(worldPosition)) return null;
        return bound.pos();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            LaserNetworks.changed(level, worldPosition);
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
    public List<BlockPos> targets() {
        BlockPos partner = partner();
        return partner == null ? List.of() : List.of(partner);
    }

    @Override
    public boolean entangles() {
        return true;
    }

    /** It never lets go of its partner: the Tesseract stays bound, and light crosses again once that one's back. */
    @Override
    public boolean keepsLinks() {
        return true;
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

    /** Light from its partner (arriving as if from behind it) leaves by its face; light into its face goes to its partner. */
    @Override
    public Set<Direction> outputsFor(Set<Direction> arriving) {
        Set<Direction> out = arriving.contains(facing()) ? EnumSet.of(facing()) : EnumSet.noneOf(Direction.class);
        passing = out;
        return out;
    }

    /** Light into its face, and light from a partner; the rest stops on its frame. */
    @Override
    public boolean accepts(Direction travel) {
        return travel == facing().getOpposite() || travel == facing();
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
        return partner() == null ? LaserTuning.HOP_TAX : LaserTuning.JUMP_TAX;
    }

    @Override
    public boolean enabled() {
        return !getBlockState().getValue(EntangledMirrorBlock.POWERED);
    }

    @Override
    public void showBeams(List<LaserBeam> beams) {
        // Entangled light crosses nothing, so it isn't drawn; the Tesseract glows while it's crossing.
        List<LaserBeam> drawn = new ArrayList<>();
        boolean crossing = false;
        for (LaserBeam beam : beams) {
            if (beam.jumps()) crossing |= beam.live();
            else drawn.add(beam);
        }
        boolean same = LaserBeamView.sameLook(shownBeams, drawn) && crossing == jumping;
        shownBeams = drawn;
        jumping = crossing;
        if (!same && level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public List<LaserBeam> syncedBeams() {
        return shownBeams;
    }

    /** Whether light is crossing from it to its partner. */
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

    /** Where its Tesseract points, or that it only receives; what's passing. */
    @Override
    public net.minecraft.network.chat.MutableComponent fluxMeterLine() {
        long passing = level instanceof net.minecraft.server.level.ServerLevel server ? LaserNetworks.of(server).throughput(worldPosition) : 0;
        BlockPos partner = partner();
        net.minecraft.network.chat.MutableComponent line = partner != null
                ? net.minecraft.network.chat.Component.translatable("message.quantimium.entangled_mirror.sends", partner.getX(), partner.getY(), partner.getZ(), passing)
                : !tesseract.isEmpty() ? net.minecraft.network.chat.Component.translatable("message.quantimium.entangled_mirror.elsewhere")
                : net.minecraft.network.chat.Component.translatable("message.quantimium.entangled_mirror.receives", passing);
        return line.withStyle(enabled() ? net.minecraft.ChatFormatting.AQUA : net.minecraft.ChatFormatting.GRAY);
    }
}

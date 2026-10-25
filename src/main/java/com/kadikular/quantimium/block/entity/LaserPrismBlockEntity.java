package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.laser.LaserBeam;
import com.kadikular.quantimium.laser.LaserBeamView;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.LaserNode;
import com.kadikular.quantimium.laser.LaserTuning;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
 * A laser prism: whatever light reaches it, from any side and any number of beams, leaves along a
 * straight line to each block it's linked to, at any angle, sharing it by what each asks for. It joins
 * and splits, and refocuses as a mirror does, at the same tax. Linked with the Laser Linker.
 */
public class LaserPrismBlockEntity extends BlockEntity implements LaserNode, FluxMeterReadout, LaserBeamView {

    private final List<BlockPos> targets = new ArrayList<>();
    /** The face of each linked block it was linked by: its light leaves the way nearest that face's middle. */
    private final java.util.Map<BlockPos, Direction> faces = new java.util.HashMap<>();
    /** Whether it keeps its aim on a linked spot after the block there is gone: for a Harvest lens's light, which breaks it again when something comes back. */
    private boolean keepsLinks;
    private List<LaserBeam> shownBeams = List.of();

    public LaserPrismBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_PRISM_BE.get(), pos, state);
    }

    public enum Link { ADDED, REMOVED, FULL, TOO_FAR, SELF }

    /** Links {@code target}, or unlinks it if it was linked: by the face facing the prism. */
    public Link toggleLink(BlockPos target) {
        return toggleLink(target, facing(target));
    }

    /**
     * Links {@code target} by {@code face}, or unlinks it if it was linked. Its light goes straight to the middle of that
     * face, at whatever angle, and in by it.
     */
    public Link toggleLink(BlockPos target, Direction face) {
        if (target.equals(worldPosition)) return Link.SELF;
        if (targets.remove(target)) {
            faces.remove(target);
            changed();
            return Link.REMOVED;
        }
        if (targets.size() >= LaserTuning.PRISM_LINKS) return Link.FULL;
        if (target.getCenter().distanceTo(worldPosition.getCenter()) > LaserTuning.PRISM_RANGE) return Link.TOO_FAR;
        targets.add(target.immutable());
        faces.put(target.immutable(), face);
        changed();
        return Link.ADDED;
    }

    /** Lets go of every link. How many there were. */
    public int clearLinks() {
        int had = targets.size();
        if (had > 0) {
            targets.clear();
            changed();
        }
        return had;
    }

    public boolean keepsLinksToGone() {
        return keepsLinks;
    }

    /** Switches between letting go of links to blocks that are gone, and keeping them. */
    public void toggleKeepsLinks() {
        keepsLinks = !keepsLinks;
        changed();
    }

    @Override
    public boolean keepsLinks() {
        return keepsLinks;
    }

    @Override
    public void forget(BlockPos target) {
        faces.remove(target);
        if (targets.remove(target)) changed();
    }

    @Override
    public Direction linkFace(BlockPos target) {
        Direction face = faces.get(target);
        return face != null ? face : facing(target);
    }

    /** The face of {@code target} that faces the prism most: what a link made before faces were picked stands for. */
    private Direction facing(BlockPos target) {
        Vec3 back = worldPosition.getCenter().subtract(target.getCenter());
        return Direction.getApproximateNearest(back.x, back.y, back.z);
    }

    private void changed() {
        setChanged();
        if (level != null) {
            LaserNetworks.changed(level);
            if (!level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    @Override
    public Set<Direction> outputs() {
        return Set.of();
    }

    @Override
    public List<BlockPos> targets() {
        return targets;
    }

    @Override
    public int range() {
        return LaserTuning.PRISM_RANGE;
    }

    @Override
    public boolean relays() {
        return true;
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
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("Targets", BlockPos.CODEC.listOf(), List.copyOf(targets));
        List<Integer> linkedBy = new ArrayList<>();
        for (BlockPos target : targets) linkedBy.add(faces.getOrDefault(target, facing(target)).get3DDataValue());
        out.store("TargetFaces", com.mojang.serialization.Codec.INT.listOf(), linkedBy);
        out.putBoolean("KeepsLinks", keepsLinks);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        targets.clear();
        in.read("Targets", BlockPos.CODEC.listOf()).ifPresent(targets::addAll);
        faces.clear();
        List<Integer> linkedBy = in.read("TargetFaces", com.mojang.serialization.Codec.INT.listOf()).orElse(List.of());
        for (int i = 0; i < targets.size(); i++) {
            if (i < linkedBy.size()) faces.put(targets.get(i), Direction.from3DDataValue(linkedBy.get(i)));
        }
        keepsLinks = in.getBooleanOr("KeepsLinks", false);
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

    /** How many it's linked to, and what's passing through. */
    @Override
    @Nullable
    public MutableComponent fluxMeterLine() {
        long passing = 0;
        for (LaserBeam beam : shownBeams) passing += beam.flow();
        return Component.translatable("message.quantimium.laser.prism", targets.size(), LaserTuning.PRISM_LINKS,
                String.format(java.util.Locale.ROOT, "%,d", passing)).withStyle(passing > 0 ? ChatFormatting.DARK_AQUA : ChatFormatting.GRAY)
                .append(Component.translatable(keepsLinks ? "message.quantimium.laser_prism.keeps" : "message.quantimium.laser_prism.forgets")
                        .withStyle(ChatFormatting.GRAY));
    }
}

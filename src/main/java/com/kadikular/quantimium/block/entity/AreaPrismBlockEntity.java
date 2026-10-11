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
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * An Area Prism: light that reaches it spreads its actions over an area, set with the Laser Linker in
 * area mode: Harvest cuts each block there in turn, Damage hurts and Pickup gathers what's in it,
 * Placement fills each free space resting on something; only what the light reaching it reaches, and can
 * see from it. The light goes no further, and work in an area costs {@link LaserTuning#AREA_COST} times as
 * much. A Filter lens says what counts.
 */
public class AreaPrismBlockEntity extends BlockEntity implements LaserNode, FluxMeterReadout, LaserBeamView, net.minecraft.world.MenuProvider {

    /** What its area is: the one picked with the Linker, or a cube or sphere of a radius round it. */
    public enum Shape {
        PICKED, CUBE, SPHERE;

        public Shape next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public static final int MAX_RADIUS = LaserTuning.MAX_RANGE;
    private Shape shape = Shape.PICKED;
    private int radius = 4;

    public enum Placed { SET, TOO_BIG }

    @Nullable
    private BoundingBox area;
    /** Beams to what its actions are working on, as the network last showed them. */
    private List<LaserBeam> shownBeams = List.of();
    /** Exactly where each shown beam ends: the middle of what it's hitting, which moves. */
    private List<net.minecraft.world.phys.Vec3> points = List.of();

    public AreaPrismBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AREA_PRISM_BE.get(), pos, state);
    }

    /** Gives it the area between two corners, if it's small enough and near enough. */
    public Placed setArea(BlockPos a, BlockPos b) {
        BoundingBox box = BoundingBox.fromCorners(a, b);
        if (box.getXSpan() > LaserTuning.AREA_MAX || box.getYSpan() > LaserTuning.AREA_MAX || box.getZSpan() > LaserTuning.AREA_MAX) {
            return Placed.TOO_BIG;
        }
        area = box;
        changed();
        return Placed.SET;
    }

    public void clearArea() {
        area = null;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null) {
            LaserNetworks.changed(level);
            if (!level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    @Nullable
    public BoundingBox area() {
        if (shape == Shape.PICKED) return area;
        return new BoundingBox(worldPosition.getX() - radius, worldPosition.getY() - radius, worldPosition.getZ() - radius,
                worldPosition.getX() + radius, worldPosition.getY() + radius, worldPosition.getZ() + radius);
    }

    @Override
    public boolean inArea(BlockPos pos) {
        return shape != Shape.SPHERE || pos.distSqr(worldPosition) <= (radius + 0.5) * (radius + 0.5);
    }

    public Shape shape() {
        return shape;
    }

    public int radius() {
        return radius;
    }

    public void cycleShape() {
        shape = shape.next();
        changed();
    }

    public void setRadius(int radius) {
        this.radius = Math.clamp(radius, 1, MAX_RADIUS);
        changed();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.area_prism");
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory,
                                                                         net.minecraft.world.entity.player.Player player) {
        return new com.kadikular.quantimium.menu.AreaPrismMenu(containerId, inventory, this);
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
    public void showWork(List<LaserBeam> beams, List<net.minecraft.world.phys.Vec3> ends) {
        boolean moved = !ends.equals(points);
        points = List.copyOf(ends);
        boolean same = LaserBeamView.sameLook(shownBeams, beams);
        shownBeams = beams;
        if ((!same || moved) && level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public net.minecraft.world.phys.Vec3[] endsOf(LaserBeam beam) {
        int i = shownBeams.indexOf(beam);
        net.minecraft.world.phys.Vec3[] ends = beam.ends();
        if (i < 0 || i >= points.size()) return ends;
        net.minecraft.world.phys.Vec3 centre = worldPosition.getCenter();
        net.minecraft.world.phys.Vec3 to = points.get(i);
        return new net.minecraft.world.phys.Vec3[] {centre.add(to.subtract(centre).normalize().scale(0.5)), to};
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
        if (area != null) out.store("Area", BoundingBox.CODEC, area);
        out.putInt("Shape", shape.ordinal());
        out.putInt("Radius", radius);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        area = in.read("Area", BoundingBox.CODEC).orElse(null);
        shape = Shape.values()[Math.floorMod(in.getIntOr("Shape", 0), Shape.values().length)];
        radius = Math.clamp(in.getIntOr("Radius", 4), 1, MAX_RADIUS);
        in.read("Beams", CompoundTag.CODEC).ifPresent(tag -> shownBeams = LaserBeamView.load(worldPosition, tag));
        in.read("Points", CompoundTag.CODEC).ifPresent(tag -> {
            List<net.minecraft.world.phys.Vec3> read = new java.util.ArrayList<>();
            for (var entry : tag.getListOrEmpty("List")) {
                if (entry instanceof CompoundTag point) read.add(new net.minecraft.world.phys.Vec3(point.getDoubleOr("X", 0), point.getDoubleOr("Y", 0), point.getDoubleOr("Z", 0)));
            }
            points = read;
        });
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.put("Beams", LaserBeamView.save(shownBeams));
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (net.minecraft.world.phys.Vec3 point : points) {
            CompoundTag entry = new CompoundTag();
            entry.putDouble("X", point.x);
            entry.putDouble("Y", point.y);
            entry.putDouble("Z", point.z);
            list.add(entry);
        }
        CompoundTag wrapped = new CompoundTag();
        wrapped.put("List", list);
        tag.put("Points", wrapped);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    @Nullable
    public MutableComponent fluxMeterLine() {
        if (shape != Shape.PICKED) {
            return Component.translatable("message.quantimium.area_prism." + shape.name().toLowerCase(java.util.Locale.ROOT), radius)
                    .withStyle(ChatFormatting.DARK_AQUA);
        }
        if (area == null) return Component.translatable("message.quantimium.area_prism.none").withStyle(ChatFormatting.GRAY);
        return Component.translatable("message.quantimium.area_prism.area", area.getXSpan(), area.getYSpan(), area.getZSpan())
                .withStyle(ChatFormatting.DARK_AQUA);
    }
}

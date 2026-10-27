package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.CavityMirrorBlock;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.EnumSet;
import java.util.Set;

/**
 * A Cavity Mirror block from before it became an attachment of a Laser Focus: it holds what it held only until it
 * becomes a Laser Focus with a Stand on its base, a Cavity Mirror plate on its mirror face and an Emitter on each of its
 * outputs, the tick after it loads (see {@link #becomeFocus}).
 */
public class CavityMirrorBlockEntity extends BlockEntity {

    /** What it held, read as it was saved. */
    private final CavityPlate held;
    /** The faces what it held left by: its back, unless set otherwise. */
    private final Set<Direction> outputs = EnumSet.noneOf(Direction.class);
    private boolean outputsKnown;

    public CavityMirrorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAVITY_MIRROR_BE.get(), pos, state);
        held = new CavityPlate(null, facing());
    }

    public Direction facing() {
        return getBlockState().hasProperty(CavityMirrorBlock.FACING) ? getBlockState().getValue(CavityMirrorBlock.FACING) : Direction.NORTH;
    }

    private Direction mount() {
        BlockState state = getBlockState();
        Direction mount = state.hasProperty(CavityMirrorBlock.MOUNT) ? state.getValue(CavityMirrorBlock.MOUNT) : Direction.DOWN;
        // One from before it stood on a base, its mirror facing down: it stood on the other side.
        return mount == facing() ? mount.getOpposite() : mount;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    /** Becomes a Laser Focus with a Stand, a Cavity Mirror plate and Emitters, holding what it held. */
    public void becomeFocus() {
        Level world = level;
        if (world == null || world.isClientSide() || isRemoved()) return;
        BlockPos at = worldPosition;
        Direction facing = facing();
        Direction mount = mount();
        Set<Direction> out = outputsKnown ? EnumSet.copyOf(outputs.isEmpty() ? EnumSet.noneOf(Direction.class) : outputs)
                : EnumSet.of(CavityMirrorBlock.outputFor(facing, mount));
        world.setBlock(at, ModBlocks.LASER_FOCUS.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(world.getBlockEntity(at) instanceof LaserFocusBlockEntity focus)) return;
        focus.setCore(LaserFocusBlockEntity.Core.LASER);
        focus.setPart(mount, LaserFocusBlockEntity.Part.STAND);
        focus.setPart(facing, LaserFocusBlockEntity.Part.CAVITY);
        for (Direction face : out) if (face != facing && face != mount) focus.addEmitter(face);
        CavityPlate cavity = focus.cavity(facing);
        if (cavity != null) cavity.copyFrom(held);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        held.save(out);
        int mask = 0;
        for (Direction face : outputs) mask |= 1 << face.get3DDataValue();
        if (outputsKnown) out.putInt("Outputs", mask);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        held.load(in);
        int mask = in.getIntOr("Outputs", -1);
        outputs.clear();
        outputsKnown = mask >= 0 || !in.getBooleanOr("Output", true);
        if (mask >= 0) for (Direction face : Direction.values()) if ((mask & 1 << face.get3DDataValue()) != 0) outputs.add(face);
    }
}

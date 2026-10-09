package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.LaserMirrorBlock;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.laser.LaserNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.EnumSet;
import java.util.Set;

/**
 * A Splitter: a mirror with as many faces as you set. Light that reaches it leaves by every output face,
 * shared by what each beam's blocks ask for: a prism's job for the six straight directions, with no
 * linking. Placed, its one output faces you; a wrench, or a sneaking empty hand, on a face adds or takes
 * away an output there.
 */
public class SplitterBlockEntity extends LaserMirrorBlockEntity {

    private final Set<Direction> outputs = EnumSet.noneOf(Direction.class);

    public SplitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPLITTER_BE.get(), pos, state);
        if (state.hasProperty(LaserMirrorBlock.FACING)) outputs.add(state.getValue(LaserMirrorBlock.FACING));
    }

    @Override
    public Set<Direction> outputs() {
        return outputs;
    }

    /** Turns light out of {@code face} on or off; one output always stays. Whether it changed. */
    public boolean toggleOutput(Direction face) {
        if (outputs.contains(face)) {
            if (outputs.size() == 1) return false;
            outputs.remove(face);
        } else {
            outputs.add(face);
        }
        setChanged();
        if (level != null) {
            showOutputs();
            LaserNetworks.changed(level);
            if (!level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return true;
    }

    /** The block shows which faces light leaves by. */
    private void showOutputs() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof com.kadikular.quantimium.block.SplitterBlock)) return;
        BlockState shown = state;
        for (Direction face : Direction.values()) shown = shown.setValue(com.kadikular.quantimium.block.SplitterBlock.OUTPUTS.get(face), outputs.contains(face));
        if (shown != state) level.setBlock(worldPosition, shown, Block.UPDATE_CLIENTS);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        showOutputs();
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        int mask = 0;
        for (Direction face : outputs) mask |= 1 << face.get3DDataValue();
        out.putInt("Outputs", mask);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        int mask = in.getIntOr("Outputs", 0);
        if (mask != 0) {
            outputs.clear();
            for (Direction face : Direction.values()) if ((mask & 1 << face.get3DDataValue()) != 0) outputs.add(face);
        }
    }
}

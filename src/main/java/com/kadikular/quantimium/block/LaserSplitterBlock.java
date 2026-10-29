package com.kadikular.quantimium.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * A Laser Splitter: a Laser Mirror with a half-silvered plate. Light in by any of its four ports (its two
 * sides, and the faces straight across from them) goes both straight on and turned off the plate, so a beam
 * is split in two, and two beams meeting at right angles leave together. It's never flat, and closes no
 * cavity. A port facing its mount isn't there. One-way, its plate's back only lets light through: a laser in from behind goes
 * straight on and isn't turned, so two lasers can be combined without either spilling the other way.
 */
public class LaserSplitterBlock extends LaserMirrorBlock {
    public static final MapCodec<LaserSplitterBlock> CODEC = simpleCodec(LaserSplitterBlock::new);

    /** Whether its plate's back only lets light through (set with a wrench, sneaking). */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty ONE_WAY =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("one_way");

    public LaserSplitterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ONE_WAY, false));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block,
            net.minecraft.world.level.block.state.BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ONE_WAY);
    }

    /** Whether its plate's front only turns light: the back lets it through, untouched. */
    @Override
    public boolean oneWay(net.minecraft.world.level.block.state.BlockState state) {
        return state.getValue(ONE_WAY);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public boolean splits() {
        return true;
    }
}

package com.kadikular.quantimium.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * A Laser Splitter: a Laser Mirror with a half-silvered plate. Light in by any of its four ports (its two
 * sides, and the faces straight across from them) goes both straight on and turned off the plate, so a beam
 * is split in two, and two beams meeting at right angles leave together. It's never flat, and closes no
 * cavity. A port facing its mount isn't there.
 */
public class LaserSplitterBlock extends LaserMirrorBlock {
    public static final MapCodec<LaserSplitterBlock> CODEC = simpleCodec(LaserSplitterBlock::new);

    public LaserSplitterBlock(Properties properties) {
        super(properties);
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

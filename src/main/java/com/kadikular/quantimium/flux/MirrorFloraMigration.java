package com.kadikular.quantimium.flux;

import com.kadikular.quantimium.block.MirrorVineBlock;
import com.kadikular.quantimium.init.ModAttachments;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** One-time conversion of block-backed flora from older worlds into render-only chunk data. */
final class MirrorFloraMigration {

    private MirrorFloraMigration() {}

    static boolean migrate(ServerLevel level, LevelChunk chunk) {
        MirrorFloraData flora = chunk.getData(ModAttachments.MIRROR_FLORA);
        boolean changed = false;
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (!section.maybeHas(state -> state.is(ModTags.MIRROR_FLORA))) continue;
            int baseY = SectionPos.sectionToBlockCoord(
                    chunk.getSectionYFromSectionIndex(sectionIndex));

            for (int localY = 0; localY < 16; localY++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        BlockState state = section.getBlockState(localX, localY, localZ);
                        if (!state.is(ModTags.MIRROR_FLORA)) continue;
                        cursor.set(chunk.getPos().getMinBlockX() + localX, baseY + localY,
                                chunk.getPos().getMinBlockZ() + localZ);
                        BlockPos pos = cursor.immutable();

                        if (state.is(ModBlocks.MIRROR_VINE.get())) {
                            for (Direction face : Direction.Plane.HORIZONTAL) {
                                if (MirrorVineBlock.hasFace(state, face)) {
                                    changed |= flora.addVineFace(pos, face);
                                }
                            }
                        } else if (state.is(ModBlocks.MIRROR_SHORT_GRASS.get())) {
                            changed |= flora.put(MirrorFloraData.Entry.floor(
                                    pos, MirrorFloraData.Kind.SHORT_GRASS));
                        } else if (state.is(ModBlocks.MIRROR_DEAD_BUSH.get())) {
                            changed |= flora.put(MirrorFloraData.Entry.floor(
                                    pos, MirrorFloraData.Kind.DEAD_BUSH));
                        } else if (state.is(ModBlocks.MIRROR_TALL_GRASS.get())
                                && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER) {
                            changed |= flora.put(MirrorFloraData.Entry.floor(
                                    pos, MirrorFloraData.Kind.TALL_GRASS));
                        }

                        // Mutate the chunk only. Level#setBlock during load notifies neighbours and
                        // can wait on chunks that are still coming in, which deadlocks spawn prep.
                        chunk.setBlockState(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                                net.minecraft.world.level.block.Block.UPDATE_NONE);
                        changed = true;
                    }
                }
            }
        }

        if (changed) chunk.markUnsaved();
        return changed;
    }
}

package com.kadikular.quantimium.client;

import java.util.List;
import java.util.Set;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;


/**
 * Replaces biome grass and foliage tints with the mirror palette while phased, and sickens them
 * near flux rifts while not. Colours are baked into chunk meshes, so {@link ClientPhaseState}
 * rebuilds visible sections on entry/exit and {@link FluxRiftCorruption} rebuilds around rifts.
 */
public final class MirrorPhaseColours {

    /** Cool grey target aligned with the mirror fog wash at medium intensity. */
    private static final float GREY = 0.28f;
    private static final float BLEND = 0.85f;

    private MirrorPhaseColours() {}

    /**
     * The mirror palette while phased; otherwise the real world's own colour, sickened towards
     * violet near a flux rift ({@link FluxRiftCorruption}). Needs a position for the latter.
     */
    public static int tintBiomeColour(int rgb, BlockPos pos) {
        if (ClientPhaseState.isActive() || rgb == -1 || pos == null) return tintBiomeColour(rgb);
        return FluxRiftCorruption.corrupt(rgb, pos);
    }

    public static int tintBiomeColour(int rgb) {
        if (!ClientPhaseState.isActive() || rgb == -1) return rgb;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        float targetR = GREY * 0.95f * 255.0f;
        float targetG = GREY * 0.90f * 255.0f;
        float targetB = GREY * 1.05f * 255.0f;
        int nr = Mth.floor(Mth.lerp(BLEND, r, targetR));
        int ng = Mth.floor(Mth.lerp(BLEND, g, targetG));
        int nb = Mth.floor(Mth.lerp(BLEND, b, targetB));
        return (nr << 16) | (ng << 8) | nb;
    }

    /**
     * {@code vanilla} in the mirror palette: the biome's own colour, taken from vanilla's source so
     * new plants and tweaks come along, then greyed while phased or sickened near a rift.
     */
    public static BlockTintSource phased(BlockTintSource vanilla) {
        return new BlockTintSource() {
            @Override
            public int color(BlockState state) {
                return keepAlpha(vanilla.color(state), tintBiomeColour(vanilla.color(state) & 0xFFFFFF));
            }

            @Override
            public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                int argb = vanilla.colorInWorld(state, level, pos);
                return keepAlpha(argb, tintBiomeColour(argb & 0xFFFFFF, pos));
            }

            @Override
            public int colorAsTerrainParticle(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                int argb = vanilla.colorAsTerrainParticle(state, level, pos);
                return keepAlpha(argb, tintBiomeColour(argb & 0xFFFFFF, pos));
            }

            @Override
            public Set<Property<?>> relevantProperties() {
                return vanilla.relevantProperties();
            }
        };
    }

    /** Colours are ARGB since 1.21.11; the palette works in RGB, so the source's alpha goes back on. */
    private static int keepAlpha(int argb, int rgb) {
        return (argb & 0xFF000000) | (rgb & 0xFFFFFF);
    }

    /** Takes over vanilla's grass and foliage tints, each wrapped in {@link #phased}. */
    public static void register(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(List.of(phased(BlockTintSources.doubleTallGrass())), Blocks.LARGE_FERN, Blocks.TALL_GRASS);
        event.register(List.of(phased(BlockTintSources.grass())),
                Blocks.FERN, Blocks.SHORT_GRASS, Blocks.POTTED_FERN, Blocks.BUSH);
        event.register(List.of(phased(BlockTintSources.grassBlock())), Blocks.GRASS_BLOCK);
        event.register(List.of(BlockTintSources.constant(-1), phased(BlockTintSources.grass())),
                Blocks.PINK_PETALS, Blocks.WILDFLOWERS);
        event.register(List.of(phased(BlockTintSources.sugarCane())), Blocks.SUGAR_CANE);
        event.register(List.of(phased(BlockTintSources.constant(FoliageColor.FOLIAGE_EVERGREEN))), Blocks.SPRUCE_LEAVES);
        event.register(List.of(phased(BlockTintSources.constant(FoliageColor.FOLIAGE_BIRCH))), Blocks.BIRCH_LEAVES);
        event.register(List.of(phased(BlockTintSources.foliage())),
                Blocks.OAK_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES,
                Blocks.VINE, Blocks.MANGROVE_LEAVES);
        event.register(List.of(phased(BlockTintSources.dryFoliage())), Blocks.LEAF_LITTER);
    }
}

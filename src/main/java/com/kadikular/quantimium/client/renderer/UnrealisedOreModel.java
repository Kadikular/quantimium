package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.client.ClientPhaseState;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import org.jspecify.annotations.Nullable;

/**
 * Wears another ore's model. The facade is resolved once per position while the section mesh is
 * built, which is what buys real lighting and culling; the mesher hands us the level and position,
 * so no block entity is involved anywhere in the chain. Its own model is the fallback: the phased
 * view, and anywhere with no level to ask.
 */
public class UnrealisedOreModel extends DelegateBlockStateModel {

    public UnrealisedOreModel(BlockStateModel fallback) {
        super(fallback);
    }

    /** The ore it looks like at {@code pos}, or null to look like itself. */
    @Nullable
    private static BlockState facade(BlockAndTintGetter level, BlockPos pos) {
        return ClientPhaseState.isActive() ? null : UnrealisedOreFacadeCache.facadeFor(level, pos);
    }

    private static BlockStateModel modelOf(BlockState facade) {
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(facade);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
                             List<BlockStateModelPart> parts) {
        BlockState facade = facade(level, pos);
        if (facade == null) {
            super.collectParts(level, pos, state, random, parts);
        } else {
            modelOf(facade).collectParts(level, pos, facade, random, parts);
        }
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        BlockState facade = facade(level, pos);
        return facade == null ? super.particleMaterial(level, pos, state) : modelOf(facade).particleMaterial(level, pos, facade);
    }

    @Override
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        BlockState facade = facade(level, pos);
        return facade == null ? super.materialFlags(level, pos, state) : modelOf(facade).materialFlags(level, pos, facade);
    }
}

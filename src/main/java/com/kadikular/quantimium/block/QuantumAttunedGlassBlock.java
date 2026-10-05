package com.kadikular.quantimium.block;

import net.minecraft.server.level.ServerLevel;
import com.kadikular.quantimium.block.multiblock.MultiblockParts;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Faintly glowing glass whose seams are culled against its own kind.
 *
 * <p>Inheriting {@link TransparentBlock} is what makes a wall of this read as one pane. The face
 * culling lives in {@code HalfTransparentBlock#skipRendering} further up the chain, which a plain
 * {@code Block} does not have — which is why the first version showed every internal seam.
 */
public class QuantumAttunedGlassBlock extends TransparentBlock {

    public static final MapCodec<QuantumAttunedGlassBlock> CODEC =
            simpleCodec(QuantumAttunedGlassBlock::new);

    /**
     * Which face the formed model leaves out, pointed inwards by the hall.
     *
     * <p>A pane facing the cell would otherwise hang in front of the void as a visible sheet of
     * glass. Culling only removes faces between two panes, so the one looking into open air has to
     * be dropped by the model — and since the direction differs per wall, it needs a property to
     * rotate on. Corner panes point this at a neighbouring pane, where the face was culled anyway,
     * which leaves the rotation free to carry {@link #CORNER}'s pillar instead.
     */
    public static final EnumProperty<Direction> OPEN_FACE =
            EnumProperty.create("open_face", Direction.class, Direction.Plane.HORIZONTAL.stream().toList());

    /**
     * Set on the four panes at the corners of a formed shell, which carry a pillar in their model.
     *
     * <p>Needed as its own property because a corner and a wall centre can want the same
     * {@code open_face} — the corner's is chosen to place the pillar rather than to hide a face.
     */
    public static final BooleanProperty CORNER = BooleanProperty.create("corner");

    public QuantumAttunedGlassBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(QuantumFoundryStructure.FORMED, false)
                .setValue(OPEN_FACE, Direction.NORTH)
                .setValue(CORNER, false));
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(QuantumFoundryStructure.FORMED, OPEN_FACE, CORNER);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
                           BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // Guarded on block identity, so writing FORMED below does not recurse.
        if (!state.is(oldState.getBlock())) MultiblockParts.notifyNearby(level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        MultiblockParts.notifyNearby(level, pos);
    }

    // Deliberately NOT tinted glass's light blocking. Blocking skylight lowers the heightmap under
    // the shell, which left one corner of the hall in a hard shadow that the surrounding plinth
    // picked up. Behaving like ordinary glass keeps the lighting even; the block's own emission
    // (see ModBlocks) is what makes the cell read at night.
}

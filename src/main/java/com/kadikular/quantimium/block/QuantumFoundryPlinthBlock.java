package com.kadikular.quantimium.block;

import net.minecraft.server.level.ServerLevel;
import com.kadikular.quantimium.block.entity.QuantumFoundryPartBlockEntity;
import com.kadikular.quantimium.block.multiblock.MultiblockHost;
import com.kadikular.quantimium.block.multiblock.MultiblockParts;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public class QuantumFoundryPlinthBlock extends BaseEntityBlock {
    public static final MapCodec<QuantumFoundryPlinthBlock> CODEC =
            simpleCodec(QuantumFoundryPlinthBlock::new);

    /**
     * Where in a formed 3×3 this plinth sits, so a ring can be drawn as one piece of masonry.
     *
     * <p>Only read when formed; a loose plinth keeps its plain textures whatever this says. Drives
     * the outward faces as well as the top: in a closed ring every inward face is buried against
     * another full cube, so the only vertical faces anyone sees are the outward ones — one on an
     * {@link Role#EDGE}, two on a {@link Role#CORNER}, none at all on a {@link Role#CENTRE}.
     */
    public enum Role implements StringRepresentable {
        EDGE("edge"),
        CORNER("corner"),
        CENTRE("centre");

        private final String name;

        Role(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Role> ROLE = EnumProperty.create("role", Role.class);

    /**
     * Rotation for the role's art, as a compass direction mapped to 0/90/180/270 in the blockstate.
     *
     * <p>An edge points outward. A corner points at whichever direction turns the north-west piece
     * onto it, so both textures are drawn once in their north / north-west orientation and rotated
     * into the other three positions.
     */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * True for the ring on top of a Containment Hall, which gets its own textures.
     *
     * <p>A cap is seen from angles no other ring is — its underside is the cell's ceiling, and the
     * beacon leaves through the middle of its top — so it earns a separate set rather than a
     * property on the art. The Foundry ring and the hall's base still share one set: both sit on the
     * ground with a controller in the middle, so they read the same way.
     */
    public static final BooleanProperty CAP = BooleanProperty.create("cap");

    public QuantumFoundryPlinthBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(QuantumFoundryStructure.FORMED, false)
                .setValue(ROLE, Role.EDGE)
                .setValue(FACING, Direction.NORTH)
                .setValue(CAP, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(QuantumFoundryStructure.FORMED, ROLE, FACING, CAP);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        // An unformed plinth is just a building block, so the click has to fall through to whatever
        // the player is holding: swallowing it makes the ring you are halfway through building
        // impossible to place against without crouching. Read the blockstate rather than the host,
        // because the host is server-only data and the two sides must agree on who took the click.
        if (!state.getValue(QuantumFoundryStructure.FORMED)) return InteractionResult.PASS;

        if (level.getBlockEntity(pos) instanceof QuantumFoundryPartBlockEntity part) {
            MultiblockHost host = part.host();
            if (!level.isClientSide() && host != null) {
                player.openMenu(host, host.controllerPos());
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
                           BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock())) MultiblockParts.notifyNearby(level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        MultiblockParts.notifyNearby(level, pos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QuantumFoundryPartBlockEntity(pos, state);
    }
}

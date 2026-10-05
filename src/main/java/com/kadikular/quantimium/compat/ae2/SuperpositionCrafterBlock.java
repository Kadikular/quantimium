package com.kadikular.quantimium.compat.ae2;

import net.minecraft.world.level.redstone.Orientation;
import com.kadikular.quantimium.util.LegacyEnergy;
import com.kadikular.quantimium.util.LegacyItems;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.Direction;
import appeng.api.AECapabilities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * See {@link SuperpositionCrafterBlockEntity}. Its core shows its link to the network: dark when
 * offline, a soft glow when on a powered network, bright while it crafts.
 */
public class SuperpositionCrafterBlock extends BaseEntityBlock {

    public enum Link implements StringRepresentable {
        OFFLINE("offline", 2),
        ONLINE("online", 7),
        ACTIVE("active", 12);

        private final String name;
        public final int light;

        Link(String name, int light) {
            this.name = name;
            this.light = light;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final MapCodec<SuperpositionCrafterBlock> CODEC = simpleCodec(SuperpositionCrafterBlock::new);
    public static final EnumProperty<Link> LINK = EnumProperty.create("link", Link.class);
    /** A port plate on each side something connects to: its open frame would otherwise meet nothing. */
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    public SuperpositionCrafterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LINK, Link.OFFLINE)
                .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
    }

    private static BooleanProperty sideProperty(Direction side) {
        return switch (side) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> null;
        };
    }

    /** Whether the neighbour on {@code side} is something that would plug in: ME, power or items. */
    private static boolean connects(Level level, BlockPos pos, Direction side) {
        BlockPos neighbour = pos.relative(side);
        Direction facing = side.getOpposite();
        return level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, neighbour) != null
                || LegacyEnergy.legacy(level.getCapability(Capabilities.Energy.BLOCK, neighbour, facing)) != null
                || LegacyItems.legacy(level.getCapability(Capabilities.Item.BLOCK, neighbour, facing)) != null;
    }

    /** The state with a port plate on every side that has something to meet. */
    public static BlockState withPorts(BlockState state, Level level, BlockPos pos) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            state = state.setValue(sideProperty(side), connects(level, pos, side));
        }
        return state;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock, @Nullable Orientation orientation,
                                   boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbourBlock, orientation, movedByPiston);
        if (level.isClientSide()) return;
        BlockState updated = withPorts(state, level, pos);
        if (updated != state) level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide() || oldState.is(state.getBlock())) return;
        BlockState updated = withPorts(state, level, pos);
        if (updated != state) level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LINK, NORTH, EAST, SOUTH, WEST);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SuperpositionCrafterBlockEntity crafter) {
            player.openMenu(crafter, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SuperpositionCrafterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, Ae2Content.SUPERPOSITION_CRAFTER_BE.get(), SuperpositionCrafterBlockEntity::tick);
    }
}

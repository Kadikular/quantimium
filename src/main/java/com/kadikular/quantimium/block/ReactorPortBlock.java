package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.ReactorPortBlockEntity;
import com.kadikular.quantimium.reactor.ReactorTraces;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A port on the Reactor's rim, in place of a plinth block. Input takes items into the horizon,
 * Output holds what the Reactor makes for pipes to take, Energy feeds it, and the Materialiser Port
 * offers everything it holds or could make to pipes and storage buses. Place as many as you like.
 */
public class ReactorPortBlock extends BaseEntityBlock {

    /** {@code ME}: the ME Superposition Port, which only exists with AE2 (compat.ae2). */
    public enum Kind { INPUT, OUTPUT, ENERGY, MATERIALISER, ME }

    private final Kind kind;

    public ReactorPortBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(QuantumFoundryStructure.FORMED, false)
                .setValue(ReactorTraces.NORTH, 0).setValue(ReactorTraces.EAST, 0)
                .setValue(ReactorTraces.SOUTH, 0).setValue(ReactorTraces.WEST, 0));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return switch (kind) {
            case INPUT -> simpleCodec(p -> new ReactorPortBlock(p, Kind.INPUT));
            case OUTPUT -> simpleCodec(p -> new ReactorPortBlock(p, Kind.OUTPUT));
            case ENERGY -> simpleCodec(p -> new ReactorPortBlock(p, Kind.ENERGY));
            case MATERIALISER -> simpleCodec(p -> new ReactorPortBlock(p, Kind.MATERIALISER));
            case ME -> simpleCodec(p -> new ReactorPortBlock(p, Kind.ME));
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(QuantumFoundryStructure.FORMED, ReactorTraces.NORTH, ReactorTraces.EAST, ReactorTraces.SOUTH,
                ReactorTraces.WEST);
    }

    /** Ports sit in the plinth, so the plinth's traces run through them too. */
    @org.jetbrains.annotations.Nullable
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return ReactorTraces.at(defaultBlockState(), context.getClickedPos());
    }

    @Override
    protected void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        BlockState traced = ReactorTraces.at(state, pos);
        if (traced != state && !level.isClientSide()) level.setBlock(pos, traced, Block.UPDATE_CLIENTS);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReactorPortBlockEntity(pos, state);
    }
}

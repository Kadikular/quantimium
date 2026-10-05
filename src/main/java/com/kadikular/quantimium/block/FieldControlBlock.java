package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.FieldControlBlockEntity;
import com.kadikular.quantimium.init.ModBlockEntities;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The field hardware that holds a chunk's field where you set it: the Flux Maintainer keeps flux up to
 * a floor, the Anomaly Siphon keeps anomaly down under a ceiling, and the Field Regulator does both, for
 * less. The Basic Anomaly Siphon is the early, fixed Siphon, and the Flux Suppressor keeps flux (not
 * anomaly) under a ceiling. One block class for all five, told apart by {@link Kind}; the work is in
 * {@link FieldControlBlockEntity}.
 */
public class FieldControlBlock extends BaseEntityBlock {

    /** Working: raising flux or pulling anomaly this moment. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    /** What a field control block does, at what share of the FE, and how hard it pulls. */
    public enum Kind {
        MAINTAINER(true, false, false, 1.0, 1.0, false),
        SIPHON(false, true, false, 1.0, 1.0, false),
        REGULATOR(true, true, false, 0.8, 1.0, false),
        /** Early: holds anomaly at the top of Low (no setting), at a twelfth of a Siphon's pull and a quarter of its FE. */
        BASIC_SIPHON(false, true, false, 0.25, 1.0 / 12.0, true),
        /** Early: holds flux, and only flux, under a ceiling, for firebreaks and starving rifts. No fragments. */
        SUPPRESSOR(false, false, true, 0.25, 0.25, false);

        private final boolean raisesFlux;
        private final boolean pullsAnomaly;
        private final boolean pullsFlux;
        private final double feFactor;
        private final double pullScale;
        private final boolean fixedCeiling;

        Kind(boolean raisesFlux, boolean pullsAnomaly, boolean pullsFlux, double feFactor, double pullScale,
             boolean fixedCeiling) {
            this.raisesFlux = raisesFlux;
            this.pullsAnomaly = pullsAnomaly;
            this.pullsFlux = pullsFlux;
            this.feFactor = feFactor;
            this.pullScale = pullScale;
            this.fixedCeiling = fixedCeiling;
        }

        public boolean raisesFlux() {
            return raisesFlux;
        }

        /** Pulls anomaly (and so makes fragments). */
        public boolean pullsAnomaly() {
            return pullsAnomaly;
        }

        /** Pulls flux: the Suppressor. */
        public boolean pullsFlux() {
            return pullsFlux;
        }

        public boolean pulls() {
            return pullsAnomaly || pullsFlux;
        }

        /** Has a ceiling to set (the Basic Siphon's is fixed at Low). */
        public boolean setsCeiling() {
            return pulls() && !fixedCeiling;
        }

        public double feFactor() {
            return feFactor;
        }

        /** Its pull, as a share of a Siphon's. */
        public double pullScale() {
            return pullScale;
        }
    }

    private final Kind kind;

    public FieldControlBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(properties -> new FieldControlBlock(kind, properties));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FieldControlBlockEntity control) {
            player.openMenu(control, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FieldControlBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.FIELD_CONTROL_BE.get(), FieldControlBlockEntity::tick);
    }
}

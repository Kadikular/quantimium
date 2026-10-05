package com.kadikular.quantimium.block;

import com.kadikular.quantimium.item.BlockTooltip;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import com.kadikular.quantimium.superposition.PodModule;
import com.kadikular.quantimium.superposition.PodStructure;
import com.kadikular.quantimium.superposition.Superposition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * One of the four edge slots of a Superposition Pod's cradle: plain Pod Plating, or a module that
 * upgrades the pod. See {@link PodStructure} and {@link PodModule}.
 */
public class PodModuleBlock extends Block implements BlockTooltip {

    /**
     * Which way its top runs. A formed pod turns each edge so the plating's treads cross the way you step
     * in, towards the capsule; along X for the edges north and south of it, along Z for east and west.
     */
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    private final PodModule module;

    public PodModuleBlock(PodModule module, Properties properties) {
        super(properties);
        this.module = module;
        registerDefaultState(stateDefinition.any().setValue(PodCradleBlock.FORMED, false).setValue(AXIS, Direction.Axis.X));
    }

    public PodModule module() {
        return module;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PodCradleBlock.FORMED, AXIS);
    }

    @Override
    public void appendTooltip(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("block.quantimium.pod_module." + module.getSerializedName() + ".tip")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    /** A Stash module opens its owner's stash when used, as the stash key does from anywhere. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (module != PodModule.STASH) return InteractionResult.PASS;
        if (!level.isClientSide() && player instanceof ServerPlayer server) Superposition.openStash(server);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock())) PodStructure.changed(level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        PodStructure.changed(level, pos);
    }
}

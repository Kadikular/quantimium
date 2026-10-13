package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Superposition Pod's Logistics module: a Logistics Terminal in the pod's cradle. Where a controller's light ends on
 * it, its owner opens that network's terminal from anywhere, with the logistics key, while one of their doubles waits
 * in the pod.
 */
public class PodLogisticsModuleBlockEntity extends LogisticsTerminalBlockEntity {

    public PodLogisticsModuleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POD_LOGISTICS_MODULE_BE.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.pod_logistics_module");
    }
}

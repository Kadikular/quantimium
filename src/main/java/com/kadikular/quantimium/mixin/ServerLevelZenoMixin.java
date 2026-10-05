package com.kadikular.quantimium.mixin;

import com.kadikular.quantimium.zeno.ZenoFields;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Inside a pausing Zeno field nothing is chosen for a random tick: crops stop growing, leaves stop
 * decaying, ice stops melting, copper stops weathering. Only random ticks; scheduled ticks and block
 * entities carry on, so machines and redstone inside the field still work.
 *
 * <p>{@code WrapWithCondition} rather than a redirect, so other mods hooking the same calls still
 * run; the check is a map lookup that returns at once when no field exists.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelZenoMixin {

    @WrapWithCondition(
            method = "tickChunk",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private boolean quantimium$zenoBlock(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        return !ZenoFields.isPaused(level, pos);
    }

    @WrapWithCondition(
            method = "tickChunk",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/material/FluidState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private boolean quantimium$zenoFluid(FluidState state, ServerLevel level, BlockPos pos, RandomSource random) {
        return !ZenoFields.isPaused(level, pos);
    }
}

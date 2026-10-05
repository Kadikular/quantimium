package com.kadikular.quantimium.mixin;

import com.kadikular.quantimium.debug.TickProfiler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Wraps the level's call into each ticking block entity, for {@link TickProfiler}. Does nothing but
 * read one flag unless the profiler is watching something.
 */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class BlockEntityTickTimingMixin {

    @Shadow
    @Final
    private BlockEntity blockEntity;

    @WrapMethod(method = "tick")
    private void quantimium$timeTick(Operation<Void> original) {
        if (!TickProfiler.active()) {
            original.call();
            return;
        }
        long start = System.nanoTime();
        original.call();
        TickProfiler.record(blockEntity, System.nanoTime() - start);
    }
}

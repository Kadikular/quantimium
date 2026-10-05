package com.kadikular.quantimium.mixin;

import com.kadikular.quantimium.init.ModTags;
import com.kadikular.quantimium.phase.MirrorPhase;
import com.kadikular.quantimium.phase.OverlayBlockEdit;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overlay crystals exist in the backing world as blocks; machines must not vacuum them. A crystal whose
 * host is gone is let go, though, or it would be left floating where the host was.
 */
@Mixin(Level.class)
public abstract class LevelMixin {

    @Inject(
            method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void quantimium$protectAnomalite(BlockPos pos, boolean dropBlock, Entity entity, int recursion,
                                             CallbackInfoReturnable<Boolean> cir) {
        Level self = (Level) (Object) this;
        if (self.isClientSide()) return;
        BlockState state = self.getBlockState(pos);
        if (!state.is(ModTags.ANOMALITE)) return;
        if (OverlayBlockEdit.allowed()) return;
        // Its host is gone (broken, moved, burnt): the crystal goes with it, as vanilla asks when the
        // crystal reports it can no longer stand. Only a crystal still on its host is protected.
        if (!state.canSurvive(self, pos)) return;
        if (entity instanceof Player player && MirrorPhase.canHarvestOverlay(player)) return;
        cir.setReturnValue(false);
    }
}

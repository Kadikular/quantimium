package com.kadikular.quantimium.mixin;

import com.kadikular.quantimium.laser.LaserNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every block set in a server level, by anything (players, pistons, water, other mods), tells the laser
 * networks: a beam crossing it is traced again at once, rather than when its next check comes round.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelLaserMixin {

    @Inject(method = "updatePOIOnBlockStateChange", at = @At("HEAD"))
    private void quantimium$tellLasers(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        LaserNetworks.blockChanged((ServerLevel) (Object) this, pos, oldState, newState);
    }
}

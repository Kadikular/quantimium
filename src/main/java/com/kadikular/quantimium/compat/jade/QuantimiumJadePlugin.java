package com.kadikular.quantimium.compat.jade;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade (optional): looking at any Quantimium block shows the field where it stands (flux, where it is
 * heading, anomaly, how full any containment over it is), what anomaly is costing machines there, and
 * the block's own readout, the same line the Flux Meter gives. Jade finds this class by its
 * annotation; nothing else in the mod touches Jade, so the mod runs without it.
 */
@WailaPlugin(Quantimium.MODID)
public final class QuantimiumJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(FieldData.INSTANCE, BlockEntity.class);
        registration.registerBlockDataProvider(AccelerationProvider.Data.INSTANCE, BlockEntity.class);
        // A Laser Focus block is named for the piece looked at (its pick result), not the whole block.
        registration.blockOperations().pick(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(com.kadikular.quantimium.Quantimium.MODID, "laser_focus")));
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(FieldProvider.INSTANCE, Block.class);
        registration.registerBlockComponent(AccelerationProvider.Client.INSTANCE, Block.class);
    }
}

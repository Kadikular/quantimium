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
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(FieldProvider.INSTANCE, Block.class);
    }
}

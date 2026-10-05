package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import net.minecraft.gametest.framework.GameTestServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.List;

/**
 * The band rewards (running hot pays) are flattened for the test server: every band gets the top
 * batch, rate, catalysts and the plain tax. Tests of other mechanics run in a cold field, and heating
 * their plots would spill anomaly into their neighbours'. Tests of the rewards themselves switch the
 * defaults back on in a batch of their own ({@link #on}) and flatten them again when done.
 */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class BandRewards {

    private BandRewards() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (event.getServer() instanceof GameTestServer) flatten();
    }

    public static void flatten() {
        Config.CRAFTER_TAX_BY_BAND.set(List.of(100, 100, 100, 100, 100));
        Config.CRAFTER_CATALYST_BANDS.set(List.of("low", "low", "low", "low", "low"));
        Config.SIMULATOR_BATCH_BY_BAND.set(List.of(32, 32, 32, 32, 32));
        Config.ZENO_FACTOR_BY_BAND.set(List.of(16, 16, 16, 16, 16));
    }

    /** The shipped defaults, for a test of the rewards; call {@link #flatten} when it ends. */
    public static void on() {
        Config.CRAFTER_TAX_BY_BAND.set(Config.CRAFTER_TAX_BY_BAND.getDefault());
        Config.CRAFTER_CATALYST_BANDS.set(Config.CRAFTER_CATALYST_BANDS.getDefault());
        Config.SIMULATOR_BATCH_BY_BAND.set(Config.SIMULATOR_BATCH_BY_BAND.getDefault());
        Config.ZENO_FACTOR_BY_BAND.set(Config.ZENO_FACTOR_BY_BAND.getDefault());
    }
}

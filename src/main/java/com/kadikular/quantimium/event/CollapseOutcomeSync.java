package com.kadikular.quantimium.event;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.network.CollapseOutcomesPayload;
import com.kadikular.quantimium.recipe.observation.CollapseOutcome;
import com.kadikular.quantimium.recipe.observation.CollapseTable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Ships the collapse loot table's weighted outputs to clients on join and after {@code /reload},
 * which is the only way a recipe viewer can see them: loot tables stay server-side.
 */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class CollapseOutcomeSync {

    private CollapseOutcomeSync() {}

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        MinecraftServer server = event.getPlayerList().getServer();
        List<CollapseOutcome> outcomes = CollapseTable.read(server);
        if (outcomes.isEmpty()) return;
        CollapseOutcomesPayload payload = new CollapseOutcomesPayload(outcomes);
        ServerPlayer target = event.getPlayer();
        if (target != null) {
            PacketDistributor.sendToPlayer(target, payload);
        } else {
            PacketDistributor.sendToAllPlayers(payload);
        }
    }
}

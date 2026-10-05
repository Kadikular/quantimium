package com.kadikular.quantimium;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Code-granted advancements. JSON criteria use {@code minecraft:impossible}. */
public final class QuantimiumAdvancements {

    private QuantimiumAdvancements() {}

    public static void award(ServerPlayer player, String id, String criterion) {
        AdvancementHolder advancement = player.level().getServer().getAdvancements()
                .get(Identifier.fromNamespaceAndPath(Quantimium.MODID, id));
        if (advancement != null) {
            player.getAdvancements().award(advancement, criterion);
        }
    }
}

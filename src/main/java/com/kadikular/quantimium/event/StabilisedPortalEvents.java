package com.kadikular.quantimium.event;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.QuantimiumAdvancements;
import com.kadikular.quantimium.block.StabilisedPortalFrameBlock;
import com.kadikular.quantimium.block.StabilisedPortalShape;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Lights a completed frame with a Quantimium Trace (flint and steel still works). */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class StabilisedPortalEvents {

    private StabilisedPortalEvents() {}

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!StabilisedPortalFrameBlock.isLighter(stack)) return;
        BlockPos inside = event.getPos().relative(event.getFace());
        if (!StabilisedPortalShape.tryLight(event.getLevel(), inside)
                && !StabilisedPortalShape.tryLight(event.getLevel(), event.getPos())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            QuantimiumAdvancements.award(player, "a_way_to_come_and_go", "lit_portal");
        }
        var ignite = StabilisedPortalFrameBlock.lightSound(stack);
        StabilisedPortalFrameBlock.consumeLighter(stack, event.getEntity(), event.getHand());
        event.getLevel().playSound(null, inside, ignite, SoundSource.BLOCKS, 1.0f,
                event.getLevel().getRandom().nextFloat() * 0.4f + 0.8f);
    }
}

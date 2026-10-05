package com.kadikular.quantimium.superposition;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.item.SophonBinding;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/** The death save, and keeping the Sophon count honest when one falls out of the world. */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class SuperpositionEvents {

    private SuperpositionEvents() {}

    /** Late, so anything else that saves a player gets its chance first and the Sophon is kept. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isSpectator() || player.getHealth() > 0) return;
        if (Superposition.rescue(player)) event.setCanceled(true);
    }

    /** Picked up where it was knocked out, a Sophon is carried now: out of Recovery's reach. */
    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer().level() instanceof ServerLevel level)) return;
        SophonBinding binding = event.getOriginalStack().get(ModDataComponents.SOPHON.get());
        if (binding == null) return;
        SophonRegistry registry = SophonRegistry.get(level.getServer());
        SophonRegistry.Entry entry = registry.entry(binding.id());
        if (entry != null && entry.dropped()) registry.folded(binding.id());
    }

    /**
     * A Sophon can only leave the world as an item by being picked up (its stack is emptied first) or
     * by being destroyed: it neither burns nor despawns, so what is left is the void or a kill command.
     * Either way that Sophon is gone and stops counting towards its owner's cap.
     */
    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof ItemEntity item) || !(event.getLevel() instanceof ServerLevel level)) return;
        Entity.RemovalReason reason = item.getRemovalReason();
        if (reason != Entity.RemovalReason.KILLED && reason != Entity.RemovalReason.DISCARDED) return;
        if (item.getItem().isEmpty()) return;
        SophonBinding binding = item.getItem().get(ModDataComponents.SOPHON.get());
        if (binding == null) return;
        SophonRegistry registry = SophonRegistry.get(level.getServer());
        SophonRegistry.Entry entry = registry.entry(binding.id());
        // Only a Sophon that was an item (carried, or lying where it was knocked out) can have been this one.
        if (entry != null && !entry.inPod() && !entry.inField()) registry.remove(binding.id());
    }
}

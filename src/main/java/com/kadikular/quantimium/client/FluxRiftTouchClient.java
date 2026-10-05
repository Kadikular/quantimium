package com.kadikular.quantimium.client;

import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.item.LanceTargeting;
import com.kadikular.quantimium.network.FluxRiftTouchPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Swinging at a flux rift. A rift is not a block or an entity, so the vanilla attack would pass
 * through it to whatever is behind; this catches the swing when the rift is the nearer thing under
 * the crosshair, and tells the server, which decides what the rift does about it.
 */
@EventBusSubscriber(modid = Quantimium.MODID, value = Dist.CLIENT)
public final class FluxRiftTouchClient {

    /** Survival reach, plus a little: the rift is big and ragged. */
    private static final double REACH = 4.5;

    private FluxRiftTouchClient() {}

    @SubscribeEvent
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 look = minecraft.player.getViewVector(1.0f);
        double behind = minecraft.hitResult == null || minecraft.hitResult.getType() == HitResult.Type.MISS
                ? REACH : minecraft.hitResult.getLocation().distanceTo(eye);
        LanceTargeting.RiftSphere hit = null;
        double hitDistance = Math.min(REACH, behind);
        for (LanceTargeting.RiftSphere rift : FluxRiftClientCache.spheres()) {
            double distance = raySphere(eye, look, rift.centre(), rift.radius());
            if (distance >= 0.0 && distance < hitDistance) {
                hit = rift;
                hitDistance = distance;
            }
        }
        if (hit == null) return;
        event.setCanceled(true);
        event.setSwingHand(true);
        ClientPacketDistributor.sendToServer(new FluxRiftTouchPayload(hit.id()));
    }

    private static double raySphere(Vec3 origin, Vec3 direction, Vec3 centre, double radius) {
        Vec3 offset = origin.subtract(centre);
        double along = offset.dot(direction);
        double outside = offset.lengthSqr() - radius * radius;
        if (outside <= 0.0) return 0.0;
        double discriminant = along * along - outside;
        if (discriminant < 0.0) return -1.0;
        double distance = -along - Math.sqrt(discriminant);
        return distance >= 0.0 ? distance : -1.0;
    }
}

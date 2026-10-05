package com.kadikular.quantimium.client;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.network.OpenStashPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** The stash key: opens your stash from anywhere, while a pod with a Stash module holds one of your doubles. */
@EventBusSubscriber(modid = Quantimium.MODID, value = Dist.CLIENT)
public final class SuperpositionKeys {

    /** The controls page section for every Quantimium key; its name is key.category.quantimium.quantimium. */
    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(Quantimium.MODID, "quantimium"));

    public static final KeyMapping OPEN_STASH = new KeyMapping("key.quantimium.stash", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);

    private SuperpositionKeys() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (OPEN_STASH.consumeClick()) {
            if (Minecraft.getInstance().player != null) ClientPacketDistributor.sendToServer(OpenStashPayload.INSTANCE);
        }
    }

    /** Registered on the mod bus, from {@link ClientModEvents}. */
    public static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_STASH);
    }
}

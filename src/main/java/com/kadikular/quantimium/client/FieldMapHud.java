package com.kadikular.quantimium.client;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.network.FieldSurveyPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * A heat map of the field round the player, chunk by chunk, north up: flux as azure, anomaly as a
 * violet wash over it, the player's chunk outlined, and chunks under containment marked in the corner:
 * pale when held, amber when overloaded. It draws the survey
 * the Mirror Lens already receives, so it shows while the field can be seen (a lens worn, or in the
 * mirror) and the map key has switched it on. Built to watch Field Model 2.0 settle; M2 of the game
 * plan polishes it.
 */
@EventBusSubscriber(modid = Quantimium.MODID, value = Dist.CLIENT)
public final class FieldMapHud implements GuiLayer {

    public static final KeyMapping TOGGLE = new KeyMapping("key.quantimium.field_map", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, SuperpositionKeys.CATEGORY);

    private static final int MARGIN = 6;
    /** Below the Mirror Lens readout, which is at most this tall. */
    private static final int BELOW_READOUT = 44;
    private static final int PAD = 4;

    private static final int PANEL = 0xB00B0D18;
    private static final int EDGE = 0xFF262A4A;
    private static final int TEXT = 0xFFDFE3F4;
    private static final int DIM = 0xFF8D95C2;

    private static boolean shown;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (TOGGLE.consumeClick()) shown = !shown;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!shown || player == null || minecraft.options.hideGui || minecraft.getDebugOverlay().showDebugScreen()) return;
        if (!MirrorLensClient.canSee()) return;
        FieldSurveyPayload survey = MirrorLensClient.survey();
        if (survey == null) return;

        Font font = minecraft.font;
        int grid = FieldMapRenderer.size(survey);
        Component title = Component.translatable("gui.quantimium.field_map.title");
        int width = Math.max(grid, font.width(title)) + PAD * 2;
        int height = PAD + 10 + grid + 3 + 10 + PAD;

        Config.HudCorner corner = Config.fieldHudCorner();
        boolean right = corner == Config.HudCorner.TOP_RIGHT || corner == Config.HudCorner.BOTTOM_RIGHT;
        boolean bottom = corner == Config.HudCorner.BOTTOM_LEFT || corner == Config.HudCorner.BOTTOM_RIGHT;
        int x = right ? graphics.guiWidth() - MARGIN - width : MARGIN;
        int y = bottom ? graphics.guiHeight() - MARGIN - height - 40 - BELOW_READOUT : MARGIN + BELOW_READOUT;

        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.outline(x, y, width, height, EDGE);
        graphics.text(font, title, x + PAD, y + PAD, TEXT, false);

        int gridX = x + PAD + (width - PAD * 2 - grid) / 2;
        int gridY = y + PAD + 10;
        FieldMapRenderer.draw(graphics, survey, gridX, gridY,
                player.chunkPosition().x() - survey.centreX(), player.chunkPosition().z() - survey.centreZ());

        MirrorLensClient.Reading here = MirrorLensClient.here(player);
        if (here != null) {
            Component values = Component.translatable("gui.quantimium.field_map.values",
                    String.format("%,.0f", here.flux()), String.format("%,.0f", here.anomaly()));
            graphics.text(font, values, x + PAD, gridY + grid + 3, DIM, false);
        }
    }

}

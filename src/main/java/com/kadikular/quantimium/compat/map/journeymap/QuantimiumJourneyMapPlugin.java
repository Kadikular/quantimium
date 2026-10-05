package com.kadikular.quantimium.compat.map.journeymap;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.FieldMapClient;
import com.kadikular.quantimium.client.FluxRiftClientCache;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.display.MarkerOverlay;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.fullscreen.IThemeButton;
import journeymap.api.v2.client.model.MapImage;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The field on JourneyMap: a filled square over each chunk holding one, tinted as the Mirror Lens's map
 * draws it (or by containment load, see Config.mapOverlay), titled with its values. JourneyMap finds
 * the plugin by its annotation, so without JourneyMap it's never loaded. A button on the fullscreen
 * map's toolbar cycles it through the field, load and off, as the key does. Flux rifts are marked
 * too, with their stage, under a toolbar button of their own.
 */
@JourneyMapPlugin(apiVersion = IClientAPI.API_VERSION)
public final class QuantimiumJourneyMapPlugin implements IClientPlugin {

    private record Shown(PolygonOverlay overlay, int colour) {}

    private static final Identifier ICON =
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/journeymap_field.png");
    private static final Identifier RIFTS_ICON =
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/journeymap_rifts.png");
    private static final Identifier RIFT_MARKER =
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/journeymap_rift_marker.png");

    private IClientAPI api;
    private final Map<Long, Shown> shown = new HashMap<>();
    private ResourceKey<Level> shownIn;
    private final List<MarkerOverlay> riftMarkers = new ArrayList<>();

    @Override
    public String getModId() {
        return Quantimium.MODID;
    }

    @Override
    public void initialize(IClientAPI api) {
        this.api = api;
        FieldMapClient.listen(this::refresh);
        FluxRiftClientCache.listen(this::refreshRifts);
        FullscreenEventRegistry.ADDON_BUTTON_DISPLAY_EVENT.subscribe(Quantimium.MODID, event -> {
            IThemeButton button = event.getThemeButtonDisplay().addThemeToggleButton(
                    Component.translatable("gui.quantimium.map_overlay.button").getString(), ICON,
                    Config.mapOverlay() != Config.MapOverlay.OFF, pressed -> show(pressed, FieldMapClient.cycle()));
            show(button, Config.mapOverlay());
            IThemeButton rifts = event.getThemeButtonDisplay().addThemeToggleButton(
                    Component.translatable("gui.quantimium.map_rifts.button").getString(), RIFTS_ICON,
                    Config.mapRifts(), pressed -> {
                        // Set from the config rather than flipped, whatever JourneyMap did to the button first.
                        boolean on = !Config.mapRifts();
                        Config.MAP_RIFTS.set(on);
                        Config.MAP_RIFTS.save();
                        pressed.setToggled(on);
                        refreshRifts();
                    });
            rifts.setToggled(Config.mapRifts());
        });
    }

    /** Marks every rift this client knows of, in this dimension, with its stage; none when switched off. */
    private void refreshRifts() {
        for (MarkerOverlay marker : riftMarkers) api.remove(marker);
        riftMarkers.clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (!Config.mapRifts() || minecraft.level == null) return;
        for (FluxRiftClientCache.Entry rift : FluxRiftClientCache.visible()) {
            if (rift.closing()) continue;
            Component label = Component.translatable(rift.stabilised() ? "gui.quantimium.map_rifts.held" : "gui.quantimium.map_rifts.wild",
                    rift.stage());
            MapImage icon = new MapImage(RIFT_MARKER, 16, 16).centerAnchors();
            MarkerOverlay marker = new MarkerOverlay(Quantimium.MODID, rift.anchor(), icon);
            marker.setDimension(minecraft.level.dimension());
            marker.setOverlayGroupName("Quantimium rifts").setLabel(label.getString()).setTitle(label.getString());
            try {
                api.show(marker);
                riftMarkers.add(marker);
            } catch (Exception e) {
                Quantimium.LOGGER.warn("JourneyMap refused a rift marker", e);
                return;
            }
        }
    }

    /** Lit for the field or load, dark when off, with the mode in its tooltip. Set, not flipped, whatever JourneyMap did first. */
    private static void show(IThemeButton button, Config.MapOverlay overlay) {
        button.setToggled(overlay != Config.MapOverlay.OFF);
        button.setTooltip(FieldMapClient.modeName(overlay).getString());
    }

    /** Brings the overlays in line with the field: only chunks that appeared, went or changed colour. */
    private void refresh() {
        ResourceKey<Level> dimension = FieldMapClient.dimension();
        if (dimension == null || !dimension.equals(shownIn)) {
            for (Shown old : shown.values()) api.remove(old.overlay());
            shown.clear();
            shownIn = dimension;
            if (dimension == null) return;
        }
        Map<Long, FieldMapClient.Cell> cells = FieldMapClient.cells();
        Iterator<Map.Entry<Long, Shown>> iterator = shown.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Shown> entry = iterator.next();
            if (!cells.containsKey(entry.getKey())) {
                api.remove(entry.getValue().overlay());
                iterator.remove();
            }
        }
        for (Map.Entry<Long, FieldMapClient.Cell> entry : cells.entrySet()) {
            FieldMapClient.Cell cell = entry.getValue();
            Shown current = shown.get(entry.getKey());
            if (current != null && current.colour() == cell.colour()) {
                current.overlay().setTitle(FieldMapClient.describe(cell).getString());
                continue;
            }
            if (current != null) api.remove(current.overlay());
            PolygonOverlay overlay = overlay(dimension, ChunkPos.unpack(entry.getKey()), cell);
            try {
                api.show(overlay);
                shown.put(entry.getKey(), new Shown(overlay, cell.colour()));
            } catch (Exception e) {
                Quantimium.LOGGER.warn("JourneyMap refused a field overlay", e);
                return;
            }
        }
    }

    private static PolygonOverlay overlay(ResourceKey<Level> dimension, ChunkPos pos, FieldMapClient.Cell cell) {
        int x = pos.getMinBlockX();
        int z = pos.getMinBlockZ();
        MapPolygon square = new MapPolygon(new BlockPos(x, 64, z), new BlockPos(x, 64, z + 16),
                new BlockPos(x + 16, 64, z + 16), new BlockPos(x + 16, 64, z));
        ShapeProperties shape = new ShapeProperties()
                .setFillColor(cell.colour() & 0xFFFFFF)
                .setFillOpacity(((cell.colour() >>> 24) & 0xFF) / 255.0f)
                .setStrokeOpacity(0.0f)
                .setStrokeWidth(0.0f);
        PolygonOverlay overlay = new PolygonOverlay(Quantimium.MODID, dimension, shape, square);
        overlay.setOverlayGroupName("Quantimium field").setTitle(FieldMapClient.describe(cell).getString());
        return overlay;
    }
}

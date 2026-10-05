package com.kadikular.quantimium.client;

import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.network.FieldMapPayload;
import com.kadikular.quantimium.network.FieldMapRequestPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The field as world maps draw it (compat.map: JourneyMap). With one
 * installed, the client asks the server for the field round the player and keeps what it has seen,
 * with the colour each chunk is drawn in ({@link Config#mapOverlay}).
 */
@EventBusSubscriber(modid = Quantimium.MODID, value = Dist.CLIENT)
public final class FieldMapClient {

    public record Cell(float flux, float anomaly, float load, boolean contained, int colour) {}

    /** A chunk's field as the server last sent it. */
    private record Seen(float flux, float anomaly, float load, boolean contained) {}

    /**
     * Every chunk seen holding a field this session, by dimension. A chunk that unloads keeps what it
     * showed last: the server freezes an unloaded chunk's field, so it's still there, as a map keeps
     * the terrain you explored. Only a chunk this client has loaded is cleared when it isn't sent.
     */
    private static final Map<ResourceKey<Level>, Map<Long, Seen>> SEEN = new HashMap<>();
    /** What is drawn in the current dimension. */
    private static final Map<Long, Cell> CELLS = new HashMap<>();
    private static final List<Runnable> LISTENERS = new ArrayList<>();

    /** Cycles what world maps show: field, load, nothing. Unbound until a player chooses a key. */
    public static final KeyMapping CYCLE = new KeyMapping("key.quantimium.map_overlay", KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN, SuperpositionKeys.CATEGORY);

    @Nullable
    private static ResourceKey<Level> dimension;

    private FieldMapClient() {}

    /** Whether a world map that can show the field is installed. */
    public static boolean mapInstalled() {
        return ModList.get().isLoaded("journeymap");
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (mapInstalled()) ClientPacketDistributor.sendToServer(new FieldMapRequestPayload(true));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SEEN.clear();
        CELLS.clear();
        dimension = null;
        LISTENERS.forEach(Runnable::run);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (CYCLE.consumeClick()) {
            Minecraft.getInstance().gui.setOverlayMessage(modeName(cycle()), false);
        }
    }

    /** Moves world maps on to the next of field, load and off, and returns it. */
    public static Config.MapOverlay cycle() {
        Config.MapOverlay next = Config.MapOverlay.values()[(Config.mapOverlay().ordinal() + 1) % Config.MapOverlay.values().length];
        setOverlay(next);
        return next;
    }

    /** "World map: containment load", for {@code overlay}. */
    public static Component modeName(Config.MapOverlay overlay) {
        return Component.translatable("gui.quantimium.map_overlay." + overlay.name().toLowerCase(Locale.ROOT));
    }

    /** Switches what world maps show, keeps it in the config, and redraws at once. */
    public static void setOverlay(Config.MapOverlay overlay) {
        Config.MAP_OVERLAY.set(overlay);
        Config.MAP_OVERLAY.save();
        redraw();
    }

    public static void handle(FieldMapPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        dimension = level.dimension();
        Map<Long, Seen> seen = SEEN.computeIfAbsent(dimension, d -> new HashMap<>());
        // Loaded here but not sent: it has no field any more. Not loaded: keep what it showed last.
        seen.keySet().removeIf(key -> level.getChunkSource().hasChunk(ChunkPos.getX(key), ChunkPos.getZ(key)));
        for (int i = 0; i < payload.chunks().length; i++) {
            seen.put(payload.chunks()[i], new Seen(payload.flux()[i], payload.anomaly()[i], payload.load()[i], payload.contained()[i]));
        }
        redraw();
    }

    /** Colours what has been seen in the current dimension for the current overlay, and tells the maps. */
    private static void redraw() {
        CELLS.clear();
        Map<Long, Seen> seen = dimension == null ? Map.of() : SEEN.getOrDefault(dimension, Map.of());
        Config.MapOverlay overlay = Config.mapOverlay();
        for (Map.Entry<Long, Seen> entry : seen.entrySet()) {
            Seen field = entry.getValue();
            int colour = switch (overlay) {
                case FIELD -> FieldMapRenderer.mapColour(field.flux(), field.anomaly());
                case LOAD -> FieldMapRenderer.loadColour(field.load(), field.contained());
                case OFF -> 0;
            };
            if (colour == 0) continue;
            long key = entry.getKey();
            CELLS.put(key, new Cell(field.flux(), field.anomaly(), field.load(), field.contained(), colour));
        }
        LISTENERS.forEach(Runnable::run);
    }

    /** Called whenever the field changes, for maps that keep their own shapes. */
    public static void listen(Runnable listener) {
        LISTENERS.add(listener);
    }

    /** What the map draws over chunk ({@code x}, {@code z}) in {@code level}, or null for nothing. */
    @Nullable
    public static Cell cell(ResourceKey<Level> level, int x, int z) {
        return level.equals(dimension) ? CELLS.get(ChunkPos.pack(x, z)) : null;
    }

    public static Map<Long, Cell> cells() {
        return CELLS;
    }

    @Nullable
    public static ResourceKey<Level> dimension() {
        return dimension;
    }

    /** One line for a map's tooltip: "Flux 2,870 High · Anomaly 40 Low · Load 62%". */
    public static Component describe(Cell cell) {
        Component line = Component.translatable("gui.quantimium.field_map.chunk",
                String.format("%,.0f", cell.flux()), band(cell.flux()),
                String.format("%,.0f", cell.anomaly()), band(cell.anomaly()));
        if (cell.load() < 0.0f) return line;
        return Component.translatable("gui.quantimium.field_map.chunk_load", line,
                String.format("%.0f%%", cell.load() * 100.0f));
    }

    private static Component band(float value) {
        return Component.translatable("flux.quantimium.band." + FluxBand.of(value).getSerializedName());
    }
}

package com.kadikular.quantimium.superposition;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the pods holding a player's doubles loaded while that player is online, so their modules work
 * however far away they are (the {@code chunkLoadPods} config option). Nothing is kept loaded for
 * anyone offline, and nothing survives a restart: the tickets are worked out afresh every few seconds.
 */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class PodChunkLoading {

    private static final int INTERVAL = 100;

    /** Every ticket is dropped on load: who is online, and where their doubles are, is decided afresh. */
    public static final TicketController CONTROLLER = new TicketController(
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "superposition_pods"),
            (level, tickets) -> tickets.getEntityTickets().keySet().forEach(tickets::removeAllTickets));

    private record Chunk(ResourceKey<Level> dimension, ChunkPos pos) {}

    private static final Map<UUID, Set<Chunk>> HELD = new HashMap<>();

    private PodChunkLoading() {}

    /** Registered on the mod bus, from {@link Quantimium}. */
    public static void register(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % INTERVAL != 0) return;
        SophonRegistry registry = SophonRegistry.get(server);
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            Set<Chunk> wanted = new HashSet<>();
            if (Config.chunkLoadPods()) {
                for (SophonRegistry.Sophon sophon : registry.of(player.getUUID())) {
                    sophon.entry().pod().ifPresent(pod -> wanted.add(chunkOf(pod)));
                }
            }
            hold(server, player.getUUID(), wanted);
        }
        for (UUID gone : Set.copyOf(HELD.keySet())) {
            if (!online.contains(gone)) hold(server, gone, Set.of());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) hold(player.level().getServer(), player.getUUID(), Set.of());
    }

    private static Chunk chunkOf(GlobalPos pos) {
        return new Chunk(pos.dimension(), ChunkPos.containing(pos.pos()));
    }

    /** Brings the chunks held for {@code owner} to {@code wanted}. */
    private static void hold(MinecraftServer server, UUID owner, Set<Chunk> wanted) {
        Set<Chunk> held = HELD.getOrDefault(owner, Set.of());
        for (Chunk chunk : held) {
            if (!wanted.contains(chunk)) force(server, owner, chunk, false);
        }
        for (Chunk chunk : wanted) {
            if (!held.contains(chunk)) force(server, owner, chunk, true);
        }
        if (wanted.isEmpty()) {
            HELD.remove(owner);
        } else {
            HELD.put(owner, new HashSet<>(wanted));
        }
    }

    private static void force(MinecraftServer server, UUID owner, Chunk chunk, boolean add) {
        ServerLevel level = server.getLevel(chunk.dimension());
        if (level != null) CONTROLLER.forceChunk(level, owner, chunk.pos().x(), chunk.pos().z(), add, true);
    }
}

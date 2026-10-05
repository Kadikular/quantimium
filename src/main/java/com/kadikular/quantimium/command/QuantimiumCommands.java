package com.kadikular.quantimium.command;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.entity.Veiled;
import com.kadikular.quantimium.flux.ChunkFlux;
import com.kadikular.quantimium.init.ModAttachments;
import com.kadikular.quantimium.init.ModEntities;
import com.kadikular.quantimium.phase.FluxRift;
import com.kadikular.quantimium.phase.FluxRiftManager;
import com.kadikular.quantimium.phase.MirrorPhase;
import com.kadikular.quantimium.phase.VeiledManager;
import com.kadikular.quantimium.phase.WorldRiftManager;
import com.kadikular.quantimium.superposition.SophonRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Operator-only development controls. Survival entry is the crafted stabilised portal. */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class QuantimiumCommands {

    private QuantimiumCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("quantimium")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("phase")
                        .then(action("toggle", Action.TOGGLE))
                        .then(action("on", Action.ON))
                        .then(action("off", Action.OFF)))
                .then(Commands.literal("portal")
                        .then(Commands.argument("duration", IntegerArgumentType.integer(1, 3600))
                                .executes(context -> spawnPortal(context, null))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(context -> spawnPortal(context,
                                                BlockPosArgument.getBlockPos(context, "pos"))))))
                .then(Commands.literal("rift")
                        .then(Commands.literal("spawn")
                                .executes(context -> spawnRift(context, 1, null))
                                .then(Commands.argument("stage", IntegerArgumentType.integer(1, FluxRift.MAX_STAGE))
                                        .executes(context -> spawnRift(context,
                                                IntegerArgumentType.getInteger(context, "stage"), null))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> spawnRift(context,
                                                        IntegerArgumentType.getInteger(context, "stage"),
                                                        BlockPosArgument.getBlockPos(context, "pos"))))))
                        .then(Commands.literal("stage")
                                .then(Commands.argument("stage", IntegerArgumentType.integer(1, FluxRift.MAX_STAGE))
                                        .executes(QuantimiumCommands::setRiftStage)))
                        .then(Commands.literal("clear")
                                .executes(QuantimiumCommands::clearRifts)))
                .then(Commands.literal("veiled")
                        .then(Commands.literal("sighting").executes(QuantimiumCommands::forceSighting))
                        .then(Commands.literal("info").executes(QuantimiumCommands::veiledInfo)))
                .then(Commands.literal("field")
                        .then(Commands.literal("clear").executes(QuantimiumCommands::clearField)))
                .then(Commands.literal("sophons")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("list").executes(QuantimiumCommands::listSophons))
                                .then(Commands.literal("forget").executes(QuantimiumCommands::forgetSophons)))));
    }

    private static int spawnRift(CommandContext<CommandSourceStack> context, int stage, BlockPos explicit) {
        CommandSourceStack source = context.getSource();
        BlockPos pos = explicit != null ? explicit : BlockPos.containing(source.getPosition());
        FluxRiftManager.spawn(source.getLevel(), pos, stage);
        source.sendSuccess(() -> Component.translatable("message.quantimium.rift.spawned",
                stage, pos.getX(), pos.getY(), pos.getZ()), true);
        return 1;
    }

    private static int setRiftStage(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        FluxRift rift = FluxRiftManager.nearest(source.getLevel(), source.getPosition(), 64.0);
        if (rift == null) {
            source.sendFailure(Component.translatable("message.quantimium.rift.none_near"));
            return 0;
        }
        int stage = IntegerArgumentType.getInteger(context, "stage");
        FluxRiftManager.setStage(source.getLevel(), rift, stage);
        source.sendSuccess(() -> Component.translatable("message.quantimium.rift.staged", stage), true);
        return 1;
    }

    /** Where each of a player's Sophons is: folded, or the pod its double waits in. */
    private static int listSophons(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        var sophons = SophonRegistry.get(context.getSource().getServer()).of(player.getUUID());
        context.getSource().sendSuccess(() -> Component.translatable("message.quantimium.sophons.count",
                player.getGameProfile().name(), sophons.size(), Config.maxDoubles()), false);
        for (SophonRegistry.Sophon sophon : sophons) {
            SophonRegistry.Entry entry = sophon.entry();
            String kind = entry.inField() ? "message.quantimium.sophons.in_field"
                    : entry.dropped() ? "message.quantimium.sophons.dropped"
                    : entry.taken() ? "message.quantimium.sophons.taken" : "message.quantimium.sophons.in_pod";
            Component line = entry.pod()
                    .map(pod -> Component.translatable(kind, entry.podName(),
                            pod.dimension().identifier().toString(), pod.pos().getX(), pod.pos().getY(), pod.pos().getZ()))
                    .orElseGet(() -> Component.translatable("message.quantimium.sophons.folded"));
            context.getSource().sendSuccess(() -> line, false);
        }
        return sophons.size();
    }

    /**
     * Forgets a player's Sophons, for one lost where nothing noticed (cleared from an inventory by a
     * command, say), which would otherwise count against their cap for good. Their doubles stay in their
     * pods but no longer lead anywhere; fold them to have them back as items.
     */
    private static int forgetSophons(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        int forgotten = SophonRegistry.get(context.getSource().getServer()).forget(player.getUUID());
        context.getSource().sendSuccess(() -> Component.translatable("message.quantimium.sophons.forgotten",
                forgotten, player.getGameProfile().name()), true);
        return forgotten;
    }

    private static int forceSighting(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!VeiledManager.forceSighting(player)) {
            context.getSource().sendFailure(Component.translatable("message.quantimium.veiled.none"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("message.quantimium.veiled.sighting"), false);
        return 1;
    }

    /** Empties flux, anomaly and pending emission from the loaded chunks within 8 of the source. */
    private static int clearField(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        ChunkPos centre = ChunkPos.containing(BlockPos.containing(context.getSource().getPosition()));
        int cleared = 0;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(centre.x() + dx, centre.z() + dz);
                if (chunk == null) continue;
                ChunkFlux data = chunk.getExistingData(ModAttachments.CHUNK_FLUX).orElse(null);
                if (data == null) continue;
                data.clear();
                chunk.markUnsaved();
                cleared++;
            }
        }
        int count = cleared;
        context.getSource().sendSuccess(() -> Component.translatable("message.quantimium.field.cleared", count), true);
        return count;
    }

    private static int veiledInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        var near = source.getLevel().getEntities(ModEntities.VEILED.get(),
                new AABB(BlockPos.containing(source.getPosition())).inflate(Veiled.AREA),
                entity -> true);
        if (near.isEmpty()) {
            source.sendFailure(Component.translatable("message.quantimium.veiled.none"));
            return 0;
        }
        Veiled veiled = near.get(0);
        int distance = (int) Math.round(veiled.position().distanceTo(source.getPosition()));
        source.sendSuccess(() -> Component.translatable("message.quantimium.veiled.info",
                veiled.state().name().toLowerCase(Locale.ROOT), distance,
                veiled.onCooldown() ? "yes" : "no"), false);
        return 1;
    }

    private static int clearRifts(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int cleared = FluxRiftManager.clear(source.getLevel());
        source.sendSuccess(() -> Component.translatable("message.quantimium.rift.cleared", cleared), true);
        return cleared;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> action(
            String name, Action action) {
        return Commands.literal(name)
                .executes(context -> run(context, action, List.of(context.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> run(context, action, EntityArgument.getPlayers(context, "targets"))));
    }

    private static int run(CommandContext<CommandSourceStack> context, Action action,
                           Collection<ServerPlayer> players) {
        int changed = 0;
        for (ServerPlayer player : players) {
            boolean result = switch (action) {
                case ON -> MirrorPhase.enter(player);
                case OFF -> MirrorPhase.exit(player, MirrorPhase.ExitReason.MANUAL);
                case TOGGLE -> MirrorPhase.toggle(player);
            };
            if (result) changed++;
        }
        int count = changed;
        context.getSource().sendSuccess(
                () -> Component.literal("Mirror phase updated for " + count + " player(s)."), true);
        return changed;
    }

    private static int spawnPortal(CommandContext<CommandSourceStack> context, BlockPos explicit) {
        CommandSourceStack source = context.getSource();
        int seconds = IntegerArgumentType.getInteger(context, "duration");
        BlockPos pos = explicit != null ? explicit : BlockPos.containing(source.getPosition());
        Direction facing = source.getEntity() instanceof ServerPlayer player
                ? player.getDirection()
                : Direction.NORTH;
        WorldRiftManager.spawnEntry(source.getLevel(), pos, facing, seconds * 20);
        source.sendSuccess(() -> Component.translatable(
                "message.quantimium.portal.spawned", seconds, pos.getX(), pos.getY(), pos.getZ()), true);
        return 1;
    }

    private enum Action {
        ON, OFF, TOGGLE
    }
}

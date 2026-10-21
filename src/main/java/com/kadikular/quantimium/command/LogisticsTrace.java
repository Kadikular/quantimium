package com.kadikular.quantimium.command;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.LogisticsTerminalBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
import com.kadikular.quantimium.laser.Logistics;
import com.kadikular.quantimium.laser.LogisticsController;
import com.kadikular.quantimium.laser.LogisticsMember;
import com.kadikular.quantimium.recipe.IngredientPool;
import com.kadikular.quantimium.recipe.ResolvedCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /quantimium trace: a take at the nearest Logistics Terminal, as a player clicking it would, written down step by step
 * (what the network lists, what each machine it says makes it shows, every craft tried, made or refused and why, and what
 * changed on the network) to a file under logs/, for working out why a craft is refused.
 */
public final class LogisticsTrace {
    private LogisticsTrace() {}

    public static int run(ServerPlayer player, Item item, int count) {
        ServerLevel level = player.level();
        LogisticsTerminalBlockEntity terminal = nearestTerminal(level, player.blockPosition());
        if (terminal == null || terminal.controller() == null) {
            player.sendSystemMessage(Component.literal("No Logistics Terminal joined to a network within 8 blocks."));
            return 0;
        }
        LogisticsController controller = terminal.controller();
        ItemResource resource = ItemResource.of(new ItemStack(item));
        List<String> lines = new ArrayList<>();
        lines.add("Quantimium trace, " + LocalDateTime.now() + ": take " + count + " " + BuiltInRegistries.ITEM.getKey(item)
                + " at the terminal at " + terminal.getBlockPos().toShortString());

        // What the network says.
        var offers = controller.offers();
        lines.add("");
        lines.add("The network lists: " + offers.real(resource) + " stored, " + offers.craftable(resource) + " craftable");
        lines.add("Members: " + controller.members().size() + "; machines it says make it: " + offers.makers(resource).size());
        for (BlockPos maker : offers.makers(resource)) {
            lines.add("  maker at " + maker.toShortString() + ": " + describeMaker(level, maker, item));
        }

        // Where a take would look.
        lines.add("");
        lines.add("Where things are (spots) for it:");
        var spots = controller.spots().get(resource);
        if (spots == null || spots.isEmpty()) lines.add("  none");
        else for (Logistics.Spot spot : spots) lines.add("  " + spot.member().inventoryPos().toShortString() + " slot " + spot.slot()
                + " (" + spot.member().role() + (spot.member().maker() != null ? ", a maker" : "") + ")");

        // The take, step by step.
        Map<ItemResource, Long> before = new HashMap<>(controller.offers().stored());
        lines.add("");
        lines.add("The take:");
        var priorTracing = QuantumCrafterBlockEntity.tracing;
        List<String> steps = new ArrayList<>();
        QuantumCrafterBlockEntity.tracing = step -> {
            if (steps.size() < 2000) steps.add(step);
        };
        ItemStack got;
        long began = System.nanoTime();
        try {
            got = terminal.take(resource, count);
        } finally {
            QuantimiumTraceGuard.restore(priorTracing);
        }
        long took = System.nanoTime() - began;
        for (String step : steps) lines.add("  " + step);
        if (steps.size() >= 2000) lines.add("  (and more: cut at 2000 steps)");
        int gotCount = got.getCount();
        lines.add("Got " + gotCount + " of " + count + " in " + String.format("%.1f", took / 1e6) + " ms");
        if (!got.isEmpty() && !player.getInventory().add(got)) player.drop(got, false);

        // What it changed.
        Map<ItemResource, Long> after = controller.offers().stored();
        lines.add("");
        lines.add("Stored on the network, before and after:");
        Map<ItemResource, Long> changes = new HashMap<>();
        before.forEach((k, v) -> changes.merge(k, -v, Long::sum));
        after.forEach((k, v) -> changes.merge(k, v, Long::sum));
        changes.values().removeIf(v -> v == 0);
        changes.entrySet().stream().sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(r -> BuiltInRegistries.ITEM.getKey(r.getItem()).toString())))
                .forEach(e -> lines.add("  " + (e.getValue() > 0 ? "+" : "") + e.getValue() + " " + BuiltInRegistries.ITEM.getKey(e.getKey().getItem())));
        if (changes.isEmpty()) lines.add("  nothing (what's shown stored updates within a tick; run it again to see)");

        Path file = level.getServer().getServerDirectory().resolve("logs")
                .resolve("quantimium-trace-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines);
        } catch (IOException failed) {
            Quantimium.LOGGER.error("Couldn't write the trace", failed);
        }
        int crafts = (int) steps.stream().filter(s -> s.trim().startsWith("try ")).count();
        int refusals = (int) steps.stream().filter(s -> s.contains("refused at")).count();
        player.sendSystemMessage(Component.literal("Trace: got " + gotCount + "/" + count + "; " + crafts + " crafts tried, " + refusals
                + " refusals. Written to logs/" + file.getFileName()));
        return gotCount;
    }

    /** What a maker shows of {@code item}: its crafts of it, their batches, and what they take. */
    private static String describeMaker(ServerLevel level, BlockPos pos, Item item) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof QuantumCrafterBlockEntity crafter)) return entity == null ? "nothing there" : entity.getClass().getSimpleName();
        List<String> crafts = new ArrayList<>();
        for (ResolvedCraft craft : crafter.getPreview()) {
            boolean makes = false;
            for (ItemStack output : craft.outputs()) if (output.is(item)) makes = true;
            if (!makes) continue;
            List<String> takes = new ArrayList<>();
            for (IngredientPool.Withdrawal withdrawal : craft.withdrawals()) {
                if (withdrawal.count() <= 0) continue;
                takes.add(withdrawal.count() + " " + (withdrawal.expected() == null ? "?" : BuiltInRegistries.ITEM.getKey(withdrawal.expected())));
            }
            crafts.add(craft.recipeId() + " x" + craft.batchSize() + " (runs available " + craft.availableRuns() + "; takes " + String.join(", ", takes) + ")");
        }
        return "a Crafter, status " + crafter.getStatusCode() + ", energy " + crafter.getEnergyStorage().getEnergyStored() + ", preview of it: "
                + (crafts.isEmpty() ? "none" : String.join(" | ", crafts));
    }

    private static LogisticsTerminalBlockEntity nearestTerminal(ServerLevel level, BlockPos around) {
        LogisticsTerminalBlockEntity best = null;
        double nearest = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(around.offset(-8, -8, -8), around.offset(8, 8, 8))) {
            if (level.getBlockEntity(pos) instanceof LogisticsTerminalBlockEntity terminal) {
                double distance = pos.distSqr(around);
                if (distance < nearest) {
                    nearest = distance;
                    best = terminal;
                }
            }
        }
        return best;
    }

    /** Puts back whoever was listening before the trace. */
    private static final class QuantimiumTraceGuard {
        static void restore(java.util.function.Consumer<String> prior) {
            QuantumCrafterBlockEntity.tracing = prior;
        }
    }
}

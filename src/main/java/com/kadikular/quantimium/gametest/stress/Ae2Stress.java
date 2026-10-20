package com.kadikular.quantimium.gametest.stress;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import com.kadikular.quantimium.block.LogisticsControllerBlock;
import com.kadikular.quantimium.block.entity.LogisticsControllerBlockEntity;
import com.kadikular.quantimium.block.entity.LogisticsMaterialiserBlockEntity;
import com.kadikular.quantimium.block.entity.LogisticsReceptacleBlockEntity;
import com.kadikular.quantimium.block.entity.LogisticsTerminalBlockEntity;
import com.kadikular.quantimium.gametest.GameTest;
import com.kadikular.quantimium.gametest.TestSupport;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.laser.LaserTuning;
import com.kadikular.quantimium.laser.LogisticsMember;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.kadikular.quantimium.gametest.stress.LogisticsStress.WANTED;
import static com.kadikular.quantimium.gametest.stress.LogisticsStress.Y;
import static com.kadikular.quantimium.gametest.stress.LogisticsStress.Z;

/**
 * A busy logistics network as AE2 storage: a storage bus on its Materialiser is the ME network's only storage, an
 * import bus feeds it from a chest, and at random the ME network takes (made or not, for real or simulated) and puts
 * back, while the network's own providers, requesters, Crafters and terminal carry on. Every item counted.
 */
public final class Ae2Stress {
    private Ae2Stress() {}

    @GameTest(template = TestSupport.FLOOR_17, batch = "stress_ae2", timeoutTicks = 40_000, padding = 64)
    public static void ae2OnABusyNetwork(GameTestHelper helper) {
        String test = "ae2OnABusyNetwork";
        Random random = Stress.random(test);
        TestSupport.clearField(helper);
        LogisticsStress.Net net = new LogisticsStress.Net(helper, random);
        List<Item> filler = LogisticsStress.filler();
        int stores = Stress.scaled(12), makers = Stress.scaled(4);
        net.materialiser = new BlockPos(net.x + stores + 4 + makers + 1, Y, Z);
        BlockPos controllerAt = new BlockPos(2, Y, Z);
        helper.setBlock(controllerAt, ModBlocks.LOGISTICS_CONTROLLER.get().defaultBlockState().setValue(LogisticsControllerBlock.FACING, Direction.EAST)
                .setValue(LogisticsControllerBlock.OUTPUTS.get(Direction.NORTH), false).setValue(LogisticsControllerBlock.OUTPUTS.get(Direction.EAST), true));
        net.controller = helper.getBlockEntity(controllerAt, LogisticsControllerBlockEntity.class);
        net.controller.toggleOutput(Direction.NORTH);
        helper.setBlock(controllerAt.north(), ModBlocks.LOGISTICS_TERMINAL.get());
        net.terminal = helper.getBlockEntity(controllerAt.north(), LogisticsTerminalBlockEntity.class);
        for (int i = 0; i < stores; i++) {
            List<ItemStack> stock = new ArrayList<>();
            for (int s = 0; s < 16; s++) stock.add(new ItemStack(filler.get(random.nextInt(filler.size())), 1 + random.nextInt(64)));
            for (int s = 0; s < 4; s++) stock.add(LogisticsStress.worth(random));
            net.chest(LogisticsMember.Role.STORAGE, false, stock);
        }
        for (int i = 0; i < 2; i++) {
            List<ItemStack> stock = new ArrayList<>();
            for (int s = 0; s < 8; s++) stock.add(LogisticsStress.worth(random));
            net.chest(LogisticsMember.Role.PROVIDER, true, stock);
        }
        for (int i = 0; i < 2; i++) {
            LogisticsReceptacleBlockEntity requester = net.chest(LogisticsMember.Role.REQUESTER, true, List.of());
            requester.setFilterSlot(0, new ItemStack(WANTED[random.nextInt(WANTED.length)], 16 + random.nextInt(48)));
            requester.toggleKeep();
            requester.toggleRequestsMade();
        }
        for (int i = 0; i < makers; i++) net.crafter(i % 2 == 1, LogisticsStress.worth(random));
        while (net.x < net.materialiser.getX()) net.chest(LogisticsMember.Role.STORAGE, false, List.of());
        helper.setBlock(net.materialiser, ModBlocks.LOGISTICS_MATERIALISER.get());

        // AE2, east of the Materialiser: a storage bus on it, an import bus under a chest, power.
        BlockPos busAt = net.materialiser.east(), importAt = busAt.east(), cellAt = importAt.east();
        var level = helper.getLevel();
        IPartItem<?> cable = (IPartItem<?>) item("ae2:fluix_glass_cable");
        PartHelper.setPart(level, helper.absolutePos(busAt), null, null, cable);
        IPart bus = PartHelper.setPart(level, helper.absolutePos(busAt), Direction.WEST, null, (IPartItem<?>) item("ae2:storage_bus"));
        PartHelper.setPart(level, helper.absolutePos(importAt), null, null, cable);
        PartHelper.setPart(level, helper.absolutePos(importAt), Direction.UP, null, (IPartItem<?>) item("ae2:import_bus"));
        helper.setBlock(importAt.above(), Blocks.CHEST);
        ChestBlockEntity feed = helper.getBlockEntity(importAt.above(), ChestBlockEntity.class);
        helper.setBlock(cellAt, BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        Stress.report(test, "built: " + net.receptacles.size() + " receptacles, " + net.crafters.size() + " crafters, a storage bus and an import bus");

        Map<Item, Long> pile = new HashMap<>();
        BlockPos boxFrom = new BlockPos(0, 0, Z - 3), boxTo = new BlockPos(cellAt.getX() + 1, Y + 2, Z + 3);
        List<String> findings = new ArrayList<>();
        @SuppressWarnings("unchecked") Map<Item, Long>[] before = new Map[1];
        int[] t = {0};
        boolean[] done = {false};
        int settle = 100, length = Stress.scaled(1200);
        IActionSource source = IActionSource.empty();
        Stress.begin();
        helper.onEachTick(() -> {
            if (done[0]) return;
            t[0]++;
            net.controller.getEnergyStorage().setEnergy(LaserTuning.LOGISTICS_CAPACITY);
            if (t[0] < settle) return;
            var node = bus == null ? null : bus.getGridNode();
            if (node == null || !node.isActive()) {
                if (t[0] > settle + 200) {
                    done[0] = true;
                    helper.fail("the ME network never came up");
                }
                return;
            }
            IGrid grid = node.getGrid();
            if (before[0] == null) {
                before[0] = Stress.harness(() -> Stress.census(helper, boxFrom, boxTo, List.of(), pile));
                return;
            }
            // The import bus's chest, topped up from what's been taken now and then.
            if (t[0] % 20 == 0) Stress.harness(() -> {
                for (int i = 0; i < 4; i++) {
                    ItemStack stack = LogisticsStress.fromPile(pile, random);
                    if (stack.isEmpty()) break;
                    boolean placed = false;
                    for (int slot = 0; slot < feed.getContainerSize() && !placed; slot++) {
                        if (feed.getItem(slot).isEmpty()) {
                            feed.setItem(slot, stack);
                            placed = true;
                        }
                    }
                    if (!placed) pile.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
                }
                return null;
            });
            int ops = 1 + random.nextInt(4);
            for (int i = 0; i < ops; i++) {
                try {
                    act(net, grid, source, random, pile);
                } catch (RuntimeException failed) {
                    findings.add("tick " + t[0] + ": " + failed + " at " + (failed.getStackTrace().length > 0 ? failed.getStackTrace()[0] : "?")
                            + "\n    recent:\n    " + Stress.recent());
                    com.kadikular.quantimium.Quantimium.LOGGER.error("[stress] {} failed", test, failed);
                }
            }
            // What the ME network shows, as a terminal on it would read it every tick.
            Stress.time("me listing", "", () -> grid.getStorageService().getCachedInventory().size());
            Map<Item, Long> now = Stress.harness(() -> Stress.census(helper, boxFrom, boxTo, List.of(), pile));
            String wrong = Stress.harness(() -> Stress.compare(before[0], now));
            if (wrong != null) {
                findings.add("tick " + t[0] + ": items changed: " + wrong + "\n    recent:\n    " + Stress.recent());
                before[0] = now;
            }
            if (t[0] >= settle + length || findings.size() >= 10) {
                done[0] = true;
                Stress.end(test, findings);
                TestSupport.clearField(helper);
                if (findings.isEmpty()) helper.succeed();
                else helper.fail(findings.size() + " findings, first: " + findings.getFirst().lines().findFirst().orElse(""));
            }
        });
    }

    private static void act(LogisticsStress.Net net, IGrid grid, IActionSource source, Random random, Map<Item, Long> pile) {
        var storage = grid.getStorageService().getInventory();
        int roll = random.nextInt(100);
        if (roll < 35) {
            // An ME terminal or export bus taking something worth crafting: made by our Crafters if it must be.
            Item item = WANTED[random.nextInt(WANTED.length)];
            long amount = 1 + random.nextInt(random.nextBoolean() ? 64 : 256);
            boolean simulate = random.nextInt(4) == 0;
            long got = Stress.time("me extract", item + " x" + amount + (simulate ? " (simulated)" : ""),
                    () -> storage.extract(AEItemKey.of(item), amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE, source));
            if (!simulate && got > 0) pile.merge(item, got, Long::sum);
        } else if (roll < 50) {
            // Something stored taken through ME, only as much as is stored (what's made of filler isn't counted).
            var materialiser = net.helper.getBlockEntity(net.materialiser, LogisticsMaterialiserBlockEntity.class);
            List<ItemResource> stored = new ArrayList<>(net.controller.offers().stored().keySet());
            if (stored.isEmpty()) return;
            ItemResource resource = stored.get(random.nextInt(stored.size()));
            long amount = Math.min(64, net.controller.offers().real(resource));
            if (amount <= 0 || materialiser == null) return;
            while (materialiser.mode() != LogisticsMaterialiserBlockEntity.Mode.REAL) materialiser.cycleMode();
            long got;
            try {
                got = Stress.time("me extract stored", resource.getItem() + " x" + amount,
                        () -> storage.extract(AEItemKey.of(resource.toStack(1)), amount, Actionable.MODULATE, source));
            } finally {
                while (materialiser.mode() != LogisticsMaterialiserBlockEntity.Mode.BOTH) materialiser.cycleMode();
            }
            if (got > 0) pile.merge(resource.getItem(), got, Long::sum);
        } else if (roll < 70) {
            ItemStack stack = LogisticsStress.fromPile(pile, random);
            if (stack.isEmpty()) return;
            long in = Stress.time("me insert", stack.toString(), () -> storage.insert(AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE, source));
            pile.merge(stack.getItem(), stack.getCount() - in, Long::sum);
        } else if (roll < 85) {
            Item item = WANTED[random.nextInt(WANTED.length)];
            int count = 1 + random.nextInt(128);
            ItemStack got = Stress.time("terminal take", item + " x" + count, () -> net.terminal.take(ItemResource.of(new ItemStack(item)), count));
            if (!got.isEmpty()) pile.merge(got.getItem(), (long) got.getCount(), Long::sum);
        } else {
            ItemStack stack = LogisticsStress.fromPile(pile, random);
            if (stack.isEmpty()) return;
            ItemStack left = Stress.time("terminal insert", stack.toString(), () -> net.terminal.insert(stack));
            pile.merge(stack.getItem(), (long) left.getCount(), Long::sum);
        }
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }
}

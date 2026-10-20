package com.kadikular.quantimium.gametest.stress;

import com.kadikular.quantimium.block.LaserMirrorBlock;
import com.kadikular.quantimium.block.LogisticsControllerBlock;
import com.kadikular.quantimium.block.LogisticsReceptacleBlock;
import com.kadikular.quantimium.block.entity.LogisticsControllerBlockEntity;
import com.kadikular.quantimium.block.entity.LogisticsMaterialiserBlockEntity;
import com.kadikular.quantimium.block.entity.LogisticsReceptacleBlockEntity;
import com.kadikular.quantimium.block.entity.LogisticsTerminalBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
import com.kadikular.quantimium.gametest.CrafterTests;
import com.kadikular.quantimium.gametest.GameTest;
import com.kadikular.quantimium.gametest.TestSupport;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.laser.LaserTuning;
import com.kadikular.quantimium.laser.LogisticsMember;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Two networks, things going wrong with them all the time. Network A runs east along a row of chests, turns south at
 * a Laser Mirror into a second row and ends on its Materialiser, which two Crafters draw on. Network B, beside it,
 * ends on a Materialiser that one of A's Receptacles provides from: A sees B's stock and what B's machines make.
 * Meanwhile, at random: the mirror switched off and on with redstone (cutting A's second row, Materialiser and all,
 * off and back), a stretch of A's first row (eight chests and their Receptacles) torn out and put back, B's
 * Materialiser broken and put back, each controller's power full, drained, cut or browning out; and a player taking,
 * putting back and pulling through A's Materialiser. Every item counted.
 */
public final class ChaosStress {
    private ChaosStress() {}

    private static final int Y = 2;
    private static final Item[] WANTED = Stress.WORTH.keySet().toArray(Item[]::new);

    private enum Power { FULL, DRAINED, CUT, BROWNOUT }

    @GameTest(template = TestSupport.FLOOR_17, batch = "stress_chaos", timeoutTicks = 40_000, padding = 48)
    public static void chaos(GameTestHelper helper) {
        String test = "chaos";
        Random random = Stress.random(test);
        TestSupport.clearField(helper);

        // Network A: controller at (2, Y, 8) shining east, a terminal north of it.
        BlockPos controllerA = new BlockPos(2, Y, 8);
        placeController(helper, controllerA, Direction.EAST, true);
        LogisticsControllerBlockEntity a = helper.getBlockEntity(controllerA, LogisticsControllerBlockEntity.class);
        helper.setBlock(controllerA.north(), ModBlocks.LOGISTICS_TERMINAL.get());
        LogisticsTerminalBlockEntity terminal = helper.getBlockEntity(controllerA.north(), LogisticsTerminalBlockEntity.class);
        int firstRow = 20, cutFrom = 6, cutTo = 13, mirrorX = 4 + firstRow, secondRow = 12;
        List<int[]> torn = new ArrayList<>();
        for (int x = 4; x < 4 + firstRow; x++) {
            if (x == 4 + firstRow - 2) {
                // B's Materialiser, under one of A's Receptacles set to provide.
                placeReceptacle(helper, new BlockPos(x, Y, 8), Direction.DOWN, LogisticsMember.Role.PROVIDER);
                continue;
            }
            chestUnder(helper, new BlockPos(x, Y, 8), random, LogisticsMember.Role.STORAGE);
            if (x >= cutFrom && x <= cutTo) torn.add(new int[] {x});
        }
        // The mirror turns A's light south, down the second row, to A's Materialiser.
        BlockPos mirror = new BlockPos(mirrorX, Y, 8);
        helper.setBlock(mirror, ModBlocks.LASER_MIRROR.get().defaultBlockState().setValue(LaserMirrorBlock.MOUNT, Direction.DOWN)
                .setValue(LaserMirrorBlock.INPUT, Direction.WEST).setValue(LaserMirrorBlock.OUTPUT, Direction.SOUTH));
        for (int z = 9; z < 9 + secondRow; z++) chestUnder(helper, new BlockPos(mirrorX, Y, z), random, LogisticsMember.Role.STORAGE);
        BlockPos materialiserA = new BlockPos(mirrorX, Y, 9 + secondRow);
        List<QuantumCrafterBlockEntity> crafters = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            QuantumCrafterBlockEntity crafter = CrafterTests.crafter(helper, new BlockPos(mirrorX + 2 + i, Y, 9 + secondRow), Items.CRAFTING_TABLE);
            crafter.getInventory().setStackInSlot(0, CrafterTests.link(helper, materialiserA, ModBlocks.LOGISTICS_MATERIALISER.get()));
            crafters.add(crafter);
        }
        helper.setBlock(materialiserA, ModBlocks.LOGISTICS_MATERIALISER.get());

        // Network B: at y = Y - 1, shining north along x = mirrorX - 2 from z = 2 to its Materialiser under A's provider.
        int bx = 4 + firstRow - 2;
        BlockPos controllerB = new BlockPos(bx, Y - 1, 2);
        placeController(helper, controllerB, Direction.SOUTH, true);
        LogisticsControllerBlockEntity b = helper.getBlockEntity(controllerB, LogisticsControllerBlockEntity.class);
        for (int z = 3; z < 8; z++) {
            BlockPos at = new BlockPos(bx, Y - 1, z);
            helper.setBlock(at.below(), Blocks.CHEST);
            fill(helper.getBlockEntity(at.below(), ChestBlockEntity.class), random);
            placeReceptacle(helper, at, Direction.DOWN, LogisticsMember.Role.STORAGE);
        }
        BlockPos materialiserB = new BlockPos(bx, Y - 1, 8);
        helper.setBlock(materialiserB, ModBlocks.LOGISTICS_MATERIALISER.get());

        Map<Item, Long> pile = new HashMap<>();
        BlockPos boxFrom = new BlockPos(0, 0, 0), boxTo = new BlockPos(mirrorX + 4, Y + 2, 9 + secondRow + 1);
        List<String> findings = new ArrayList<>();
        @SuppressWarnings("unchecked") Map<Item, Long>[] before = new Map[1];
        int[] t = {0};
        boolean[] done = {false}, mirrorOff = {false}, tornOut = {false}, bGone = {false};
        Power[] power = {Power.FULL, Power.FULL};
        int settle = 60, length = Stress.scaled(1500);
        Stress.begin();
        helper.onEachTick(() -> {
            if (done[0]) return;
            t[0]++;
            if (t[0] < settle) {
                a.getEnergyStorage().setEnergy(LaserTuning.LOGISTICS_CAPACITY);
                b.getEnergyStorage().setEnergy(LaserTuning.LOGISTICS_CAPACITY);
                return;
            }
            if (t[0] == settle) {
                before[0] = Stress.harness(() -> Stress.census(helper, boxFrom, boxTo, List.of(), pile));
                return;
            }
            // Power: now and then a change of state; each tick, what that state does.
            for (int n = 0; n < 2; n++) {
                if (random.nextInt(80) == 0) {
                    power[n] = Power.values()[random.nextInt(Power.values().length)];
                    Stress.note("network " + (n == 0 ? "A" : "B") + " power " + power[n]);
                }
                LogisticsControllerBlockEntity controller = n == 0 ? a : b;
                switch (power[n]) {
                    case FULL -> controller.getEnergyStorage().setEnergy(LaserTuning.LOGISTICS_CAPACITY);
                    case DRAINED -> controller.getEnergyStorage().setEnergy(0);
                    case CUT -> { }
                    case BROWNOUT -> controller.getEnergyStorage().setEnergy(random.nextInt((int) LaserTuning.LOGISTICS_FE_PER_MOVE * 3));
                }
            }
            // The mirror, switched with redstone.
            if (random.nextInt(60) == 0) {
                mirrorOff[0] = !mirrorOff[0];
                Stress.time("mirror " + (mirrorOff[0] ? "off" : "on"), "", () -> {
                    helper.setBlock(mirror.above(), mirrorOff[0] ? Blocks.REDSTONE_BLOCK : Blocks.AIR);
                    return null;
                });
            }
            // A stretch of A's chests torn out, and put back empty.
            if (random.nextInt(90) == 0) {
                tornOut[0] = !tornOut[0];
                Stress.time(tornOut[0] ? "row torn out" : "row put back", torn.size() + " chests", () -> {
                    for (int[] at : torn) {
                        BlockPos pos = new BlockPos(at[0], Y, 8);
                        if (tornOut[0]) {
                            helper.getLevel().destroyBlock(helper.absolutePos(pos.below()), true);
                            helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
                        } else {
                            helper.setBlock(pos.below(), Blocks.CHEST);
                            placeReceptacle(helper, pos, Direction.DOWN, LogisticsMember.Role.STORAGE);
                        }
                    }
                    return null;
                });
                // What fell out (contents, chests, Receptacles) is picked up, and the blocks that dropped themselves
                // don't count as new.
                if (tornOut[0]) Stress.harness(() -> {
                    pile.merge(Items.CHEST, (long) -torn.size(), Long::sum);
                    pile.merge(ModBlocks.LOGISTICS_RECEPTACLE.get().asItem(), (long) -torn.size(), Long::sum);
                    return null;
                });
            }
            // B's Materialiser broken, and put back.
            if (random.nextInt(70) == 0) {
                bGone[0] = !bGone[0];
                Stress.time(bGone[0] ? "B materialiser broken" : "B materialiser placed", "", () -> {
                    if (bGone[0]) helper.getLevel().destroyBlock(helper.absolutePos(materialiserB), true);
                    else helper.setBlock(materialiserB, ModBlocks.LOGISTICS_MATERIALISER.get());
                    return null;
                });
                if (bGone[0]) pile.merge(ModBlocks.LOGISTICS_MATERIALISER.get().asItem(), -1L, Long::sum);
            }
            // The player, and pipes through A's Materialiser.
            int ops = 1 + random.nextInt(4);
            for (int i = 0; i < ops; i++) {
                try {
                    act(helper, random, terminal, materialiserA, pile);
                } catch (RuntimeException failed) {
                    findings.add("tick " + t[0] + ": " + failed + " at " + (failed.getStackTrace().length > 0 ? failed.getStackTrace()[0] : "?")
                            + "\n    recent:\n    " + Stress.recent());
                    com.kadikular.quantimium.Quantimium.LOGGER.error("[stress] {} failed", test, failed);
                }
            }
            Stress.harness(() -> {
                BlockPos lo = helper.absolutePos(boxFrom), hi = helper.absolutePos(boxTo);
                for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(lo.getCenter(), hi.getCenter()).inflate(2))) {
                    pile.merge(drop.getItem().getItem(), (long) drop.getItem().getCount(), Long::sum);
                    drop.discard();
                }
                return null;
            });
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

    /**
     * Networks tangled together: A runs east along y = Y and ends on its Materialiser; B runs west above it, one of its
     * Receptacles on A's Materialiser, and ends on its own, which one of A's Receptacles sits under. So each draws on
     * the other, and A on itself too (a Receptacle on its own Materialiser). The cross Receptacles flip between
     * providing (reading the other side) and storage (putting into it); Crafters on both. Players take and put back at
     * both terminals, pipes pull through both Materialisers. Every item counted.
     */
    @GameTest(template = TestSupport.FLOOR_17, batch = "stress_tangled", timeoutTicks = 40_000, padding = 32)
    public static void tangled(GameTestHelper helper) {
        String test = "tangled";
        Random random = Stress.random(test);
        TestSupport.clearField(helper);
        int top = Y + 1, length = 14, xb = 6, xa = 4 + length;
        // A: controller (2, Y, 8) east, terminal north; chests under; at xb a Receptacle up into B's Materialiser; a
        // Receptacle on its own Materialiser just before it; the Materialiser at xa.
        BlockPos controllerA = new BlockPos(2, Y, 8);
        placeController(helper, controllerA, Direction.EAST, true);
        LogisticsControllerBlockEntity a = helper.getBlockEntity(controllerA, LogisticsControllerBlockEntity.class);
        helper.setBlock(controllerA.north(), ModBlocks.LOGISTICS_TERMINAL.get());
        LogisticsTerminalBlockEntity terminalA = helper.getBlockEntity(controllerA.north(), LogisticsTerminalBlockEntity.class);
        BlockPos materialiserA = new BlockPos(xa, Y, 8), materialiserB = new BlockPos(xb, top, 8);
        List<LogisticsReceptacleBlockEntity> cross = new ArrayList<>();
        for (int x = 4; x < xa; x++) {
            BlockPos at = new BlockPos(x, Y, 8);
            if (x == xb) {
                placeReceptacle(helper, at, Direction.UP, LogisticsMember.Role.PROVIDER);
                cross.add(helper.getBlockEntity(at, LogisticsReceptacleBlockEntity.class));
            } else if (x == xa - 1) {
                placeReceptacle(helper, at, Direction.EAST, LogisticsMember.Role.PROVIDER);
                cross.add(helper.getBlockEntity(at, LogisticsReceptacleBlockEntity.class));
            } else if (x == 10) {
                QuantumCrafterBlockEntity crafter = CrafterTests.crafter(helper, at.below(), Items.CRAFTING_TABLE);
                crafter.getInventory().setStackInSlot(0, CrafterTests.link(helper, materialiserA, ModBlocks.LOGISTICS_MATERIALISER.get()));
                placeReceptacle(helper, at, Direction.DOWN, LogisticsMember.Role.PROVIDER);
            } else {
                chestUnder(helper, at, random, LogisticsMember.Role.STORAGE);
            }
        }
        helper.setBlock(materialiserA, ModBlocks.LOGISTICS_MATERIALISER.get());
        // B: controller east of A's end, a row up, shining west; chests above its Receptacles; over A's Materialiser a
        // Receptacle down into it; its own Materialiser at xb.
        BlockPos controllerB = new BlockPos(xa + 2, top, 8);
        placeController(helper, controllerB, Direction.WEST, true);
        LogisticsControllerBlockEntity b = helper.getBlockEntity(controllerB, LogisticsControllerBlockEntity.class);
        helper.setBlock(controllerB.north(), ModBlocks.LOGISTICS_TERMINAL.get());
        LogisticsTerminalBlockEntity terminalB = helper.getBlockEntity(controllerB.north(), LogisticsTerminalBlockEntity.class);
        for (int x = xa + 1; x > xb; x--) {
            BlockPos at = new BlockPos(x, top, 8);
            if (x == xa) {
                placeReceptacle(helper, at, Direction.DOWN, LogisticsMember.Role.PROVIDER);
                cross.add(helper.getBlockEntity(at, LogisticsReceptacleBlockEntity.class));
            } else if (x == 12) {
                QuantumCrafterBlockEntity crafter = CrafterTests.crafter(helper, at.above(), Items.CRAFTING_TABLE);
                crafter.getInventory().setStackInSlot(0, CrafterTests.link(helper, materialiserB, ModBlocks.LOGISTICS_MATERIALISER.get()));
                placeReceptacle(helper, at, Direction.UP, LogisticsMember.Role.PROVIDER);
            } else {
                helper.setBlock(at.above(), Blocks.CHEST);
                fill(helper.getBlockEntity(at.above(), ChestBlockEntity.class), random);
                placeReceptacle(helper, at, Direction.UP, LogisticsMember.Role.STORAGE);
            }
        }
        helper.setBlock(materialiserB, ModBlocks.LOGISTICS_MATERIALISER.get());

        Map<Item, Long> pile = new HashMap<>();
        BlockPos boxFrom = new BlockPos(0, 0, 5), boxTo = new BlockPos(xa + 3, top + 2, 10);
        List<String> findings = new ArrayList<>();
        @SuppressWarnings("unchecked") Map<Item, Long>[] before = new Map[1];
        int[] t = {0};
        boolean[] done = {false};
        int settle = 80, runFor = Stress.scaled(1200);
        Stress.begin();
        helper.onEachTick(() -> {
            if (done[0]) return;
            t[0]++;
            a.getEnergyStorage().setEnergy(LaserTuning.LOGISTICS_CAPACITY);
            b.getEnergyStorage().setEnergy(LaserTuning.LOGISTICS_CAPACITY);
            if (t[0] < settle) return;
            if (t[0] == settle) {
                before[0] = Stress.harness(() -> Stress.census(helper, boxFrom, boxTo, List.of(), pile));
                // Tangled for real: each lists more than its own chests hold, the other's too.
                long aLists = a.offers().stored().values().stream().mapToLong(Long::longValue).sum();
                long bLists = b.offers().stored().values().stream().mapToLong(Long::longValue).sum();
                Stress.report(test, "A lists " + aLists + " stored, B " + bLists + "; A " + a.members().size() + " members, B " + b.members().size());
                return;
            }
            // The tangle's Receptacles flip between reading the other side and putting into it.
            if (random.nextInt(40) == 0) {
                LogisticsReceptacleBlockEntity flip = cross.get(random.nextInt(cross.size()));
                LogisticsMember.Role to = flip.role() == LogisticsMember.Role.PROVIDER ? LogisticsMember.Role.STORAGE : LogisticsMember.Role.PROVIDER;
                while (flip.role() != to) flip.cycleRole();
                Stress.note("the Receptacle at " + flip.getBlockPos().toShortString() + " now " + to);
            }
            int ops = 1 + random.nextInt(4);
            for (int i = 0; i < ops; i++) {
                boolean onA = random.nextBoolean();
                try {
                    act(helper, random, onA ? terminalA : terminalB, onA ? materialiserA : materialiserB, pile);
                } catch (RuntimeException | StackOverflowError failed) {
                    findings.add("tick " + t[0] + ": " + failed + " at " + (failed.getStackTrace().length > 0 ? failed.getStackTrace()[0] : "?")
                            + "\n    recent:\n    " + Stress.recent());
                    com.kadikular.quantimium.Quantimium.LOGGER.error("[stress] {} failed", test, failed);
                }
            }
            Map<Item, Long> now = Stress.harness(() -> Stress.census(helper, boxFrom, boxTo, List.of(), pile));
            String wrong = Stress.harness(() -> Stress.compare(before[0], now));
            if (wrong != null) {
                findings.add("tick " + t[0] + ": items changed: " + wrong + "\n    recent:\n    " + Stress.recent());
                before[0] = now;
            }
            if (t[0] >= settle + runFor || findings.size() >= 10) {
                done[0] = true;
                Stress.end(test, findings);
                TestSupport.clearField(helper);
                if (findings.isEmpty()) helper.succeed();
                else helper.fail(findings.size() + " findings, first: " + findings.getFirst().lines().findFirst().orElse(""));
            }
        });
    }

    private static void act(GameTestHelper helper, Random random, LogisticsTerminalBlockEntity terminal, BlockPos materialiser, Map<Item, Long> pile) {
        int roll = random.nextInt(100);
        if (roll < 45) {
            Item item = WANTED[random.nextInt(WANTED.length)];
            int count = 1 + random.nextInt(128);
            ItemStack got = Stress.time("terminal take", item + " x" + count, () -> terminal.take(ItemResource.of(new ItemStack(item)), count));
            if (!got.isEmpty()) pile.merge(got.getItem(), (long) got.getCount(), Long::sum);
        } else if (roll < 70) {
            ItemStack stack = fromPile(pile, random);
            if (stack.isEmpty()) return;
            ItemStack left = Stress.time("terminal insert", stack.toString(), () -> terminal.insert(stack));
            pile.merge(stack.getItem(), (long) left.getCount(), Long::sum);
        } else {
            // A pipe on A's Materialiser: worth items, made or not, committed or not.
            var handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(materialiser), Direction.UP);
            if (handler == null) return;
            Item item = WANTED[random.nextInt(WANTED.length)];
            ItemResource resource = ItemResource.of(new ItemStack(item));
            int index = -1;
            for (int i = 0; i < handler.size(); i++) if (handler.getResource(i).equals(resource)) index = i;
            if (index < 0) return;
            int amount = 1 + random.nextInt(128), at = index;
            boolean commit = random.nextBoolean();
            int got = Stress.time("materialiser extract", item + " x" + amount + (commit ? "" : " (aborted)"), () -> {
                try (Transaction transaction = Transaction.openRoot()) {
                    int moved = handler.extract(at, resource, amount, transaction);
                    if (commit) transaction.commit();
                    return commit ? moved : 0;
                }
            });
            if (got > 0) pile.merge(item, (long) got, Long::sum);
        }
    }

    private static void placeController(GameTestHelper helper, BlockPos pos, Direction facing, boolean terminalNorth) {
        helper.setBlock(pos, ModBlocks.LOGISTICS_CONTROLLER.get().defaultBlockState().setValue(LogisticsControllerBlock.FACING, facing)
                .setValue(LogisticsControllerBlock.OUTPUTS.get(Direction.NORTH), false).setValue(LogisticsControllerBlock.OUTPUTS.get(facing), true));
        LogisticsControllerBlockEntity controller = helper.getBlockEntity(pos, LogisticsControllerBlockEntity.class);
        controller.getEnergyStorage().setEnergy(LaserTuning.LOGISTICS_CAPACITY);
        if (terminalNorth && facing != Direction.NORTH && facing != Direction.SOUTH) controller.toggleOutput(Direction.NORTH);
    }

    private static void placeReceptacle(GameTestHelper helper, BlockPos pos, Direction facing, LogisticsMember.Role role) {
        helper.setBlock(pos, ModBlocks.LOGISTICS_RECEPTACLE.get().defaultBlockState().setValue(LogisticsReceptacleBlock.FACING, facing));
        LogisticsReceptacleBlockEntity receptacle = helper.getBlockEntity(pos, LogisticsReceptacleBlockEntity.class);
        while (receptacle.role() != role) receptacle.cycleRole();
    }

    private static void chestUnder(GameTestHelper helper, BlockPos pos, Random random, LogisticsMember.Role role) {
        helper.setBlock(pos.below(), Blocks.CHEST);
        fill(helper.getBlockEntity(pos.below(), ChestBlockEntity.class), random);
        placeReceptacle(helper, pos, Direction.DOWN, role);
    }

    private static void fill(ChestBlockEntity chest, Random random) {
        for (int i = 0; i < 6; i++) chest.setItem(i, new ItemStack(WANTED[random.nextInt(WANTED.length)], 1 + random.nextInt(64)));
    }

    private static ItemStack fromPile(Map<Item, Long> pile, Random random) {
        List<Item> have = pile.entrySet().stream().filter(e -> e.getValue() > 0 && Stress.WORTH.containsKey(e.getKey())).map(Map.Entry::getKey).toList();
        if (have.isEmpty()) return ItemStack.EMPTY;
        Item item = have.get(random.nextInt(have.size()));
        int count = (int) Math.min(pile.get(item), Math.min(new ItemStack(item).getMaxStackSize(), 1 + random.nextInt(64)));
        pile.merge(item, (long) -count, Long::sum);
        return new ItemStack(item, count);
    }
}

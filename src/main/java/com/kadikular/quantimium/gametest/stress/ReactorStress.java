package com.kadikular.quantimium.gametest.stress;

import com.kadikular.quantimium.block.LogisticsControllerBlock;
import com.kadikular.quantimium.block.LogisticsReceptacleBlock;
import com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity;
import com.kadikular.quantimium.block.entity.LogisticsControllerBlockEntity;
import com.kadikular.quantimium.block.entity.LogisticsReceptacleBlockEntity;
import com.kadikular.quantimium.block.entity.LogisticsTerminalBlockEntity;
import com.kadikular.quantimium.block.entity.ReactorLogisticsPortBlockEntity;
import com.kadikular.quantimium.gametest.CrafterTests;
import com.kadikular.quantimium.gametest.GameTest;
import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
import com.kadikular.quantimium.gametest.ReactorTests;
import com.kadikular.quantimium.gametest.TestSupport;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.laser.LaserTuning;
import com.kadikular.quantimium.laser.LogisticsMember;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A Reactor holding thousands of kinds of things, on a logistics network, pushed at random: pulled from through two
 * Materialiser Ports (alone and together in one transaction, committed or not), fed through its Input port, asked to
 * make things into its ledger, drawn on through the network's terminal, shared and unshared. Every item is counted
 * each tick, the ledger's too.
 */
public final class ReactorStress {
    private ReactorStress() {}

    private static final BlockPos CORE = ReactorTests.CORE;
    private static final BlockPos INPUT = CORE.below().north(5);
    private static final BlockPos LOGISTICS = CORE.below().east(5);
    private static final BlockPos MATERIALISER_A = CORE.below().west(5);
    private static final BlockPos MATERIALISER_B = CORE.below().south(5);
    private static final Item[] WANTED = Stress.WORTH.keySet().toArray(Item[]::new);

    @GameTest(template = TestSupport.FLOOR_17, batch = "stress_reactor", timeoutTicks = 20_000, padding = 16)
    public static void busyReactor(GameTestHelper helper) {
        run(helper, "busyReactor", true, false);
    }

    /**
     * As {@link #busyReactor}, with Crafters on the same network too: three crafting tables drawing on it (the Reactor's
     * stock among it) and making for it, and a requester always asking for made things. The Reactor and the Crafters
     * can make the same things from each other's: every item still counted.
     */
    @GameTest(template = TestSupport.FLOOR_17, batch = "stress_reactor_mixed", timeoutTicks = 20_000, padding = 16)
    public static void reactorAndCrafters(GameTestHelper helper) {
        run(helper, "reactorAndCrafters", true, true);
    }

    /**
     * As {@link #busyReactor}, with thousands of kinds any recipe may use: the Reactor crafts filler from filler as it
     * likes, so nothing's counted; only how long things take, and whether anything throws.
     */
    @GameTest(template = TestSupport.FLOOR_17, batch = "stress_reactor_big", timeoutTicks = 20_000, padding = 16)
    public static void bigReactor(GameTestHelper helper) {
        run(helper, "bigReactor", false, false);
    }

    private static void run(GameTestHelper helper, String test, boolean counted, boolean crafters) {
        Random random = Stress.random(test);
        TestSupport.clearField(helper);
        HorizonCoreBlockEntity core = ReactorTests.buildReactor(helper, 2);
        ReactorTests.bay(helper, CORE.below().north(2), Items.CRAFTING_TABLE);
        helper.setBlock(MATERIALISER_A, ModBlocks.REACTOR_MATERIALISER_PORT.get());
        helper.setBlock(MATERIALISER_B, ModBlocks.REACTOR_MATERIALISER_PORT.get());
        helper.setBlock(LOGISTICS, ModBlocks.REACTOR_LOGISTICS_PORT.get());
        core.revalidate(helper.getLevel());
        core.getEnergyStorage().setEnergy(HorizonCoreBlockEntity.ENERGY_CAPACITY);

        // The network: a chest of stock on a storage receptacle, the controller shining west onto the port, a terminal north.
        BlockPos receptacleAt = LOGISTICS.east(2), chestAt = receptacleAt.above(), controllerAt = LOGISTICS.east(3);
        helper.setBlock(chestAt, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(chestAt, ChestBlockEntity.class);
        for (int i = 0; i < 27; i++) chest.setItem(i, new ItemStack(WANTED[random.nextInt(WANTED.length)], 1 + random.nextInt(64)));
        helper.setBlock(receptacleAt, ModBlocks.LOGISTICS_RECEPTACLE.get().defaultBlockState().setValue(LogisticsReceptacleBlock.FACING, Direction.UP));
        helper.setBlock(controllerAt, ModBlocks.LOGISTICS_CONTROLLER.get().defaultBlockState().setValue(LogisticsControllerBlock.FACING, Direction.WEST)
                .setValue(LogisticsControllerBlock.OUTPUTS.get(Direction.NORTH), false).setValue(LogisticsControllerBlock.OUTPUTS.get(Direction.WEST), true));
        LogisticsControllerBlockEntity controller = helper.getBlockEntity(controllerAt, LogisticsControllerBlockEntity.class);
        controller.toggleOutput(Direction.NORTH);
        helper.setBlock(controllerAt.north(), ModBlocks.LOGISTICS_TERMINAL.get());
        LogisticsTerminalBlockEntity terminal = helper.getBlockEntity(controllerAt.north(), LogisticsTerminalBlockEntity.class);
        ReactorLogisticsPortBlockEntity logisticsPort = helper.getBlockEntity(LOGISTICS, ReactorLogisticsPortBlockEntity.class);
        if (crafters) {
            // South of the controller: three Crafters on provider Receptacles, a requester's chest, and the Materialiser
            // the Crafters draw on through their Tesseracts.
            controller.toggleOutput(Direction.SOUTH);
            BlockPos materialiser = controllerAt.south(6);
            for (int i = 1; i <= 3; i++) {
                QuantumCrafterBlockEntity crafter = CrafterTests.crafter(helper, controllerAt.south(i).below(), Items.CRAFTING_TABLE);
                crafter.getInventory().setStackInSlot(0, CrafterTests.link(helper, materialiser, ModBlocks.LOGISTICS_MATERIALISER.get()));
                receptacle(helper, controllerAt.south(i), LogisticsMember.Role.PROVIDER);
            }
            helper.setBlock(controllerAt.south(4).below(), Blocks.CHEST);
            LogisticsReceptacleBlockEntity requester = receptacle(helper, controllerAt.south(4), LogisticsMember.Role.REQUESTER);
            requester.setFilterSlot(0, new ItemStack(Items.IRON_NUGGET, 64));
            requester.setFilterSlot(1, new ItemStack(Items.GOLD_BLOCK, 4));
            requester.setFilterSlot(2, new ItemStack(Items.STICK, 32));
            requester.toggleKeep();
            requester.toggleRequestsMade();
            requester.toggleActive();
            receptacle(helper, controllerAt.south(5), LogisticsMember.Role.STORAGE);
            helper.setBlock(controllerAt.south(5).below(), Blocks.CHEST);
            helper.setBlock(materialiser, ModBlocks.LOGISTICS_MATERIALISER.get());
        }

        // Thousands of kinds in the ledger, and things worth crafting with.
        List<Item> filler = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(item).getPath();
            if (item == Items.AIR || Stress.WORTH.containsKey(item)) continue;
            if (id.matches(".*(log|wood|bamboo|stem|hyphae|plank|iron|gold|nugget|ingot|stick|raw_|spawn_egg|command|debug|structure|jigsaw|barrier|tesseract|singularity).*")) continue;
            filler.add(item);
        }
        // Only what no recipe touches: whatever the Reactor makes, from whatever, these keep their counts.
        core.recountNow();
        if (counted) filler.removeIf(item -> !core.getRecipes().graph().inert(ItemResource.of(item)));
        java.util.Collections.shuffle(filler, random);
        int kinds = Math.min(filler.size(), Stress.scaled(3000));
        for (int i = 0; i < kinds; i++) core.take(ItemResource.of(filler.get(i)), 1 + random.nextInt(200));
        for (Item item : WANTED) core.take(ItemResource.of(item), 100 + random.nextInt(2000));
        List<Item> held = filler.subList(0, kinds);
        Stress.report(test, "ledger: " + core.getLedger().entries().size() + " kinds, " + core.getLedger().mass() + " items");

        Map<Item, Long> pile = new HashMap<>();
        BlockPos boxFrom = new BlockPos(0, 0, 0), boxTo = new BlockPos(17, 6, 17);
        List<String> findings = new ArrayList<>();
        @SuppressWarnings("unchecked") Map<Item, Long>[] before = new Map[1];
        int[] t = {0};
        boolean[] done = {false};
        int settle = 40, length = Stress.scaled(800);

        Stress.begin();
        helper.onEachTick(() -> {
            if (done[0]) return;
            t[0]++;
            core.getEnergyStorage().setEnergy(HorizonCoreBlockEntity.ENERGY_CAPACITY);
            controller.getEnergyStorage().setEnergy(LaserTuning.LOGISTICS_CAPACITY);
            if (t[0] < settle) return;
            if (t[0] == settle) {
                before[0] = Stress.census(helper, boxFrom, boxTo, List.of(), pile);
                return;
            }
            int ops = random.nextInt(20) == 0 ? 30 : 1 + random.nextInt(4);
            for (int i = 0; i < ops; i++) {
                try {
                    act(helper, random, core, terminal, logisticsPort, held, pile);
                } catch (RuntimeException failed) {
                    findings.add("tick " + t[0] + ": " + failed + " at " + (failed.getStackTrace().length > 0 ? failed.getStackTrace()[0] : "?")
                            + "\n    recent:\n    " + Stress.recent());
                    com.kadikular.quantimium.Quantimium.LOGGER.error("[stress] {} failed", test, failed);
                }
            }
            Map<Item, Long> now = Stress.harness(() -> Stress.census(helper, boxFrom, boxTo, List.of(), pile));
            String wrong = counted ? Stress.harness(() -> Stress.compare(before[0], now)) : null;
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

    private static void act(GameTestHelper helper, Random random, HorizonCoreBlockEntity core, LogisticsTerminalBlockEntity terminal,
                            ReactorLogisticsPortBlockEntity logisticsPort, List<Item> held, Map<Item, Long> pile) {
        int roll = random.nextInt(100);
        ResourceHandler<ItemResource> a = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(MATERIALISER_A), null);
        ResourceHandler<ItemResource> b = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(MATERIALISER_B), null);
        if (roll < 30) {
            // A pipe pulling through a Materialiser Port (or both at once, in one transaction), committed or not.
            if (a == null || b == null) return;
            boolean worth = random.nextBoolean();
            Item item = worth ? WANTED[random.nextInt(WANTED.length)] : held.get(random.nextInt(held.size()));
            ItemResource resource = ItemResource.of(item);
            int amount = 1 + random.nextInt(random.nextBoolean() ? 64 : 512);
            // Filler only as far as it's there: the Reactor making it from other filler isn't counted by worth.
            if (!worth) amount = (int) Math.min(amount, core.getLedger().count(resource));
            if (amount <= 0) return;
            boolean commit = random.nextInt(3) != 0, both = random.nextBoolean();
            int asked = amount;
            int got = Stress.time("port extract", item + " x" + asked + (both ? " both" : "") + (commit ? "" : " (aborted)"), () -> {
                try (Transaction transaction = Transaction.openRoot()) {
                    int out = a.extract(resource, asked, transaction);
                    if (both) out += b.extract(resource, asked, transaction);
                    if (commit) transaction.commit();
                    return commit ? out : 0;
                }
            });
            if (got > 0) pile.merge(item, (long) got, Long::sum);
        } else if (roll < 45) {
            // Fed back through the Input port.
            var input = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(INPUT), null);
            ItemStack stack = fromPile(pile, random);
            if (input == null || stack.isEmpty()) return;
            boolean commit = random.nextInt(4) != 0;
            int in = Stress.time("port insert", stack + (commit ? "" : " (aborted)"), () -> {
                try (Transaction transaction = Transaction.openRoot()) {
                    int put = input.insert(ItemResource.of(stack), stack.getCount(), transaction);
                    if (commit) transaction.commit();
                    return commit ? put : 0;
                }
            });
            pile.merge(stack.getItem(), (long) stack.getCount() - in, Long::sum);
        } else if (roll < 60) {
            // Asked to make something into its ledger.
            Item item = WANTED[random.nextInt(WANTED.length)];
            long count = 1 + random.nextInt(256);
            Stress.time("request", item + " x" + count, () -> core.request(ItemResource.of(item), count));
        } else if (roll < 80) {
            // Taken at the network's terminal, drawing on the Reactor.
            Item item = WANTED[random.nextInt(WANTED.length)];
            int count = 1 + random.nextInt(128);
            ItemStack got = Stress.time("terminal take", item + " x" + count, () -> terminal.take(ItemResource.of(new ItemStack(item)), count));
            if (!got.isEmpty()) pile.merge(got.getItem(), (long) got.getCount(), Long::sum);
        } else if (roll < 88) {
            ItemStack stack = fromPile(pile, random);
            if (stack.isEmpty()) return;
            ItemStack left = Stress.time("terminal insert", stack.toString(), () -> terminal.insert(stack));
            pile.merge(stack.getItem(), (long) left.getCount(), Long::sum);
        } else if (roll < 92) {
            logisticsPort.toggleSharing();
            Stress.note("sharing toggled: " + (logisticsPort.network() != null));
        } else {
            // A burst of new kinds through the Input port: recounts.
            var input = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(INPUT), null);
            if (input == null) return;
            Stress.time("input burst", "", () -> {
                for (int i = 0; i < 16; i++) {
                    ItemStack stack = fromPile(pile, random);
                    if (stack.isEmpty()) break;
                    try (Transaction transaction = Transaction.openRoot()) {
                        int put = input.insert(ItemResource.of(stack), stack.getCount(), transaction);
                        transaction.commit();
                        pile.merge(stack.getItem(), (long) stack.getCount() - put, Long::sum);
                    }
                }
                return null;
            });
        }
    }

    private static LogisticsReceptacleBlockEntity receptacle(GameTestHelper helper, BlockPos pos, LogisticsMember.Role role) {
        helper.setBlock(pos, ModBlocks.LOGISTICS_RECEPTACLE.get().defaultBlockState().setValue(LogisticsReceptacleBlock.FACING, Direction.DOWN));
        LogisticsReceptacleBlockEntity receptacle = helper.getBlockEntity(pos, LogisticsReceptacleBlockEntity.class);
        while (receptacle.role() != role) receptacle.cycleRole();
        return receptacle;
    }

    private static ItemStack fromPile(Map<Item, Long> pile, Random random) {
        List<Item> have = pile.entrySet().stream().filter(e -> e.getValue() > 0).map(Map.Entry::getKey).toList();
        if (have.isEmpty()) return ItemStack.EMPTY;
        Item item = have.get(random.nextInt(have.size()));
        int count = (int) Math.min(pile.get(item), Math.min(new ItemStack(item).getMaxStackSize(), 1 + random.nextInt(64)));
        pile.merge(item, (long) -count, Long::sum);
        return new ItemStack(item, count);
    }
}

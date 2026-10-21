package com.kadikular.quantimium.gametest.stress;

import com.kadikular.quantimium.block.LogisticsControllerBlock;
import com.kadikular.quantimium.block.LogisticsReceptacleBlock;
import com.kadikular.quantimium.block.entity.LogisticsControllerBlockEntity;
import com.kadikular.quantimium.block.entity.LogisticsReceptacleBlockEntity;
import com.kadikular.quantimium.block.entity.LogisticsTerminalBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
import com.kadikular.quantimium.gametest.CrafterTests;
import com.kadikular.quantimium.gametest.GameTest;
import com.kadikular.quantimium.gametest.TestSupport;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.laser.LaserTuning;
import com.kadikular.quantimium.laser.LogisticsMember;
import com.kadikular.quantimium.menu.QuantumCrafterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
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
 * A big, busy logistics network, pushed hard at random: thousands of items of a thousand kinds in storage, providers
 * pushing and requesters pulling every tick, hoppers filling and draining chests, Crafters drawing on it and making
 * for it, and on top of that a player taking, putting back, clicking Crafters, piping things out through the
 * Materialiser in transactions that commit or abort, flipping modes, breaking chests. Every tick every item in the
 * area is counted: what's crafted keeps its worth ({@link Stress#WORTH}), everything else its count.
 */
public final class LogisticsStress {
    private LogisticsStress() {}

    static final int Y = 2;
    static final int Z = 8;

    /** The items worth counting by worth, which the player asks for. */
    static final Item[] WANTED = Stress.WORTH.keySet().toArray(Item[]::new);

    /** The network as built: where things are. */
    static final class Net {
        final GameTestHelper helper;
        final Random random;
        LogisticsControllerBlockEntity controller;
        LogisticsTerminalBlockEntity terminal;
        BlockPos materialiser;
        final List<LogisticsReceptacleBlockEntity> receptacles = new ArrayList<>();
        final List<BlockPos> storage = new ArrayList<>();
        final List<QuantumCrafterBlockEntity> crafters = new ArrayList<>();
        /** The Crafters' own receptacles: never set pushing (they'd make whatever they can, worth counted or not). */
        final List<LogisticsReceptacleBlockEntity> makers = new ArrayList<>();
        /** Hopper loops: [storage chest's x]; source chest at (x, Y, Z+1), sink at (x, Y-2, Z+1). */
        final List<Integer> churn = new ArrayList<>();
        int x = 4;

        Net(GameTestHelper helper, Random random) {
            this.helper = helper;
            this.random = random;
        }

        LogisticsReceptacleBlockEntity chest(LogisticsMember.Role role, boolean active, List<ItemStack> stock) {
            BlockPos chest = new BlockPos(x, Y - 1, Z);
            helper.setBlock(chest, Blocks.CHEST);
            ChestBlockEntity box = helper.getBlockEntity(chest, ChestBlockEntity.class);
            for (int i = 0; i < Math.min(27, stock.size()); i++) box.setItem(i, stock.get(i));
            LogisticsReceptacleBlockEntity receptacle = receptacle(role, active);
            if (role == LogisticsMember.Role.STORAGE) storage.add(chest);
            x++;
            return receptacle;
        }

        private LogisticsReceptacleBlockEntity receptacle(LogisticsMember.Role role, boolean active) {
            BlockPos pos = new BlockPos(x, Y, Z);
            helper.setBlock(pos, ModBlocks.LOGISTICS_RECEPTACLE.get().defaultBlockState().setValue(LogisticsReceptacleBlock.FACING, Direction.DOWN));
            LogisticsReceptacleBlockEntity receptacle = helper.getBlockEntity(pos, LogisticsReceptacleBlockEntity.class);
            while (receptacle.role() != role) receptacle.cycleRole();
            if (active) receptacle.toggleActive();
            receptacles.add(receptacle);
            return receptacle;
        }

        QuantumCrafterBlockEntity crafter(boolean basic, ItemStack... grid) {
            BlockPos at = new BlockPos(x, Y - 1, Z);
            QuantumCrafterBlockEntity crafter = basic ? CrafterTests.basic(helper, at, Items.CRAFTING_TABLE) : CrafterTests.crafter(helper, at, Items.CRAFTING_TABLE);
            crafter.getInventory().setStackInSlot(0, CrafterTests.link(helper, materialiser, ModBlocks.LOGISTICS_MATERIALISER.get()));
            for (int i = 0; i < grid.length; i++) crafter.getInventory().setStackInSlot(1 + i, grid[i]);
            makers.add(receptacle(LogisticsMember.Role.PROVIDER, false));
            crafters.add(crafter);
            x++;
            return crafter;
        }

        /** A storage chest that hoppers fill from a chest beside it and drain into one below. */
        void churning(List<ItemStack> stock, List<ItemStack> feed) {
            int at = x;
            chest(LogisticsMember.Role.STORAGE, false, stock);
            helper.setBlock(new BlockPos(at, Y, Z + 1), Blocks.CHEST);
            ChestBlockEntity source = helper.getBlockEntity(new BlockPos(at, Y, Z + 1), ChestBlockEntity.class);
            for (int i = 0; i < Math.min(27, feed.size()); i++) source.setItem(i, feed.get(i));
            helper.setBlock(new BlockPos(at, Y - 1, Z + 1), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH));
            helper.setBlock(new BlockPos(at, Y - 2, Z), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.SOUTH));
            helper.setBlock(new BlockPos(at, Y - 2, Z + 1), Blocks.CHEST);
            churn.add(at);
        }
    }


    /** Items that crafting among {@link Stress#WORTH} never touches: stock that only moves. */
    static List<Item> filler() {
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(item).getPath();
            if (item == Items.AIR || Stress.WORTH.containsKey(item)) continue;
            if (id.matches(".*(log|wood|bamboo|stem|hyphae|plank|iron|gold|nugget|ingot|stick|raw_|shulker|bundle|spawn_egg|command|debug|structure|jigsaw|barrier|light|tesseract).*")) continue;
            if (new ItemStack(item).getMaxStackSize() < 16) continue;
            items.add(item);
        }
        return items;
    }

    static ItemStack worth(Random random) {
        Item item = WANTED[random.nextInt(WANTED.length)];
        return new ItemStack(item, 1 + random.nextInt(64));
    }

    @GameTest(template = TestSupport.FLOOR_17, batch = "stress_logistics_busy", timeoutTicks = 40_000, padding = 112)
    public static void busyNetwork(GameTestHelper helper) {
        String test = "busyNetwork";
        Random random = Stress.random(test);
        TestSupport.clearField(helper);
        Net net = new Net(helper, random);
        List<Item> filler = filler();
        int stores = Stress.scaled(20), providers = Stress.scaled(3), requesters = Stress.scaled(3), churns = Stress.scaled(3), makers = Stress.scaled(6);
        // The Materialiser ends the row; Crafters' Tesseracts are bound to it before it's placed.
        net.materialiser = new BlockPos(net.x + stores + providers + requesters + churns + makers + 2, Y, Z);
        Stress.forceLoad(helper, new BlockPos(0, 0, Z - 3), net.materialiser.offset(2, 0, 3));
        BlockPos MATERIALISER_PLACEHOLDER = net.materialiser;

        // The controller, shining east along the row, and north to the terminal.
        BlockPos controllerAt = new BlockPos(2, Y, Z);
        helper.setBlock(controllerAt, ModBlocks.LOGISTICS_CONTROLLER.get().defaultBlockState().setValue(LogisticsControllerBlock.FACING, Direction.EAST)
                .setValue(LogisticsControllerBlock.OUTPUTS.get(Direction.NORTH), false).setValue(LogisticsControllerBlock.OUTPUTS.get(Direction.EAST), true));
        net.controller = helper.getBlockEntity(controllerAt, LogisticsControllerBlockEntity.class);
        net.controller.toggleOutput(Direction.NORTH);
        helper.setBlock(controllerAt.north(), ModBlocks.LOGISTICS_TERMINAL.get());
        net.terminal = helper.getBlockEntity(controllerAt.north(), LogisticsTerminalBlockEntity.class);

        // Storage: a thousand-odd kinds of filler, and things worth crafting with, scattered.
        int kinds = 0;
        for (int i = 0; i < stores; i++) {
            List<ItemStack> stock = new ArrayList<>();
            for (int s = 0; s < 24; s++) {
                stock.add(new ItemStack(filler.get(random.nextInt(filler.size())), 1 + random.nextInt(64)));
                kinds++;
            }
            for (int s = 0; s < 3; s++) stock.add(worth(random));
            net.chest(LogisticsMember.Role.STORAGE, false, stock);
        }
        // Providers pushing, requesters pulling (some asking for what's made), all the time.
        for (int i = 0; i < providers; i++) {
            List<ItemStack> stock = new ArrayList<>();
            for (int s = 0; s < 10; s++) stock.add(worth(random));
            net.chest(LogisticsMember.Role.PROVIDER, true, stock);
        }
        for (int i = 0; i < requesters; i++) {
            LogisticsReceptacleBlockEntity requester = net.chest(LogisticsMember.Role.REQUESTER, true, List.of());
            requester.setFilterSlot(0, new ItemStack(WANTED[random.nextInt(WANTED.length)], 1 + random.nextInt(64)));
            requester.setFilterSlot(1, new ItemStack(WANTED[random.nextInt(WANTED.length)], 1 + random.nextInt(64)));
            if (random.nextBoolean()) requester.toggleKeep();
            if (random.nextBoolean()) requester.toggleRequestsMade();
        }
        // Hoppers churning stock in and out.
        for (int i = 0; i < churns; i++) {
            List<ItemStack> stock = new ArrayList<>(), feed = new ArrayList<>();
            for (int s = 0; s < 8; s++) stock.add(new ItemStack(filler.get(random.nextInt(filler.size())), 64));
            for (int s = 0; s < 27; s++) feed.add(s % 3 == 0 ? worth(random) : new ItemStack(filler.get(random.nextInt(filler.size())), 64));
            net.churning(stock, feed);
        }
        // Crafters: crafting tables drawing on the network and making for it; a few basic, a few with a grid of their own.
        for (int i = 0; i < makers; i++) {
            boolean basic = i % 3 == 2;
            if (random.nextBoolean()) net.crafter(basic, worth(random), worth(random));
            else net.crafter(basic);
        }
        if (net.x >= MATERIALISER_PLACEHOLDER.getX()) {
            helper.fail("the row reached the Materialiser's place: " + net.x);
            return;
        }
        // Relays (bare receptacles set to nothing much) up to the Materialiser, which ends the row.
        while (net.x < MATERIALISER_PLACEHOLDER.getX()) {
            net.chest(LogisticsMember.Role.STORAGE, false, List.of());
        }
        helper.setBlock(MATERIALISER_PLACEHOLDER, ModBlocks.LOGISTICS_MATERIALISER.get());
        Stress.report(test, "built: " + net.receptacles.size() + " receptacles, " + kinds + " filler stacks, " + net.crafters.size() + " crafters, "
                + net.churn.size() + " hopper loops");

        ServerPlayer player = TestSupport.player(helper, new BlockPos(8, Y, 4), GameType.SURVIVAL);
        Map<Item, Long> pile = new HashMap<>();
        BlockPos boxFrom = new BlockPos(0, 0, Z - 3), boxTo = new BlockPos(MATERIALISER_PLACEHOLDER.getX() + 1, Y + 1, Z + 3);
        List<String> findings = new ArrayList<>();
        Map<Item, Long>[] before = new Map[1];
        int[] t = {0};
        int settle = 60, length = Stress.scaled(1200);
        QuantumCrafterBlockEntity[] open = {null};

        Stress.begin();
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) return;
            t[0]++;
            net.controller.getEnergyStorage().setEnergy(LaserTuning.LOGISTICS_CAPACITY);
            if (t[0] < settle) return;
            if (t[0] == settle) {
                before[0] = Stress.census(helper, boxFrom, boxTo, List.of(player), pile);
                return;
            }
            // Hopper loops: what drained goes back to be fed in again.
            if (t[0] % 40 == 0) Stress.harness(() -> {
                for (int at : net.churn) {
                    ChestBlockEntity sink = helper.getBlockEntity(new BlockPos(at, Y - 2, Z + 1), ChestBlockEntity.class);
                    ChestBlockEntity source = helper.getBlockEntity(new BlockPos(at, Y, Z + 1), ChestBlockEntity.class);
                    for (int s = 0; s < sink.getContainerSize(); s++) {
                        ItemStack moving = sink.removeItemNoUpdate(s);
                        if (moving.isEmpty()) continue;
                        boolean placed = false;
                        for (int d = 0; d < source.getContainerSize() && !placed; d++) {
                            if (source.getItem(d).isEmpty()) {
                                source.setItem(d, moving);
                                placed = true;
                            }
                        }
                        if (!placed) pile.merge(moving.getItem(), (long) moving.getCount(), Long::sum);
                    }
                }
                return null;
            });
            int ops = random.nextInt(20) == 0 ? 50 : 1 + random.nextInt(4);
            for (int i = 0; i < ops; i++) {
                try {
                    act(net, random, player, pile, open, t[0]);
                } catch (RuntimeException failed) {
                    findings.add("tick " + t[0] + ": " + failed + " at " + (failed.getStackTrace().length > 0 ? failed.getStackTrace()[0] : "?")
                            + "\n    recent:\n    " + Stress.recent());
                    com.kadikular.quantimium.Quantimium.LOGGER.error("[stress] {} failed", test, failed);
                }
            }
            // What fell out of a broken chest is picked up.
            Stress.harness(() -> {
                BlockPos a = helper.absolutePos(boxFrom), b = helper.absolutePos(boxTo);
                for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(a.getCenter(), b.getCenter()).inflate(2))) {
                    pile.merge(drop.getItem().getItem(), (long) drop.getItem().getCount(), Long::sum);
                    drop.discard();
                }
                return null;
            });
            Map<Item, Long> now = Stress.harness(() -> Stress.census(helper, boxFrom, boxTo, List.of(player), pile));
            String wrong = Stress.harness(() -> Stress.compare(before[0], now));
            if (wrong != null) {
                findings.add("tick " + t[0] + ": items changed: " + wrong + "\n    recent:\n    " + Stress.recent());
                before[0] = now;
            }
            if (t[0] >= settle + length || findings.size() >= 10) {
                done[0] = true;
                if (open[0] != null) player.containerMenu.removed(player);
                Stress.end(test, findings);
                TestSupport.removePlayer(helper, player);
                TestSupport.clearField(helper);
                if (findings.isEmpty()) helper.succeed();
                else helper.fail(findings.size() + " findings, first: " + findings.getFirst().lines().findFirst().orElse(""));
            }
        });
    }

    /**
     * A firehose: a quarry pipes stacks of what it digs into the network through the Materialiser every tick, an
     * export pipe pulls stacks out, providers push and requesters pull, Crafters draw on it (so its offers are always
     * out of date and being worked out again), and a player has the terminal open throughout, now and then taking
     * something. How long ticks take, and what takes it; every item counted.
     */
    @GameTest(template = TestSupport.FLOOR_17, batch = "stress_logistics_firehose", timeoutTicks = 40_000, padding = 64)
    public static void firehose(GameTestHelper helper) {
        String test = "firehose";
        Random random = Stress.random(test);
        TestSupport.clearField(helper);
        Net net = new Net(helper, random);
        List<Item> filler = filler();
        List<Item> dug = new ArrayList<>(List.of(Items.COBBLESTONE, Items.DIRT, Items.GRAVEL, Items.ANDESITE, Items.DIORITE, Items.GRANITE,
                Items.DEEPSLATE, Items.TUFF, Items.COAL, Items.FLINT, Items.REDSTONE, Items.LAPIS_LAZULI, Items.DIAMOND, Items.EMERALD));
        for (int i = 0; i < 40; i++) dug.add(filler.get(random.nextInt(filler.size())));
        int stores = Stress.scaled(16), makers = Stress.scaled(4);
        net.materialiser = new BlockPos(net.x + stores + 4 + makers + 2, Y, Z);
        Stress.forceLoad(helper, new BlockPos(0, 0, Z - 3), net.materialiser.offset(2, 0, 3));
        BlockPos controllerAt = new BlockPos(2, Y, Z);
        helper.setBlock(controllerAt, ModBlocks.LOGISTICS_CONTROLLER.get().defaultBlockState().setValue(LogisticsControllerBlock.FACING, Direction.EAST)
                .setValue(LogisticsControllerBlock.OUTPUTS.get(Direction.NORTH), false).setValue(LogisticsControllerBlock.OUTPUTS.get(Direction.EAST), true));
        net.controller = helper.getBlockEntity(controllerAt, LogisticsControllerBlockEntity.class);
        net.controller.toggleOutput(Direction.NORTH);
        helper.setBlock(controllerAt.north(), ModBlocks.LOGISTICS_TERMINAL.get());
        net.terminal = helper.getBlockEntity(controllerAt.north(), LogisticsTerminalBlockEntity.class);
        for (int i = 0; i < stores; i++) {
            List<ItemStack> stock = new ArrayList<>();
            for (int s = 0; s < 6; s++) stock.add(worth(random));
            net.chest(LogisticsMember.Role.STORAGE, false, stock);
        }
        for (int i = 0; i < 2; i++) {
            List<ItemStack> stock = new ArrayList<>();
            for (int s = 0; s < 10; s++) stock.add(worth(random));
            net.chest(LogisticsMember.Role.PROVIDER, true, stock);
        }
        for (int i = 0; i < 2; i++) {
            LogisticsReceptacleBlockEntity requester = net.chest(LogisticsMember.Role.REQUESTER, true, List.of());
            requester.setFilterSlot(0, new ItemStack(dug.get(random.nextInt(dug.size()))));
            requester.setFilterSlot(1, new ItemStack(WANTED[random.nextInt(WANTED.length)], 32));
            requester.toggleKeep();
        }
        for (int i = 0; i < makers; i++) net.crafter(i % 2 == 1, worth(random));
        while (net.x < net.materialiser.getX()) net.chest(LogisticsMember.Role.STORAGE, false, List.of());
        helper.setBlock(net.materialiser, ModBlocks.LOGISTICS_MATERIALISER.get());
        Stress.report(test, "built: " + net.receptacles.size() + " receptacles, " + net.crafters.size() + " crafters; " + Stress.scaled(4) + " stacks dug a tick");

        ServerPlayer player = TestSupport.player(helper, new BlockPos(8, Y, 4), GameType.SURVIVAL);
        Map<Item, Long> pile = new HashMap<>();
        BlockPos boxFrom = new BlockPos(0, 0, Z - 3), boxTo = new BlockPos(net.materialiser.getX() + 1, Y + 1, Z + 3);
        List<String> findings = new ArrayList<>();
        @SuppressWarnings("unchecked") Map<Item, Long>[] before = new Map[1];
        int[] t = {0};
        long[] flow = {0, 0};
        boolean[] done = {false};
        int settle = 60, length = Stress.scaled(1200);
        Stress.begin();
        helper.onEachTick(() -> {
            if (done[0]) return;
            t[0]++;
            net.controller.getEnergyStorage().setEnergy(LaserTuning.LOGISTICS_CAPACITY);
            if (t[0] < settle) return;
            if (t[0] == settle) {
                before[0] = Stress.harness(() -> Stress.census(helper, boxFrom, boxTo, List.of(player), pile));
                return;
            }
            var handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(net.materialiser), Direction.UP);
            // The terminal open: what it lists, every tick.
            Stress.time("terminal open", "", net.terminal::contents);
            if (handler != null) {
                // The quarry: what it digs comes from nowhere, so it's counted as there from the start.
                for (int i = 0; i < Stress.scaled(4); i++) {
                    Item item = dug.get(random.nextInt(dug.size()));
                    int count = 16 + random.nextInt(49);
                    before[0].merge(item, (long) count, Long::sum);
                    int in = Stress.time("quarry insert", item + " x" + count, () -> {
                        try (Transaction transaction = Transaction.openRoot()) {
                            int moved = handler.insert(handler.size() - 1, ItemResource.of(new ItemStack(item)), count, transaction);
                            transaction.commit();
                            return moved;
                        }
                    });
                    flow[0] += in;
                    // What didn't fit stays in the quarry's buffer.
                    if (in < count) pile.merge(item, (long) count - in, Long::sum);
                }
                // The export pipe: a few stacks of what's stored, out.
                for (int i = 0; i < Stress.scaled(3); i++) {
                    int size = handler.size();
                    if (size <= 1) break;
                    int index = random.nextInt(size - 1);
                    ItemResource resource = handler.getResource(index);
                    if (resource.isEmpty() || Stress.WORTH.containsKey(resource.getItem())) continue;
                    int stored = (int) Math.min(64, net.controller.offers().real(resource));
                    if (stored <= 0) continue;
                    int out = Stress.time("export extract", resource.getItem() + " x" + stored, () -> {
                        try (Transaction transaction = Transaction.openRoot()) {
                            int moved = handler.extract(index, resource, stored, transaction);
                            transaction.commit();
                            return moved;
                        }
                    });
                    flow[1] += out;
                    if (out > 0) pile.merge(resource.getItem(), (long) out, Long::sum);
                }
            }
            // Now and then the player takes something, worth crafting or dug.
            if (random.nextInt(4) == 0) {
                boolean worth = random.nextBoolean();
                Item item = worth ? WANTED[random.nextInt(WANTED.length)] : dug.get(random.nextInt(dug.size()));
                int count = 1 + random.nextInt(64);
                // What's dug only as it's stored: made of other dug things, it isn't counted by worth.
                ItemStack got = Stress.time("terminal take", item + " x" + count, () -> worth ? net.terminal.take(ItemResource.of(new ItemStack(item)), count)
                        : com.kadikular.quantimium.laser.Logistics.take(net.controller.spots(), ItemResource.of(new ItemStack(item)), count, false, true, false));
                if (!got.isEmpty()) pile.merge(got.getItem(), (long) got.getCount(), Long::sum);
            }
            Map<Item, Long> now = Stress.harness(() -> Stress.census(helper, boxFrom, boxTo, List.of(player), pile));
            String wrong = Stress.harness(() -> Stress.compare(before[0], now));
            if (wrong != null) {
                findings.add("tick " + t[0] + ": items changed: " + wrong + "\n    recent:\n    " + Stress.recent());
                before[0] = now;
            }
            if (t[0] >= settle + length || findings.size() >= 10) {
                done[0] = true;
                Stress.report(test, "moved: " + flow[0] + " in, " + flow[1] + " out through the Materialiser");
                Stress.end(test, findings);
                TestSupport.removePlayer(helper, player);
                TestSupport.clearField(helper);
                if (findings.isEmpty()) helper.succeed();
                else helper.fail(findings.size() + " findings, first: " + findings.getFirst().lines().findFirst().orElse(""));
            }
        });
    }

    /**
     * How long a network takes to work its offers out, all at once, measured: a fixed network of 60 chests of filler
     * and worth (scaled) and 18 crafting-table Crafters drawing on it and making for it; a nugget added between each of
     * twelve rebuilds, so every one has something to catch up on. Reports each rebuild's time.
     */
    @GameTest(template = TestSupport.FLOOR_17, batch = "stress_rebuild_bench", timeoutTicks = 4000, padding = 112)
    public static void rebuildBench(GameTestHelper helper) {
        String test = "rebuildBench";
        Random random = new Random(Long.getLong("quantimium.stressSeed", 42L));
        TestSupport.clearField(helper);
        Net net = new Net(helper, random);
        List<Item> filler = filler();
        int stores = Stress.scaled(60), makers = Stress.scaled(18);
        net.materialiser = new BlockPos(net.x + stores + makers + 1, Y, Z);
        Stress.forceLoad(helper, new BlockPos(0, 0, Z - 3), net.materialiser.offset(2, 0, 3));
        BlockPos controllerAt = new BlockPos(2, Y, Z);
        helper.setBlock(controllerAt, ModBlocks.LOGISTICS_CONTROLLER.get().defaultBlockState().setValue(LogisticsControllerBlock.FACING, Direction.EAST)
                .setValue(LogisticsControllerBlock.OUTPUTS.get(Direction.NORTH), false).setValue(LogisticsControllerBlock.OUTPUTS.get(Direction.EAST), true));
        net.controller = helper.getBlockEntity(controllerAt, LogisticsControllerBlockEntity.class);
        for (int i = 0; i < stores; i++) {
            List<ItemStack> stock = new ArrayList<>();
            for (int s = 0; s < 24; s++) stock.add(new ItemStack(filler.get(random.nextInt(filler.size())), 1 + random.nextInt(64)));
            for (int s = 0; s < 3; s++) stock.add(worth(random));
            net.chest(LogisticsMember.Role.STORAGE, false, stock);
        }
        for (int i = 0; i < makers; i++) net.crafter(false);
        while (net.x < net.materialiser.getX()) net.chest(LogisticsMember.Role.STORAGE, false, List.of());
        helper.setBlock(net.materialiser, ModBlocks.LOGISTICS_MATERIALISER.get());
        int[] t = {0};
        List<Long> times = new ArrayList<>();
        helper.onEachTick(() -> {
            t[0]++;
            net.controller.getEnergyStorage().setEnergy(LaserTuning.LOGISTICS_CAPACITY);
            if (t[0] < 100 || t[0] % 5 != 0 || times.size() >= 12) return;
            // Something changed: a nugget more in the first chest.
            ChestBlockEntity first = helper.getBlockEntity(net.storage.getFirst(), ChestBlockEntity.class);
            first.setItem(26, new ItemStack(Items.IRON_NUGGET, 1 + times.size()));
            long began = System.nanoTime();
            var table = com.kadikular.quantimium.laser.OfferTable.build(helper.getLevel(), net.controller, net.controller.getBlockPos(), net.controller.members());
            long took = System.nanoTime() - began;
            times.add(took);
            if (times.size() == 1) Stress.report(test, "network: " + net.receptacles.size() + " receptacles, " + net.crafters.size() + " crafters, "
                    + table.stored().size() + " kinds stored, " + table.entries().size() + " listed, "
                    + table.entries().stream().filter(e -> e.craftable() > 0).count() + " craftable, e.g. "
                    + table.entries().stream().filter(e -> e.craftable() > 0).limit(5).map(e -> e.item().getItem() + " " + e.craftable()).toList()
                    + "; a crafter's preview: " + net.crafters.getFirst().getPreview().size()
                    + "; asked directly: " + net.crafters.getFirst().makes(net.controller, table.stock(null, true, true), true).size()
                    + "; energy " + net.crafters.getFirst().getEnergyStorage().getEnergyStored()
                    + ", catalyst " + net.crafters.getFirst().getInventory().getStackInSlot(QuantumCrafterBlockEntity.CATALYST_SLOT)
                    + ", grid0 " + net.crafters.getFirst().getInventory().getStackInSlot(0)
                    + "; members " + net.controller.members().size() + ", makers " + net.controller.members().stream().filter(m -> m.maker() != null).count());
            if (times.size() == 12) {
                List<Long> sorted = new ArrayList<>(times);
                java.util.Collections.sort(sorted);
                Stress.report(test, String.format("first rebuild %.1fms; then median %.1fms, min %.1fms, max %.1fms", times.getFirst() / 1e6,
                        sorted.get(sorted.size() / 2) / 1e6, sorted.get(1) / 1e6, sorted.getLast() / 1e6));
                TestSupport.clearField(helper);
                helper.succeed();
            }
        });
    }

    /** One random thing a player, a pipe or a hand does to the network. */
    private static void act(Net net, Random random, ServerPlayer player, Map<Item, Long> pile, QuantumCrafterBlockEntity[] open, int tick) {
        int roll = random.nextInt(100);
        if (roll < 30) {
            // Taken at the terminal.
            Item item = WANTED[random.nextInt(WANTED.length)];
            int count = 1 + random.nextInt(random.nextBoolean() ? 64 : 192);
            ItemStack got = Stress.time("terminal take", item + " x" + count, () -> net.terminal.take(ItemResource.of(new ItemStack(item)), count));
            if (!got.isEmpty()) pile.merge(got.getItem(), (long) got.getCount(), Long::sum);
        } else if (roll < 45) {
            // Put back at the terminal.
            ItemStack stack = fromPile(pile, random);
            if (stack.isEmpty()) return;
            ItemStack left = Stress.time("terminal insert", stack.toString(), () -> net.terminal.insert(stack));
            pile.merge(stack.getItem(), (long) left.getCount(), Long::sum);
        } else if (roll < 60) {
            // A Crafter's screen: a click on something it shows.
            if (net.crafters.isEmpty()) return;
            QuantumCrafterBlockEntity crafter = net.crafters.get(random.nextInt(net.crafters.size()));
            if (open[0] != crafter) {
                if (open[0] != null) player.containerMenu.removed(player);
                player.containerMenu = crafter.createMenu(1, player.getInventory(), player);
                open[0] = crafter;
            }
            // Something worth counting it shows: made, or already in its outputs.
            List<Integer> worthy = new ArrayList<>();
            for (int g = 0; g < crafter.ghostCount(); g++) {
                if (Stress.WORTH.containsKey(crafter.getGhost(QuantumCrafterBlockEntity.GHOST_BASE + g).getItem())) worthy.add(QuantumCrafterBlockEntity.GHOST_BASE + g);
            }
            for (int o = 0; o < QuantumCrafterBlockEntity.OUTPUT_SLOTS; o++) {
                ItemStack out = crafter.getInventory().getStackInSlot(QuantumCrafterBlockEntity.OUTPUT_START + o);
                if (!out.isEmpty() && Stress.WORTH.containsKey(out.getItem())) worthy.add(o);
            }
            if (worthy.isEmpty()) return;
            int slot = worthy.get(random.nextInt(worthy.size()));
            int mode = 1 + random.nextInt(4);
            Stress.time("crafter click", "mode " + mode + " slot " + slot, () -> player.containerMenu.clickMenuButton(player, mode * QuantumCrafterMenu.TAKE + slot));
            ItemStack carried = player.containerMenu.getCarried();
            if (!carried.isEmpty()) {
                pile.merge(carried.getItem(), (long) carried.getCount(), Long::sum);
                player.containerMenu.setCarried(ItemStack.EMPTY);
            }
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack held = player.getInventory().removeItemNoUpdate(i);
                if (!held.isEmpty()) pile.merge(held.getItem(), (long) held.getCount(), Long::sum);
            }
        } else if (roll < 78) {
            // A pipe or a storage bus through the Materialiser, in transactions that commit or not.
            var handler = net.helper.getLevel().getCapability(Capabilities.Item.BLOCK, net.helper.absolutePos(net.materialiser), Direction.UP);
            if (handler == null) return;
            boolean commit = random.nextBoolean();
            if (random.nextInt(3) == 0) {
                ItemStack stack = fromPile(pile, random);
                if (stack.isEmpty()) return;
                int in = Stress.time("materialiser insert", stack + (commit ? "" : " (aborted)"), () -> {
                    try (Transaction transaction = Transaction.openRoot()) {
                        int moved = handler.insert(handler.size() - 1, ItemResource.of(stack), stack.getCount(), transaction);
                        if (commit) transaction.commit();
                        return commit ? moved : 0;
                    }
                });
                pile.merge(stack.getItem(), (long) stack.getCount() - in, Long::sum);
                return;
            }
            int size = handler.size();
            if (size <= 1) return;
            int index = random.nextInt(size - 1);
            ItemResource resource = handler.getResource(index);
            if (resource.isEmpty()) return;
            int amount = 1 + random.nextInt(random.nextBoolean() ? 64 : 256);
            // Filler only as it's stored, the Materialiser showing only what's stored meanwhile: what a Crafter would make
            // of it isn't counted by worth.
            boolean filler = !Stress.WORTH.containsKey(resource.getItem());
            var materialiser = net.helper.getBlockEntity(net.materialiser, com.kadikular.quantimium.block.entity.LogisticsMaterialiserBlockEntity.class);
            int at = index;
            if (filler) {
                while (materialiser.mode() != com.kadikular.quantimium.block.entity.LogisticsMaterialiserBlockEntity.Mode.REAL) materialiser.cycleMode();
                at = -1;
                for (int i = 0; i < handler.size(); i++) if (handler.getResource(i).equals(resource)) at = i;
                if (at < 0) {
                    while (materialiser.mode() != com.kadikular.quantimium.block.entity.LogisticsMaterialiserBlockEntity.Mode.BOTH) materialiser.cycleMode();
                    return;
                }
            }
            int asked = amount, slot = at;
            boolean nested = random.nextBoolean();
            int out = Stress.time("materialiser extract", resource.getItem() + " x" + asked + (commit ? "" : " (aborted)") + (nested ? " nested" : ""), () -> {
                try (Transaction transaction = Transaction.openRoot()) {
                    int got = handler.extract(slot, resource, asked, transaction);
                    if (nested) {
                        // A second pull inside, given up: what it moved goes back, the first stands.
                        try (Transaction inner = Transaction.open(transaction)) {
                            handler.extract(slot, resource, asked, inner);
                        }
                    }
                    if (commit) transaction.commit();
                    return commit ? got : 0;
                }
            });
            if (filler) while (materialiser.mode() != com.kadikular.quantimium.block.entity.LogisticsMaterialiserBlockEntity.Mode.BOTH) materialiser.cycleMode();
            if (out > 0) pile.merge(resource.getItem(), (long) out, Long::sum);
        } else if (roll < 88) {
            // Someone fiddles with a receptacle.
            LogisticsReceptacleBlockEntity receptacle = net.receptacles.get(random.nextInt(net.receptacles.size()));
            switch (random.nextInt(4)) {
                case 0 -> {
                    if (!net.makers.contains(receptacle)) receptacle.toggleActive();
                }
                case 1 -> receptacle.cycleCrafts();
                case 2 -> receptacle.toggleRequestsMade();
                // Not a requester: set to everything but its list, asking for what's made, it'd have anything crafted.
                default -> {
                    if (receptacle.role() != LogisticsMember.Role.REQUESTER) receptacle.toggleAllow();
                }
            }
            Stress.note("fiddled with the receptacle at " + receptacle.getBlockPos().toShortString());
        } else if (roll < 92) {
            // A storage chest broken and put back empty.
            if (net.storage.isEmpty()) return;
            BlockPos chest = net.storage.get(random.nextInt(net.storage.size()));
            Stress.time("chest broken", chest.toShortString(), () -> {
                net.helper.getLevel().destroyBlock(net.helper.absolutePos(chest), true);
                net.helper.setBlock(chest, Blocks.CHEST);
                // The chest dropped itself too: that one's put back.
                pile.merge(Items.CHEST, -1L, Long::sum);
                return null;
            });
        } else {
            // Spam: the same thing asked for over and over in one tick.
            Item item = WANTED[random.nextInt(WANTED.length)];
            for (int i = 0; i < 20; i++) {
                ItemStack got = Stress.time("terminal spam", item.toString(), () -> net.terminal.take(ItemResource.of(new ItemStack(item)), 64));
                if (!got.isEmpty()) pile.merge(got.getItem(), (long) got.getCount(), Long::sum);
            }
        }
    }

    /** Some of what the player's holding, taken off the pile. */
    static ItemStack fromPile(Map<Item, Long> pile, Random random) {
        List<Item> held = pile.entrySet().stream().filter(e -> e.getValue() > 0).map(Map.Entry::getKey).toList();
        if (held.isEmpty()) return ItemStack.EMPTY;
        Item item = held.get(random.nextInt(held.size()));
        int count = (int) Math.min(pile.get(item), Math.min(new ItemStack(item).getMaxStackSize(), 1 + random.nextInt(64)));
        pile.merge(item, (long) -count, Long::sum);
        return new ItemStack(item, count);
    }
}

package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.entity.MirrorEndermite;
import com.kadikular.quantimium.entity.Veiled;
import com.kadikular.quantimium.init.ModEntities;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.item.DecoherenceLanceItem;
import com.kadikular.quantimium.phase.FluxRift;
import com.kadikular.quantimium.phase.FluxRiftManager;
import com.kadikular.quantimium.phase.MirrorPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Everything that needs a player in the mirror: the Lance, the Veiled's mirror behaviour, and
 * a rift's hazards. Wiki: mechanics/veiled, mechanics/flux-rifts.
 *
 * <p>The Lance is driven the way holding use drives it ({@link TestSupport#lanceTick}): the stand-in
 * is aimed, and the real item finds its target from where it looks. Each test runs alone.
 */
public final class MirrorTests {

    private static final BlockPos NEAR = new BlockPos(8, 2, 4);
    private static final BlockPos PLAYER = new BlockPos(8, 2, 10);

    private MirrorTests() {}

    private static Vec3 body(Veiled veiled) {
        return veiled.position().add(0.0, veiled.getBbHeight() * 0.55, 0.0);
    }

    // ---- The Lance on the Veiled ----

    // covers: veiled.lance.aggravate, veiled.lance.freeze
    @GameTest(template = TestSupport.FLOOR_17, batch = "mirror_lance", timeoutTicks = 60)
    public static void theLanceAggravatesAndFreezesIt(GameTestHelper helper) {
        TestPlayer player = TestSupport.phasedPlayer(helper, PLAYER);
        Veiled veiled = helper.spawn(ModEntities.VEILED.get(), NEAR);
        ItemStack lance = TestSupport.chargedLance();
        int[] held = {0};
        Vec3[] start = {null};
        helper.onEachTick(() -> {
            if (held[0] >= 20) return;
            TestSupport.aimAt(player, body(veiled));
            TestSupport.lanceTick(player, lance, held[0]++);
            if (start[0] == null) start[0] = veiled.position();
        });
        helper.runAfterDelay(22, () -> {
            helper.assertValueEqual(veiled.state(), Veiled.State.AGGRAVATED, "state once lanced");
            helper.assertTrue(veiled.position().distanceTo(start[0]) < 0.05, "held in the beam, it should not move");
            float expected = 1.0f - 20.0f / 160.0f;
            helper.assertTrue(Math.abs(veiled.coherence() - expected) < 0.02,
                    "twenty ticks of beam should wear an eighth off, coherence " + veiled.coherence());
            int spent = DecoherenceLanceItem.CAPACITY - DecoherenceLanceItem.energy(lance);
            helper.assertValueEqual(spent, 20 * Config.lanceFePerTick(), "FE the Lance spent in twenty ticks");
            TestSupport.discard(veiled);
            TestSupport.removePlayer(helper, player);
            helper.succeed();
        });
    }

    // covers: veiled.lance.charge
    @GameTest(template = TestSupport.FLOOR_17, batch = "mirror_charge", timeoutTicks = 200)
    public static void whenTheBeamBreaksItStrikes(GameTestHelper helper) {
        TestPlayer player = TestSupport.phasedPlayer(helper, PLAYER);
        Veiled veiled = helper.spawn(ModEntities.VEILED.get(), new BlockPos(8, 2, 6));
        ItemStack lance = TestSupport.chargedLance();
        for (int i = 0; i < 5; i++) {
            TestSupport.aimAt(player, body(veiled));
            TestSupport.lanceTick(player, lance, i);
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(player.getHealth() < player.getMaxHealth(), "it should come at the lancer and strike");
            helper.assertTrue(player.hasEffect(MobEffects.BLINDNESS), "a strike blinds");
            helper.assertTrue(player.hasEffect(MobEffects.SLOWNESS), "a strike slows");
            TestSupport.discard(veiled);
            TestSupport.removePlayer(helper, player);
        });
    }

    // covers: veiled.lance.lose_interest
    @GameTest(template = TestSupport.FLOOR_17, batch = "mirror_interest", timeoutTicks = 80)
    public static void leavingTheMirrorEndsTheFight(GameTestHelper helper) {
        TestPlayer player = TestSupport.phasedPlayer(helper, PLAYER);
        Veiled veiled = helper.spawn(ModEntities.VEILED.get(), NEAR);
        ItemStack lance = TestSupport.chargedLance();
        TestSupport.aimAt(player, body(veiled));
        TestSupport.lanceTick(player, lance, 0);
        helper.assertValueEqual(veiled.state(), Veiled.State.AGGRAVATED, "state once lanced");
        MirrorPhase.exit(player, MirrorPhase.ExitReason.MANUAL);
        helper.succeedWhen(() -> {
            helper.assertTrue(veiled.onCooldown(), "with its lancer gone from the mirror, it should be driven off");
            TestSupport.discard(veiled);
            TestSupport.removePlayer(helper, player);
        });
    }

    // ---- Its mirror behaviour ----

    // covers: veiled.curious
    @GameTest(template = TestSupport.FLOOR_17, batch = "mirror_curious", timeoutTicks = 260)
    public static void staredAtItComesCloserAndStops(GameTestHelper helper) {
        // Creative, so it is not picked to be shadowed. Both kept off the floor's outermost row, which
        // can fall in a chunk that does not tick entities.
        TestPlayer player = TestSupport.phasedPlayer(helper, new BlockPos(8, 2, 15), GameType.CREATIVE);
        Veiled veiled = helper.spawn(ModEntities.VEILED.get(), new BlockPos(8, 2, 1));
        double[] arrived = {-1.0};
        long[] arrivedAt = {-1};
        helper.onEachTick(() -> {
            TestSupport.aimAt(player, body(veiled));
            if (arrived[0] < 0.0 && veiled.distanceTo(player) <= 10.0) {
                arrived[0] = veiled.distanceTo(player);
                arrivedAt[0] = helper.getLevel().getGameTime();
            }
        });
        // Once it is there it stops. Afterwards it may stroll about at random, which is not curiosity.
        helper.succeedWhen(() -> {
            helper.assertTrue(arrived[0] >= 0.0, "stared at, it should come to about ten blocks [state="
                    + veiled.state() + ", distance " + veiled.distanceTo(player) + "]");
            helper.assertTrue(helper.getLevel().getGameTime() >= arrivedAt[0] + 10, "give it a moment to stop");
            helper.assertTrue(veiled.distanceTo(player) >= 9.0,
                    "having arrived, it should stop there, not come right up: " + veiled.distanceTo(player));
            TestSupport.discard(veiled);
            TestSupport.removePlayer(helper, player);
        });
    }

    // covers: veiled.wary
    @GameTest(template = TestSupport.FLOOR_17, batch = "mirror_wary", timeoutTicks = 120)
    public static void tooCloseItBacksAway(GameTestHelper helper) {
        TestPlayer player = TestSupport.phasedPlayer(helper, new BlockPos(8, 2, 8));
        Veiled veiled = helper.spawn(ModEntities.VEILED.get(), new BlockPos(8, 2, 3));
        helper.runAfterDelay(100, () -> {
            double distance = veiled.distanceTo(player);
            helper.assertTrue(distance > 7.0, "within eight blocks it should back off, now " + distance);
            TestSupport.discard(veiled);
            TestSupport.removePlayer(helper, player);
            helper.succeed();
        });
    }

    // ---- Rifts, by hand ----

    // covers: rift.drain, rift.collapse
    @GameTest(template = TestSupport.FLOOR_17, batch = "mirror_rift_drain", timeoutTicks = 200)
    public static void lancingARiftClosesItAndPays(GameTestHelper helper) {
        TestPlayer player = TestSupport.phasedPlayer(helper, new BlockPos(8, 2, 15));
        FluxRift rift = TestSupport.rift(helper, new BlockPos(8, 2, 8), 1);
        ItemStack lance = TestSupport.chargedLance();
        int[] held = {0};
        long[] gave = {-1};
        ServerLevel level = helper.getLevel();
        helper.onEachTick(() -> {
            boolean open = FluxRiftManager.rifts(level).stream().anyMatch(r -> r.id().equals(rift.id()));
            if (!open) {
                if (gave[0] < 0) gave[0] = held[0];
                return;
            }
            TestSupport.aimAt(player, rift.centre());
            TestSupport.lanceTick(player, lance, held[0]++);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(gave[0] > 0, "the rift should have given");
            // A stage 1 rift holds its coherence against the Lance's drain for 4 s of unbroken beam.
            double expected = FluxRift.maxCoherence(1) / Config.lanceDrainPerTick();
            helper.assertTrue(Math.abs(gave[0] - expected) <= 1, "it should give after " + expected + " ticks, took " + gave[0]);
            // Stage 1 pays nothing: a planted seed has to be left to grow before it does.
            helper.assertValueEqual(player.getInventory().countItem(ModItems.RIFT_RESIDUE.get()), 0, "residue for closing a stage 1 rift");
            helper.assertTrue(!MirrorPhase.isPhased(player), "closing it should throw the breaker back to the real world");
            TestSupport.removePlayer(helper, player);
        });
    }

    // covers: rift.fight_back
    @GameTest(template = TestSupport.FLOOR_17, batch = "mirror_rift_fight", timeoutTicks = 120)
    public static void aDrainedRiftSpitsMites(GameTestHelper helper) {
        TestPlayer player = TestSupport.phasedPlayer(helper, new BlockPos(8, 2, 15));
        FluxRift rift = TestSupport.rift(helper, new BlockPos(8, 2, 6), 4);
        ItemStack lance = TestSupport.chargedLance();
        int[] held = {0};
        helper.onEachTick(() -> {
            TestSupport.aimAt(player, rift.centre());
            TestSupport.lanceTick(player, lance, held[0]++);
        });
        AABB around = new AABB(helper.absolutePos(new BlockPos(8, 2, 8))).inflate(24.0);
        helper.runAfterDelay(100, () -> {
            // Stage 4: a mite is spat 85% of seconds while drained; five seconds without one is 1 in 13,000.
            int mites = helper.getLevel().getEntitiesOfClass(MirrorEndermite.class, around, FluxRiftManager::spawnedByRift).size();
            helper.assertTrue(mites >= 1, "a drained rift should fight back with mites");
            TestSupport.removeRift(helper, rift);
            TestSupport.removePlayer(helper, player);
            helper.succeed();
        });
    }

    // covers: rift.contact
    @GameTest(template = TestSupport.FLOOR_17, batch = "mirror_rift_contact", timeoutTicks = 40)
    public static void touchingARiftBurnsAndDragsYouThrough(GameTestHelper helper) {
        BlockPos anchor = new BlockPos(8, 2, 8);
        FluxRift rift = TestSupport.rift(helper, anchor, 2);
        TestPlayer player = TestSupport.player(helper, anchor, GameType.SURVIVAL);
        helper.succeedWhen(() -> {
            helper.assertTrue(MirrorPhase.isPhased(player), "touched from the real world, it should drag you into the mirror");
            helper.assertValueEqual(player.getHealth(), player.getMaxHealth() - 3.0f, "health after touching a stage 2 rift");
            TestSupport.removeRift(helper, rift);
            TestSupport.removePlayer(helper, player);
        });
    }

    // covers: rift.lightning
    @GameTest(template = TestSupport.FLOOR_17, batch = "mirror_rift_lightning", timeoutTicks = 160)
    public static void standingNearARiftDrawsLightning(GameTestHelper helper) {
        BlockPos anchor = new BlockPos(8, 2, 8);
        FluxRift rift = TestSupport.rift(helper, anchor, 4);
        // Outside its solid body, well inside its reach (about 9 blocks at stage 4).
        TestPlayer player = TestSupport.player(helper, new BlockPos(8, 2, 13), GameType.SURVIVAL);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(player.getHealth(), player.getMaxHealth() - 4.5f, "health after one stage 4 bolt");
            TestSupport.removeRift(helper, rift);
            TestSupport.removePlayer(helper, player);
        });
    }

    // covers: mirror.usable_blocks
    @GameTest(template = TestSupport.FLOOR_17, batch = "mirror_usable", timeoutTicks = 40)
    public static void doorsAndLeversWorkFromTheMirror(GameTestHelper helper) {
        TestPlayer player = TestSupport.phasedPlayer(helper, PLAYER);
        BlockPos door = new BlockPos(6, 2, 8);
        BlockPos lever = new BlockPos(8, 2, 8);
        BlockPos repeater = new BlockPos(10, 2, 8);
        helper.setBlock(door, net.minecraft.world.level.block.Blocks.OAK_DOOR);
        helper.setBlock(door.above(), net.minecraft.world.level.block.Blocks.OAK_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        helper.setBlock(lever, net.minecraft.world.level.block.Blocks.LEVER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
        helper.setBlock(repeater, net.minecraft.world.level.block.Blocks.REPEATER);
        use(helper, player, door);
        use(helper, player, lever);
        use(helper, player, repeater);
        helper.assertTrue(helper.getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.OPEN), "the door opens");
        helper.assertTrue(helper.getBlockState(lever).getValue(net.minecraft.world.level.block.LeverBlock.POWERED), "the lever flips");
        helper.assertValueEqual(helper.getBlockState(repeater).getValue(net.minecraft.world.level.block.RepeaterBlock.DELAY), 1,
                "a repeater is scenery");
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    // covers: mirror.mite_traces
    @GameTest(template = TestSupport.FLOOR_17, batch = "mirror_mite_traces", timeoutTicks = 40)
    public static void mitesKilledInTheMirrorLeaveTraces(GameTestHelper helper) {
        TestPlayer player = TestSupport.phasedPlayer(helper, PLAYER);
        ServerLevel level = helper.getLevel();
        // A quarter of them drop one: forty leave some (all but one time in a hundred thousand).
        for (int i = 0; i < 40; i++) {
            MirrorEndermite mite = helper.spawn(ModEntities.MIRROR_ENDERMITE.get(), NEAR);
            mite.hurtServer(level, level.damageSources().playerAttack(player), 1000.0f);
        }
        AABB around = new AABB(helper.absolutePos(NEAR)).inflate(4.0);
        var traces = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, around,
                drop -> drop.getItem().is(ModItems.QUANTIMIUM_TRACE.get()));
        helper.assertTrue(!traces.isEmpty(), "some mites left a Trace");
        for (var trace : traces) {
            helper.assertTrue(player.getUUID().equals(trace.getTarget()), "a Trace is the killer's, in the mirror");
            trace.discard();
        }
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    private static void use(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        player.gameMode.useItemOn(player, helper.getLevel(), ItemStack.EMPTY, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
    }
}

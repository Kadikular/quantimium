package com.kadikular.quantimium.gametest;

import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.ContainmentHallBlockEntity;
import com.kadikular.quantimium.entity.Veiled;
import com.kadikular.quantimium.init.ModEntities;
import com.kadikular.quantimium.phase.VeiledManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import com.kadikular.quantimium.block.entity.RiftStabiliserBlockEntity;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/**
 * The Veiled. Wiki: mechanics/veiled.
 *
 * <p>Worn down through the Decoherence Projector's path ({@link Veiled#decohere}) rather than the
 * Lance's, which needs a phased player. Both wear it at one tick per tick and share the pin and hall
 * rules. A stand-in player keeps it from despawning. Every test runs alone, since one Veiled per
 * area and halls that reach 32 blocks would otherwise reach across tests.
 */
public final class VeiledTests {

    private static final BlockPos HALL = new BlockPos(8, 2, 8);
    private static final BlockPos SPAWN = new BlockPos(8, 2, 2);
    private static final BlockPos PLAYER = new BlockPos(2, 2, 14);

    private VeiledTests() {}

    // covers: veiled.capture, veiled.projector, hall.arms
    @GameTest(template = TestSupport.FLOOR_17, batch = "veiled_capture", timeoutTicks = 400)
    public static void wornDownBesideAHallItIsTakenIn(GameTestHelper helper) {
        ServerPlayer player = TestSupport.player(helper, PLAYER);
        ContainmentHallBlockEntity hall = TestSupport.readyHall(helper, HALL, 1, 4);
        Veiled veiled = helper.spawn(ModEntities.VEILED.get(), SPAWN);
        ServerLevel level = helper.getLevel();
        long[] tookHold = {-1};
        helper.onEachTick(() -> {
            if (veiled.isRemoved()) return;
            if (veiled.captureHall().isPresent()) {
                if (tookHold[0] < 0) {
                    tookHold[0] = level.getGameTime();
                    helper.assertTrue(veiled.coherence() <= 0.5f + 0.01f,
                            "the hall should only take hold once it is worn past halfway, coherence " + veiled.coherence());
                }
                return;
            }
            veiled.decohere(level);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(veiled.isRemoved(), "it should have been drawn into the hall");
            helper.assertTrue(hall.isOccupied(), "the hall should be holding it");
            long drawn = level.getGameTime() - tookHold[0];
            helper.assertTrue(Math.abs(drawn - hall.captureTicks()) <= 3,
                    "a one-arm hall should draw it in over " + hall.captureTicks() + " ticks, took " + drawn);
            TestSupport.removePlayer(helper, player);
            // Breaking the controller lets its captive out; it cannot be killed, so it is removed.
            TestSupport.removeHall(helper, HALL);
            level.getEntitiesOfClass(Veiled.class, new AABB(helper.absolutePos(HALL)).inflate(8.0))
                    .forEach(TestSupport::discard);
        });
    }

    // covers: veiled.drive_off, veiled.projector
    @GameTest(template = TestSupport.FLOOR_17, batch = "veiled_drive_off", timeoutTicks = 300)
    public static void fullyPinnedWithNoHallItIsDrivenOff(GameTestHelper helper) {
        ServerPlayer player = TestSupport.player(helper, PLAYER);
        Veiled veiled = helper.spawn(ModEntities.VEILED.get(), SPAWN);
        ServerLevel level = helper.getLevel();
        helper.onEachTick(() -> {
            if (!veiled.onCooldown()) veiled.decohere(level);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(veiled.onCooldown(), "it should be driven off, on cooldown");
            helper.assertValueEqual(veiled.state(), Veiled.State.WANDER, "state once driven off");
            helper.assertValueEqual(veiled.coherence(), 1.0f, "coherence once driven off");
            TestSupport.discard(veiled);
            TestSupport.removePlayer(helper, player);
        });
    }

    // covers: veiled.presence
    @GameTest(template = TestSupport.FLOOR_17, batch = "veiled_presence", timeoutTicks = 100)
    public static void atAMachineItFeeds(GameTestHelper helper) {
        ServerPlayer player = TestSupport.player(helper, PLAYER);
        // A stabiliser with no anchor draws nothing itself, so every FE it loses is the Veiled's.
        BlockPos machinePos = new BlockPos(8, 2, 6);
        helper.setBlock(machinePos, ModBlocks.RIFT_STABILISER.get());
        RiftStabiliserBlockEntity machine = helper.getBlockEntity(machinePos, RiftStabiliserBlockEntity.class);
        TestSupport.fill(machine.getEnergyStorage());
        Veiled veiled = helper.spawn(ModEntities.VEILED.get(), new BlockPos(8, 2, 4));
        veiled.releasedFrom(helper.absolutePos(machinePos));
        helper.runAfterDelay(20, () -> {
            helper.assertValueEqual(veiled.state(), Veiled.State.PRESENCE, "state at its machine");
            int before = machine.getEnergyStorage().getEnergyStored();
            helper.runAfterDelay(20, () -> {
                int drained = before - machine.getEnergyStorage().getEnergyStored();
                helper.assertValueEqual(drained, 20 * 80, "FE it drains from the machine in a second");
                TestSupport.discard(veiled);
                TestSupport.removePlayer(helper, player);
                helper.succeed();
            });
        });
    }

    // covers: veiled.hall_release
    @GameTest(template = TestSupport.FLOOR_17, batch = "veiled_hall_release", timeoutTicks = 300)
    public static void outOfAFailedHallItFeedsOnTheHall(GameTestHelper helper) {
        ServerPlayer player = TestSupport.player(helper, PLAYER);
        ContainmentHallBlockEntity hall = TestSupport.buildHall(helper, HALL, 1);
        hall.getResidue().setStackInSlot(0, new ItemStack(ModItems.RIFT_RESIDUE.get(), 4));
        hall.contain();
        ServerLevel level = helper.getLevel();
        AABB around = new AABB(helper.absolutePos(HALL)).inflate(8.0);
        helper.succeedWhen(() -> {
            helper.assertTrue(!hall.isOccupied(), "unpowered, the hall should let it go");
            List<Veiled> out = level.getEntitiesOfClass(Veiled.class, around);
            helper.assertValueEqual(out.size(), 1, "Veiled released beside the hall");
            helper.assertValueEqual(out.get(0).state(), Veiled.State.PRESENCE, "state of a released Veiled");
            out.forEach(TestSupport::discard);
            TestSupport.removePlayer(helper, player);
            TestSupport.removeHall(helper, HALL);
        });
    }

    // covers: veiled.persistence
    @GameTest(template = TestSupport.FLOOR_17, batch = "veiled_persistence", timeoutTicks = 300)
    public static void cooldownAndStateSurviveASave(GameTestHelper helper) {
        ServerPlayer player = TestSupport.player(helper, PLAYER);
        Veiled veiled = helper.spawn(ModEntities.VEILED.get(), SPAWN);
        ServerLevel level = helper.getLevel();
        helper.onEachTick(() -> {
            if (!veiled.onCooldown() && !veiled.isRemoved()) veiled.decohere(level);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(veiled.onCooldown(), "drive it off first");
            TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            veiled.saveWithoutId(saved);
            Veiled loaded = ModEntities.VEILED.get().create(level, EntitySpawnReason.LOAD);
            loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved.buildResult()));
            helper.assertTrue(loaded.onCooldown(), "its cooldown should survive a save");
            helper.assertValueEqual(loaded.state(), veiled.state(), "state after a save");
            TestSupport.discard(veiled);
            TestSupport.removePlayer(helper, player);
        });
    }

    // covers: veiled.one_per_area
    @GameTest(template = TestSupport.FLOOR_17, batch = "veiled_area", timeoutTicks = 40)
    public static void onlyOnePerArea(GameTestHelper helper) {
        ServerPlayer player = TestSupport.player(helper, PLAYER);
        ServerLevel level = helper.getLevel();
        BlockPos far = helper.absolutePos(HALL).offset(200, 0, 0);
        Veiled veiled = helper.spawn(ModEntities.VEILED.get(), SPAWN);
        helper.assertTrue(!VeiledManager.canRelease(level, far), "none may be released within 256 blocks of another");
        TestSupport.discard(veiled);
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(VeiledManager.canRelease(level, far), "with it gone, the area is free");
            TestSupport.removePlayer(helper, player);
            helper.succeed();
        });
    }

    // covers: veiled.despawn
    @GameTest(template = TestSupport.FLOOR_17, batch = "veiled_despawn", timeoutTicks = 60)
    public static void goneWithNobodyToHaunt(GameTestHelper helper) {
        Veiled veiled = helper.spawn(ModEntities.VEILED.get(), SPAWN);
        helper.succeedWhen(() -> helper.assertTrue(veiled.isRemoved(), "with no player in the area it should be gone"));
    }

    // covers: veiled.recover
    @GameTest(template = TestSupport.FLOOR_17, batch = "veiled_recover", timeoutTicks = 200)
    public static void leftAloneItPullsItselfTogether(GameTestHelper helper) {
        ServerPlayer player = TestSupport.player(helper, PLAYER);
        Veiled veiled = helper.spawn(ModEntities.VEILED.get(), SPAWN);
        ServerLevel level = helper.getLevel();
        for (int i = 0; i < 40; i++) veiled.decohere(level);
        float worn = veiled.coherence();
        helper.assertTrue(worn < 0.76f, "forty ticks of beam should wear it to three quarters, coherence " + worn);
        // One tick of wear heals every three ticks: 40 ticks of wear back in 120.
        helper.runAfterDelay(60, () -> helper.assertTrue(veiled.coherence() > worn && veiled.coherence() < 1.0f,
                "half way back, coherence " + veiled.coherence()));
        helper.runAfterDelay(130, () -> {
            helper.assertValueEqual(veiled.coherence(), 1.0f, "coherence once healed");
            TestSupport.discard(veiled);
            TestSupport.removePlayer(helper, player);
            helper.succeed();
        });
    }
}

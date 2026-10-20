package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.work.WorkQueue;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.ArrayList;
import java.util.List;

/** The background work queue: within its budget, most urgent first, nothing left behind, a failure contained. */
public final class WorkQueueTests {

    private WorkQueueTests() {}

    // covers: work.queue
    @GameTest(template = TestSupport.FLOOR_9, batch = "work_queue", timeoutTicks = 100)
    public static void slowWorkStillGetsDoneAStepATick(GameTestHelper helper) {
        // A job whose every step overruns the budget is still stepped once a tick, so it gets there.
        int[] steps = {0};
        Object key = new Object();
        WorkQueue.submit(key, WorkQueue.NORMAL, () -> {
            long until = System.nanoTime() + 3_000_000;
            while (System.nanoTime() < until) Thread.onSpinWait();
            return ++steps[0] >= 5;
        });
        helper.succeedWhen(() -> {
            helper.assertFalse(WorkQueue.waiting(key), "done after " + steps[0] + " steps");
            helper.assertValueEqual(steps[0], 5, "five steps");
        });
    }

    // covers: work.queue
    @GameTest(template = TestSupport.FLOOR_9, batch = "work_queue_order", timeoutTicks = 100)
    public static void urgentWorkGoesFirst(GameTestHelper helper) {
        // Drained, the urgent job runs before the others, and a job queued again replaces the one waiting.
        List<String> ran = new ArrayList<>();
        Object a = new Object(), b = new Object();
        WorkQueue.submit(a, WorkQueue.NORMAL, () -> ran.add("a old"));
        WorkQueue.submit(a, WorkQueue.NORMAL, () -> ran.add("a"));
        WorkQueue.submit(b, WorkQueue.URGENT, () -> ran.add("b"));
        WorkQueue.drain();
        helper.assertValueEqual(ran, List.of("b", "a"), "urgent first, the newest job for a thing");
        helper.succeed();
    }

    // covers: work.queue
    @GameTest(template = TestSupport.FLOOR_9, batch = "work_queue_failure", timeoutTicks = 100)
    public static void aFailingJobIsDroppedAndTheRestCarryOn(GameTestHelper helper) {
        boolean[] ran = {false};
        Object bad = new Object(), good = new Object();
        WorkQueue.submit(bad, WorkQueue.URGENT, () -> {
            throw new IllegalStateException("on purpose, in a test");
        });
        WorkQueue.submit(good, WorkQueue.NORMAL, () -> ran[0] = true);
        helper.succeedWhen(() -> {
            helper.assertFalse(WorkQueue.waiting(bad), "the failing job is let go");
            helper.assertTrue(ran[0], "the other ran");
        });
    }

    // covers: work.queue
    @GameTest(template = TestSupport.FLOOR_17, batch = "work_queue_flora", timeoutTicks = 200)
    public static void floraGrowsAtItsOwnPaceNearSomeoneInTheMirror(GameTestHelper helper) {
        // A chunk at Singularity flux with a player in the mirror beside it: its flora grows within a few seconds,
        // as it always did. (With no one in the mirror it grows every half minute, in the background.)
        net.minecraft.core.BlockPos centre = new net.minecraft.core.BlockPos(8, 2, 8);
        TestSupport.setField(helper, centre, 5000.0, 0.0);
        var player = TestSupport.player(helper, centre.above());
        com.kadikular.quantimium.phase.MirrorPhase.enter(player, false);
        var chunk = helper.getLevel().getChunkAt(helper.absolutePos(centre));
        helper.succeedWhen(() -> {
            helper.assertTrue(com.kadikular.quantimium.phase.MirrorPhase.isPhased(player), "in the mirror");
            helper.assertFalse(chunk.getData(com.kadikular.quantimium.init.ModAttachments.MIRROR_FLORA).isEmpty(), "flora has grown");
            TestSupport.removePlayer(helper, player);
        });
    }
}

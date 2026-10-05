package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.util.LegacyEnergy;
import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.PodCradleBlock;
import com.kadikular.quantimium.block.SuperpositionPodBlock;
import com.kadikular.quantimium.block.entity.RelayModuleBlockEntity;
import com.kadikular.quantimium.block.entity.SuperpositionPodBlockEntity;
import com.kadikular.quantimium.block.entity.UnfoldingArrayBlockEntity;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.item.SophonBinding;
import com.kadikular.quantimium.item.SophonItem;
import com.kadikular.quantimium.superposition.Relay;
import com.kadikular.quantimium.superposition.SophonRegistry;
import com.kadikular.quantimium.superposition.StashContainer;
import com.kadikular.quantimium.superposition.Superposition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import com.kadikular.quantimium.entity.FieldDouble;
import com.kadikular.quantimium.item.TetherItem;
import com.kadikular.quantimium.superposition.Recovery;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import com.kadikular.quantimium.entity.Veiled;
import com.kadikular.quantimium.init.ModEntities;
import com.kadikular.quantimium.block.entity.DecoherenceProjectorBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.List;
import java.util.UUID;

/**
 * The Superposition Pod, the Unfolding Array and the Sophons between them: making a double, swapping
 * into one, folding one up, and the death save. Wiki: multiblocks/superposition-pod.
 */
public final class SuperpositionTests {

    private static final BlockPos POD_A = new BlockPos(4, 3, 4);
    private static final BlockPos POD_B = new BlockPos(12, 3, 12);
    private static final BlockPos PAD = new BlockPos(8, 2, 8);

    private SuperpositionTests() {}

    // covers: pod.form
    @GameTest(template = TestSupport.FLOOR_17, batch = "pod_form", timeoutTicks = 20)
    public static void theCradleMakesThePodWhole(GameTestHelper helper) {
        TestSupport.track(helper);
        SuperpositionPodBlockEntity pod = pod(helper, POD_A, ModBlocks.POD_PLATING.get());
        helper.assertTrue(pod.isFormed(), "a whole cradle forms the pod");
        helper.assertTrue(helper.getBlockState(POD_A.offset(-1, -1, -1)).getValue(PodCradleBlock.FORMED), "the cradle lights");
        helper.setBlock(POD_A.offset(0, -1, 1), Blocks.STONE);
        helper.assertTrue(!pod.isFormed(), "a missing edge breaks it");
        helper.setBlock(POD_A.offset(0, -1, 1), ModBlocks.POD_RESCUE_MODULE.get());
        helper.assertTrue(pod.isFormed() && pod.hasModule(com.kadikular.quantimium.superposition.PodModule.RESCUE),
                "a module fills an edge and is counted");
        helper.succeed();
    }

    // covers: pod.swap
    @GameTest(template = TestSupport.FLOOR_17, batch = "pod_swap", timeoutTicks = 20)
    public static void aSwapLeavesYourBodyBehindAndWakesYouInTheDouble(GameTestHelper helper) {
        TestSupport.track(helper);
        SuperpositionPodBlockEntity a = pod(helper, POD_A, ModBlocks.POD_PLATING.get());
        SuperpositionPodBlockEntity b = pod(helper, POD_B, ModBlocks.POD_PLATING.get());
        ServerPlayer player = TestSupport.player(helper, POD_A.offset(0, 0, 3));
        UUID sophon = unfoldInto(helper, player, b);
        a.claim(player);
        player.snapTo(helper.absolutePos(POD_A).getX() + 0.5, helper.absolutePos(POD_A).getY() + 0.0625,
                helper.absolutePos(POD_A).getZ() + 0.5);
        int before = a.getEnergyStorage().getEnergyStored();
        int cost = Superposition.cost(a.globalPos(), b.globalPos());

        Superposition.swap(player, helper.absolutePos(POD_A), sophon);

        helper.assertTrue(player.blockPosition().equals(helper.absolutePos(POD_B)), "you wake in the far pod");
        helper.assertTrue(!b.hasDouble(), "the double you took over is gone from its pod");
        helper.assertValueEqual(a.occupant(), sophon, "the body you left stays in the pod you left");
        SophonRegistry.Entry entry = SophonRegistry.get(helper.getLevel().getServer()).entry(sophon);
        helper.assertTrue(entry != null && entry.pod().isPresent() && entry.pod().get().equals(a.globalPos()),
                "the Sophon follows the double");
        helper.assertValueEqual(before - a.getEnergyStorage().getEnergyStored(), cost, "the pod you leave pays");
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    // covers: pod.power
    @GameTest(template = TestSupport.FLOOR_17, batch = "pod_power", timeoutTicks = 20)
    public static void withoutPowerThePodWillNotSwap(GameTestHelper helper) {
        TestSupport.track(helper);
        SuperpositionPodBlockEntity a = pod(helper, POD_A, ModBlocks.POD_PLATING.get());
        SuperpositionPodBlockEntity b = pod(helper, POD_B, ModBlocks.POD_PLATING.get());
        a.getEnergyStorage().setEnergy(0);
        ServerPlayer player = TestSupport.player(helper, POD_A.offset(0, 0, 3));
        UUID sophon = unfoldInto(helper, player, b);
        player.snapTo(helper.absolutePos(POD_A).getX() + 0.5, helper.absolutePos(POD_A).getY() + 0.0625,
                helper.absolutePos(POD_A).getZ() + 0.5);
        Superposition.swap(player, helper.absolutePos(POD_A), sophon);
        helper.assertTrue(player.blockPosition().equals(helper.absolutePos(POD_A)), "you stay where you are");
        helper.assertTrue(b.hasDouble(), "the double stays in its pod");
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    // covers: pod.fold
    @GameTest(template = TestSupport.FLOOR_17, batch = "pod_fold", timeoutTicks = 20)
    public static void aDoubleFoldsBackIntoItsSophon(GameTestHelper helper) {
        TestSupport.track(helper);
        SuperpositionPodBlockEntity b = pod(helper, POD_B, ModBlocks.POD_PLATING.get());
        ServerPlayer player = TestSupport.player(helper, POD_A);
        UUID sophon = unfoldInto(helper, player, b);
        b.fold(player);
        helper.assertTrue(!b.hasDouble(), "the pod stands empty");
        boolean carried = false;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            SophonBinding binding = stack.get(ModDataComponents.SOPHON.get());
            if (binding != null && binding.id().equals(sophon)) carried = true;
        }
        helper.assertTrue(carried, "its Sophon comes back to you");
        SophonRegistry.Entry entry = SophonRegistry.get(helper.getLevel().getServer()).entry(sophon);
        helper.assertTrue(entry != null && !entry.inPod(), "the registry has it folded");
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    // covers: pod.rescue
    @GameTest(template = TestSupport.FLOOR_17, batch = "pod_rescue", timeoutTicks = 20)
    public static void dyingWakesYouAtYourAnchorAndSpendsItsSophon(GameTestHelper helper) {
        TestSupport.track(helper);
        SuperpositionPodBlockEntity anchor = pod(helper, POD_B, ModBlocks.POD_RESCUE_MODULE.get());
        ServerPlayer player = TestSupport.player(helper, POD_A, GameType.SURVIVAL);
        UUID sophon = unfoldInto(helper, player, anchor);
        Superposition.toggleAnchor(player, helper.absolutePos(POD_B));
        // A fake player never dies (its die() does nothing), so the death is posted as a real one would be.
        player.setHealth(0.0f);
        LivingDeathEvent death = new LivingDeathEvent(player, player.damageSources().fellOutOfWorld());
        NeoForge.EVENT_BUS.post(death);
        helper.assertTrue(death.isCanceled(), "the death is called off");
        helper.assertTrue(player.getHealth() == player.getMaxHealth(), "alive, in a whole body");
        helper.assertTrue(player.blockPosition().equals(helper.absolutePos(POD_B)), "woken in the Anchor");
        helper.assertTrue(!anchor.hasDouble(), "the Anchor's double is you now");
        helper.assertTrue(SophonRegistry.get(helper.getLevel().getServer()).entry(sophon) == null, "that Sophon is spent");
        // With it spent, the next death is a death.
        helper.assertTrue(!Superposition.rescue(player), "no second rescue without a new Sophon");
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    // covers: array.unfold
    @GameTest(template = TestSupport.FLOOR_17, batch = "array_unfold", timeoutTicks = UnfoldingArrayBlockEntity.UNFOLD_TICKS + 60)
    public static void standingOnThePadUnfoldsASophon(GameTestHelper helper) {
        UnfoldingArrayBlockEntity array = array(helper);
        ServerPlayer player = TestSupport.player(helper, PAD);
        player.snapTo(helper.absolutePos(PAD).getX() + 0.5, helper.absolutePos(PAD).getY() + 0.375,
                helper.absolutePos(PAD).getZ() + 0.5);
        array.pressButton(player);
        helper.assertValueEqual(array.status(), UnfoldingArrayBlockEntity.STATUS_UNFOLDING, "status once started");
        helper.succeedWhen(() -> {
            ItemStack out = array.getInventory().getStackInSlot(UnfoldingArrayBlockEntity.OUTPUT_SLOT);
            helper.assertTrue(out.is(ModItems.SOPHON.get()), "a Sophon comes out");
            SophonBinding binding = out.get(ModDataComponents.SOPHON.get());
            helper.assertTrue(binding != null && binding.owner().equals(player.getUUID()), "bound to who stood there");
            helper.assertValueEqual(SophonRegistry.get(helper.getLevel().getServer()).count(player.getUUID()), 1, "counted");
            helper.assertTrue(array.getInventory().getStackInSlot(UnfoldingArrayBlockEntity.TESSERACT_SLOT).isEmpty(), "the tesseract is used");
            TestSupport.removePlayer(helper, player);
        });
    }

    // covers: array.cap
    @GameTest(template = TestSupport.FLOOR_17, batch = "array_cap", timeoutTicks = 20)
    public static void theSoulCanOnlyBeSplitSoManyTimes(GameTestHelper helper) {
        UnfoldingArrayBlockEntity array = array(helper);
        ServerPlayer player = TestSupport.player(helper, PAD);
        SophonRegistry registry = SophonRegistry.get(helper.getLevel().getServer());
        for (int i = 0; i < Config.maxDoubles(); i++) registry.create(player.getUUID());
        array.pressButton(player);
        helper.assertValueEqual(array.status(), UnfoldingArrayBlockEntity.STATUS_AT_CAP, "refused at the cap");
        helper.assertTrue(array.subject() == null, "nobody is being unfolded");
        registry.forget(player.getUUID());
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    // covers: sophon.void
    @GameTest(template = TestSupport.FLOOR_9, batch = "sophon_void", timeoutTicks = 20)
    public static void aSophonLostToTheVoidStopsCounting(GameTestHelper helper) {
        TestSupport.track(helper);
        SophonRegistry registry = SophonRegistry.get(helper.getLevel().getServer());
        UUID owner = UUID.randomUUID();
        UUID sophon = registry.create(owner);
        BlockPos at = helper.absolutePos(new BlockPos(4, 3, 4));
        ItemEntity item = new ItemEntity(helper.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
                SophonItem.of(sophon, owner, "lost"));
        helper.getLevel().addFreshEntity(item);
        helper.assertValueEqual(item.lifespan, Integer.MAX_VALUE, "a Sophon never despawns");
        helper.assertTrue(!item.hurtServer(helper.getLevel(), helper.getLevel().damageSources().lava(), 100.0f) || item.isAlive(), "lava does not touch it");
        item.kill(helper.getLevel());
        helper.assertTrue(registry.entry(sophon) == null, "killed, it stops counting");
        helper.succeed();
    }

    // covers: pod.buffs
    @GameTest(template = TestSupport.FLOOR_17, batch = "pod_buffs", timeoutTicks = 60)
    public static void aDoubleKeepsItsOwnerMendedAndHardened(GameTestHelper helper) {
        TestSupport.clearField(helper);
        TestSupport.track(helper);
        SuperpositionPodBlockEntity pod = pod(helper, POD_B, ModBlocks.POD_REGENERATION_MODULE.get(),
                ModBlocks.POD_HARDENING_MODULE.get(), ModBlocks.POD_PLATING.get(), ModBlocks.POD_PLATING.get());
        ServerPlayer player = TestSupport.player(helper, POD_A, GameType.SURVIVAL);
        unfoldInto(helper, player, pod);
        int before = pod.getEnergyStorage().getEnergyStored();
        helper.succeedWhen(() -> {
            helper.assertTrue(player.hasEffect(MobEffects.REGENERATION), "Regeneration from the double");
            helper.assertTrue(player.hasEffect(MobEffects.RESISTANCE), "Resistance from the double");
            helper.assertTrue(pod.getEnergyStorage().getEnergyStored() < before, "the pod pays for them");
            TestSupport.removePlayer(helper, player);
        });
    }

    // covers: pod.ward
    @GameTest(template = TestSupport.FLOOR_17, batch = "pod_ward", timeoutTicks = 20 * (SuperpositionPodBlockEntity.KNOCK_OUT_STRIKES + 4))
    public static void somethingAtTheGlassKnocksAnUnguardedDoubleOut(GameTestHelper helper) {
        TestSupport.track(helper);
        SuperpositionPodBlockEntity open = pod(helper, POD_A, ModBlocks.POD_PLATING.get());
        SuperpositionPodBlockEntity warded = pod(helper, POD_B, ModBlocks.POD_WARD_MODULE.get());
        ServerPlayer player = TestSupport.player(helper, new BlockPos(8, 2, 8));
        UUID loose = unfoldInto(helper, player, open);
        unfoldInto(helper, player, warded);
        for (BlockPos at : new BlockPos[]{POD_A, POD_B}) {
            // Standing on the step in front of each pod, held still so the test does not depend on pathing.
            Husk husk = helper.spawn(EntityType.HUSK, at.offset(0, 0, 1));
            husk.setNoAi(true);
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(!open.hasDouble(), "the unguarded double is knocked out");
            helper.assertTrue(warded.hasDouble(), "the warded double stays");
            SophonRegistry.Entry entry = SophonRegistry.get(helper.getLevel().getServer()).entry(loose);
            helper.assertTrue(entry != null && !entry.inPod(), "its Sophon is folded, not lost");
            helper.assertEntityPresent(EntityType.ITEM, POD_A, 2.0);
            TestSupport.removePlayer(helper, player);
            helper.killAllEntitiesOfClass(Husk.class);
        });
    }

    // covers: pod.stash
    @GameTest(template = TestSupport.FLOOR_17, batch = "pod_stash", timeoutTicks = 20)
    public static void theStashOpensFromAnywhereAndKeepsWhatIsPutIn(GameTestHelper helper) {
        TestSupport.track(helper);
        ServerPlayer player = TestSupport.player(helper, POD_A);
        SophonRegistry registry = SophonRegistry.get(helper.getLevel().getServer());
        helper.assertTrue(!registry.hasStash(player.getUUID()), "no stash without a Stash module");
        SuperpositionPodBlockEntity pod = pod(helper, POD_B, ModBlocks.POD_STASH_MODULE.get());
        unfoldInto(helper, player, pod);
        helper.assertTrue(registry.hasStash(player.getUUID()), "a double in a Stash pod opens it");
        // A fake player cannot open a menu, so the chest the stash opens as is used directly.
        StashContainer stash = new StashContainer(registry.stash(player.getUUID()));
        stash.setItem(0, new ItemStack(Items.DIAMOND, 3));
        stash.stopOpen(player);
        ItemStack kept = registry.stash(player.getUUID()).get(0);
        helper.assertTrue(kept.is(Items.DIAMOND) && kept.getCount() == 3, "what went in is kept");
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    // covers: pod.charge
    @GameTest(template = TestSupport.FLOOR_17, batch = "pod_charge", timeoutTicks = 60)
    public static void aChargeModuleChargesWhatYouCarry(GameTestHelper helper) {
        TestSupport.clearField(helper);
        TestSupport.track(helper);
        SuperpositionPodBlockEntity pod = pod(helper, POD_B, ModBlocks.POD_CHARGE_MODULE.get());
        ServerPlayer player = TestSupport.player(helper, POD_A);
        ItemStack lance = new ItemStack(ModItems.DECOHERENCE_LANCE.get());
        player.getInventory().add(lance);
        unfoldInto(helper, player, pod);
        helper.succeedWhen(() -> {
            int stored = 0;
            for (ItemStack carried : player.getInventory().getNonEquipmentItems()) {
                if (!carried.is(ModItems.DECOHERENCE_LANCE.get())) continue;
                var battery = LegacyEnergy.item(carried);
                if (battery != null) stored = battery.getEnergyStored();
            }
            helper.assertTrue(stored > 0, "the lance is charged from the pod");
            TestSupport.removePlayer(helper, player);
        });
    }

    // covers: pod.listing
    @GameTest(template = TestSupport.FLOOR_17, batch = "pod_listing", timeoutTicks = 20)
    public static void anUnlistedPodIsNotSwappedInto(GameTestHelper helper) {
        TestSupport.track(helper);
        SuperpositionPodBlockEntity a = pod(helper, POD_A, ModBlocks.POD_PLATING.get());
        SuperpositionPodBlockEntity b = pod(helper, POD_B, ModBlocks.POD_PLATING.get());
        ServerPlayer player = TestSupport.player(helper, POD_A.offset(0, 0, 3));
        UUID sophon = unfoldInto(helper, player, b);
        b.toggleListed();
        SophonRegistry.Entry entry = SophonRegistry.get(helper.getLevel().getServer()).entry(sophon);
        helper.assertTrue(entry != null && !entry.listed(), "the registry knows it is unlisted");
        player.snapTo(helper.absolutePos(POD_A).getX() + 0.5, helper.absolutePos(POD_A).getY() + 0.0625,
                helper.absolutePos(POD_A).getZ() + 0.5);
        Superposition.swap(player, helper.absolutePos(POD_A), sophon);
        helper.assertTrue(b.hasDouble() && player.blockPosition().equals(helper.absolutePos(POD_A)),
                "an unlisted pod keeps its double");
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    // covers: relay.trip, relay.network
    @GameTest(template = TestSupport.FLOOR_17, batch = "relay_trip", timeoutTicks = Relay.FORMING_TICKS + 40)
    public static void aRelaySendsASpareDoubleAndSwapsYouIn(GameTestHelper helper) {
        TestSupport.track(helper);
        SuperpositionPodBlockEntity hub = pod(helper, POD_A, ModBlocks.POD_RELAY_MODULE.get(), ModBlocks.POD_PLATING.get(),
                ModBlocks.POD_PLATING.get(), ModBlocks.POD_PLATING.get());
        SuperpositionPodBlockEntity spare = pod(helper, POD_B, ModBlocks.POD_PLATING.get());
        SuperpositionPodBlockEntity outpost = pod(helper, POD_C, ModBlocks.POD_PLATING.get());
        ServerPlayer player = TestSupport.player(helper, POD_A.offset(0, 0, 3));
        UUID sophon = unfoldInto(helper, player, spare);
        outpost.claim(player);
        relay(hub).getInventory().setStackInSlot(0, boundTo(helper, POD_C));
        // Off the network, the spare double is left alone.
        standIn(helper, player, POD_A);
        Relay.startTrip(player, helper.absolutePos(POD_A), Relay.destinationId(outpost.globalPos()));
        helper.assertTrue(spare.hasDouble() && !outpost.hasDouble(), "a double off the network is not sent");
        relay(hub).getInventory().setStackInSlot(1, boundTo(helper, POD_B));
        Relay.startTrip(player, helper.absolutePos(POD_A), Relay.destinationId(outpost.globalPos()));
        helper.assertTrue(!spare.hasDouble() && sophon.equals(outpost.occupant()), "the spare double goes to the outpost");
        helper.succeedWhen(() -> {
            helper.assertTrue(player.blockPosition().equals(helper.absolutePos(POD_C)), "you are swapped in once it has formed");
            helper.assertValueEqual(hub.occupant(), sophon, "your body waits in the hub for the way back");
            TestSupport.removePlayer(helper, player);
        });
    }

    // covers: relay.cancel
    @GameTest(template = TestSupport.FLOOR_17, batch = "relay_cancel", timeoutTicks = Relay.FORMING_TICKS + 40)
    public static void steppingOutCallsTheSwapOff(GameTestHelper helper) {
        TestSupport.track(helper);
        SuperpositionPodBlockEntity hub = pod(helper, POD_A, ModBlocks.POD_RELAY_MODULE.get(), ModBlocks.POD_PLATING.get(),
                ModBlocks.POD_PLATING.get(), ModBlocks.POD_PLATING.get());
        SuperpositionPodBlockEntity spare = pod(helper, POD_B, ModBlocks.POD_PLATING.get());
        SuperpositionPodBlockEntity outpost = pod(helper, POD_C, ModBlocks.POD_PLATING.get());
        ServerPlayer player = TestSupport.player(helper, POD_A.offset(0, 0, 3));
        unfoldInto(helper, player, spare);
        outpost.claim(player);
        relay(hub).getInventory().setStackInSlot(0, boundTo(helper, POD_C));
        relay(hub).getInventory().setStackInSlot(1, boundTo(helper, POD_B));
        standIn(helper, player, POD_A);
        Relay.startTrip(player, helper.absolutePos(POD_A), Relay.destinationId(outpost.globalPos()));
        BlockPos outside = helper.absolutePos(POD_A.offset(0, -1, 3));
        player.snapTo(outside.getX() + 0.5, outside.getY(), outside.getZ() + 0.5);
        helper.runAfterDelay(Relay.FORMING_TICKS + 10, () -> {
            helper.assertTrue(!player.blockPosition().equals(helper.absolutePos(POD_C)), "no swap once you step out");
            helper.assertTrue(outpost.hasDouble() && !hub.hasDouble(), "the double stays where it went");
            TestSupport.removePlayer(helper, player);
            helper.succeed();
        });
    }

    // covers: relay.recall
    @GameTest(template = TestSupport.FLOOR_17, batch = "relay_recall", timeoutTicks = 20)
    public static void aRelayRecallsAndSendsSophons(GameTestHelper helper) {
        TestSupport.track(helper);
        SuperpositionPodBlockEntity hub = pod(helper, POD_A, ModBlocks.POD_RELAY_MODULE.get(), ModBlocks.POD_PLATING.get(),
                ModBlocks.POD_PLATING.get(), ModBlocks.POD_PLATING.get());
        SuperpositionPodBlockEntity far = pod(helper, POD_B, ModBlocks.POD_PLATING.get());
        SuperpositionPodBlockEntity outpost = pod(helper, POD_C, ModBlocks.POD_PLATING.get());
        ServerPlayer player = TestSupport.player(helper, POD_A.offset(0, 0, 3));
        UUID sophon = unfoldInto(helper, player, far);
        hub.claim(player);
        outpost.claim(player);
        RelayModuleBlockEntity relay = relay(hub);
        relay.getInventory().setStackInSlot(0, boundTo(helper, POD_B));
        relay.getInventory().setStackInSlot(1, boundTo(helper, POD_C.above()));
        Relay.recall(player, relay, 0);
        helper.assertTrue(!far.hasDouble(), "the double is recalled");
        SophonBinding held = relay.getInventory().getStackInSlot(RelayModuleBlockEntity.SOPHON_SLOT).get(ModDataComponents.SOPHON.get());
        helper.assertTrue(held != null && held.id().equals(sophon), "into the Relay's Sophon slot");
        Relay.send(player, relay, 1);
        helper.assertValueEqual(outpost.occupant(), sophon, "and sent on to another pod (bound by its top half)");
        helper.assertTrue(relay.getInventory().getStackInSlot(RelayModuleBlockEntity.SOPHON_SLOT).isEmpty(), "the slot is empty again");
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    // covers: relay.spare
    @GameTest(template = TestSupport.FLOOR_17, batch = "relay_spare", timeoutTicks = 20)
    public static void theRelayNeverSendsTheAnchorsDouble(GameTestHelper helper) {
        TestSupport.track(helper);
        SuperpositionPodBlockEntity hub = pod(helper, POD_A, ModBlocks.POD_RELAY_MODULE.get(), ModBlocks.POD_PLATING.get(),
                ModBlocks.POD_PLATING.get(), ModBlocks.POD_PLATING.get());
        SuperpositionPodBlockEntity anchor = pod(helper, POD_B, ModBlocks.POD_RESCUE_MODULE.get());
        SuperpositionPodBlockEntity outpost = pod(helper, POD_C, ModBlocks.POD_PLATING.get());
        ServerPlayer player = TestSupport.player(helper, POD_A.offset(0, 0, 3));
        unfoldInto(helper, player, anchor);
        Superposition.toggleAnchor(player, helper.absolutePos(POD_B));
        outpost.claim(player);
        relay(hub).getInventory().setStackInSlot(0, boundTo(helper, POD_C));
        relay(hub).getInventory().setStackInSlot(1, boundTo(helper, POD_B));
        standIn(helper, player, POD_A);
        Relay.startTrip(player, helper.absolutePos(POD_A), Relay.destinationId(outpost.globalPos()));
        helper.assertTrue(anchor.hasDouble() && !outpost.hasDouble(), "the Anchor keeps its double");
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    // covers: tether.swap, tether.back
    @GameTest(template = TestSupport.FLOOR_17, batch = "tether_swap", timeoutTicks = 20)
    public static void theTetherSwapsFromTheFieldAndLeavesABodyThere(GameTestHelper helper) {
        TestSupport.track(helper);
        SuperpositionPodBlockEntity home = pod(helper, POD_B, ModBlocks.POD_PLATING.get());
        SuperpositionPodBlockEntity back = pod(helper, POD_A, ModBlocks.POD_PLATING.get());
        ServerPlayer player = TestSupport.player(helper, FIELD, GameType.SURVIVAL);
        UUID sophon = unfoldInto(helper, player, home);
        ItemStack tether = tether(player);
        BlockPos field = player.blockPosition();

        Superposition.tetherSwap(player, sophon);
        helper.assertTrue(player.blockPosition().equals(helper.absolutePos(POD_B)), "you wake in the pod");
        helper.assertTrue(!home.hasDouble(), "the double you took over is gone from its pod");
        FieldDouble left = fieldDouble(helper, field);
        helper.assertTrue(left != null && sophon.equals(left.sophon()), "your body stands where you were");
        helper.assertTrue(TetherItem.energy(tether) < TetherItem.CAPACITY, "the Tether pays");
        SophonRegistry.Entry entry = SophonRegistry.get(helper.getLevel().getServer()).entry(sophon);
        helper.assertTrue(entry != null && entry.inField(), "the registry has it in the field");

        // Back into it, from another pod.
        standIn(helper, player, POD_A);
        Superposition.swap(player, helper.absolutePos(POD_A), sophon);
        helper.assertTrue(player.blockPosition().equals(field), "you wake where you left your body");
        helper.assertTrue(!left.isAlive() && sophon.equals(back.occupant()), "the pod you left holds the body now");
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    // covers: field.knockout
    @GameTest(template = TestSupport.FLOOR_17, batch = "field_knockout", timeoutTicks = 20)
    public static void aFieldDoubleHoldsStillAndIsKnockedOutIntoItsSophon(GameTestHelper helper) {
        TestSupport.track(helper);
        SuperpositionPodBlockEntity home = pod(helper, POD_B, ModBlocks.POD_PLATING.get());
        ServerPlayer player = TestSupport.player(helper, FIELD, GameType.SURVIVAL);
        UUID sophon = unfoldInto(helper, player, home);
        tether(player);
        BlockPos field = player.blockPosition();
        Superposition.tetherSwap(player, sophon);
        FieldDouble left = fieldDouble(helper, field);
        helper.assertTrue(left != null, "a body is left standing");
        double x = left.getX();
        left.knockback(2.0, 1.0, 0.0);
        left.push(1.0, 0.0, 0.0);
        left.tick();
        helper.assertTrue(left.getX() == x, "it cannot be moved");
        // The Tether never swaps into a body in the field.
        Superposition.tetherSwap(player, sophon);
        helper.assertTrue(left.isAlive() && player.blockPosition().equals(helper.absolutePos(POD_B)),
                "the Tether does not reach a field double");
        left.hurt(helper.getLevel().damageSources().generic(), 100.0f);
        helper.assertTrue(!left.isAlive(), "knocked out");
        SophonRegistry.Entry entry = SophonRegistry.get(helper.getLevel().getServer()).entry(sophon);
        helper.assertTrue(entry != null && entry.dropped(), "its Sophon lies where it fell");
        helper.assertTrue(lying(helper, field, sophon) != null, "as an item on the ground");
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    // covers: field.fold
    @GameTest(template = TestSupport.FLOOR_17, batch = "field_fold", timeoutTicks = 20)
    public static void itsOwnerFoldsAFieldDoubleUp(GameTestHelper helper) {
        TestSupport.track(helper);
        SuperpositionPodBlockEntity home = pod(helper, POD_B, ModBlocks.POD_PLATING.get());
        ServerPlayer player = TestSupport.player(helper, FIELD, GameType.SURVIVAL);
        UUID sophon = unfoldInto(helper, player, home);
        tether(player);
        BlockPos field = player.blockPosition();
        Superposition.tetherSwap(player, sophon);
        FieldDouble left = fieldDouble(helper, field);
        ServerPlayer stranger = TestSupport.player(helper, FIELD.offset(1, 0, 0), GameType.SURVIVAL);
        stranger.setShiftKeyDown(true);
        left.interact(stranger, InteractionHand.MAIN_HAND, left.position());
        helper.assertTrue(left.isAlive(), "nobody else can fold it");
        player.setShiftKeyDown(true);
        left.interact(player, InteractionHand.MAIN_HAND, left.position());
        helper.assertTrue(!left.isAlive(), "its owner folds it up");
        SophonRegistry.Entry entry = SophonRegistry.get(helper.getLevel().getServer()).entry(sophon);
        helper.assertTrue(entry != null && entry.pod().isEmpty(), "the registry has it folded");
        helper.assertTrue(player.getInventory().getNonEquipmentItems().stream().anyMatch(stack -> {
            SophonBinding binding = stack.get(ModDataComponents.SOPHON.get());
            return binding != null && binding.id().equals(sophon);
        }), "and it is in its owner's inventory");
        TestSupport.removePlayer(helper, stranger);
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    // covers: recovery.manual
    @GameTest(template = TestSupport.FLOOR_17, batch = "recovery_manual", timeoutTicks = 20)
    public static void aRecoveryPodBringsAFieldDoubleHome(GameTestHelper helper) {
        TestSupport.track(helper);
        SuperpositionPodBlockEntity home = pod(helper, POD_B, ModBlocks.POD_PLATING.get());
        SuperpositionPodBlockEntity recovery = pod(helper, POD_A, ModBlocks.POD_RECOVERY_MODULE.get());
        ServerPlayer player = TestSupport.player(helper, FIELD, GameType.SURVIVAL);
        UUID sophon = unfoldInto(helper, player, home);
        recovery.claim(player);
        tether(player);
        BlockPos field = player.blockPosition();
        Superposition.tetherSwap(player, sophon);
        FieldDouble left = fieldDouble(helper, field);
        Recovery.recover(player, helper.absolutePos(POD_A), sophon);
        helper.assertTrue(left != null && !left.isAlive(), "the field double is folded up where it stood");
        helper.assertValueEqual(recovery.occupant(), sophon, "and unfolds in the Recovery pod");
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    // covers: recovery.auto, recovery.stolen
    @GameTest(template = TestSupport.FLOOR_17, batch = "recovery_auto", timeoutTicks = 20)
    public static void aKnockedOutDoubleIsPulledHomeUnlessSomeoneTookIt(GameTestHelper helper) {
        TestSupport.track(helper);
        SuperpositionPodBlockEntity home = pod(helper, POD_B, ModBlocks.POD_PLATING.get());
        SuperpositionPodBlockEntity recovery = pod(helper, POD_A, ModBlocks.POD_RECOVERY_MODULE.get());
        ServerPlayer player = TestSupport.player(helper, FIELD, GameType.SURVIVAL);
        UUID sophon = unfoldInto(helper, player, home);
        recovery.claim(player);
        tether(player);
        BlockPos field = player.blockPosition();
        Superposition.tetherSwap(player, sophon);
        fieldDouble(helper, field).hurt(helper.getLevel().damageSources().generic(), 100.0f);
        helper.assertValueEqual(recovery.occupant(), sophon, "knocked out, it is pulled straight home");
        helper.assertTrue(lying(helper, field, sophon) == null, "and nothing is left lying there");

        // Out again, with the Recovery pod out of power so the Sophon stays where it falls; then someone takes it.
        SophonRegistry registry = SophonRegistry.get(helper.getLevel().getServer());
        player.snapTo(field.getX() + 0.5, field.getY(), field.getZ() + 0.5);
        Superposition.tetherSwap(player, sophon);
        recovery.getEnergyStorage().setEnergy(0);
        fieldDouble(helper, field).hurt(helper.getLevel().damageSources().generic(), 100.0f);
        ItemEntity item = lying(helper, field, sophon);
        helper.assertTrue(item != null, "with nothing to pull it home, it stays where it fell");
        item.setItem(ItemStack.EMPTY);
        item.discard();
        TestSupport.fill(recovery.getEnergyStorage());
        player.snapTo(field.getX() + 0.5, field.getY(), field.getZ() + 0.5);
        Recovery.recover(player, helper.absolutePos(POD_A), sophon);
        helper.assertTrue(!recovery.hasDouble(), "a Sophon somebody took cannot be recovered");
        SophonRegistry.Entry entry = registry.entry(sophon);
        helper.assertTrue(entry != null && !entry.dropped(), "the registry stops looking for it where it fell");
        registry.forget(player.getUUID());
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    // covers: veiled.takes_double
    @GameTest(template = TestSupport.FLOOR_17, batch = "veiled_double", timeoutTicks = Veiled.TAKE_TICKS + 300)
    public static void theVeiledTakesAnUnguardedDoubleAndDropsItWhenDrivenOff(GameTestHelper helper) {
        TestSupport.track(helper);
        SuperpositionPodBlockEntity pod = pod(helper, POD_B, ModBlocks.POD_PLATING.get());
        ServerPlayer player = TestSupport.player(helper, POD_A, GameType.SURVIVAL);
        UUID sophon = unfoldInto(helper, player, pod);
        // Out on the floor a few blocks off: it has to walk up to a pod it cannot get inside.
        Veiled veiled = helper.spawn(ModEntities.VEILED.get(), POD_B.offset(-4, -1, -4));
        veiled.huntDouble(sophon);
        SophonRegistry registry = SophonRegistry.get(helper.getLevel().getServer());
        helper.succeedWhen(() -> {
            helper.assertTrue(!pod.hasDouble(), "the double is gone from its pod");
            SophonRegistry.Entry entry = registry.entry(sophon);
            helper.assertTrue(entry != null && entry.taken(), "taken by the Veiled");
            helper.assertTrue(veiled.carried().contains(sophon), "which carries its Sophon");
            BlockPos where = veiled.blockPosition();
            veiled.driveOff(helper.getLevel());
            SophonRegistry.Entry after = registry.entry(sophon);
            helper.assertTrue(after != null && after.dropped(), "driven off, it lets go of it");
            helper.assertTrue(lying(helper, where, sophon) != null, "and the Sophon lies where it stood");
            TestSupport.discard(veiled);
            TestSupport.removePlayer(helper, player);
        });
    }

    // covers: veiled.drops_all, sophon.mirror_pickup
    @GameTest(template = TestSupport.FLOOR_17, batch = "veiled_drops_all", timeoutTicks = 2 * Veiled.TAKE_TICKS + 200)
    public static void drivenOffItDropsEverySophonItCarries(GameTestHelper helper) {
        TestSupport.track(helper);
        SuperpositionPodBlockEntity first = pod(helper, POD_A, ModBlocks.POD_PLATING.get());
        SuperpositionPodBlockEntity second = pod(helper, POD_B, ModBlocks.POD_PLATING.get());
        ServerPlayer player = TestSupport.player(helper, FIELD, GameType.SURVIVAL);
        UUID one = unfoldInto(helper, player, first);
        UUID two = unfoldInto(helper, player, second);
        Veiled veiled = helper.spawn(ModEntities.VEILED.get(), POD_A.offset(2, -1, 2));
        veiled.huntDouble(one);
        SophonRegistry registry = SophonRegistry.get(helper.getLevel().getServer());
        boolean[] huntingSecond = {false};
        helper.onEachTick(() -> {
            if (!huntingSecond[0] && veiled.carried().contains(one)) {
                huntingSecond[0] = true;
                veiled.huntDouble(two);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(veiled.carried().containsAll(List.of(one, two)), "it has taken both");
            BlockPos where = veiled.blockPosition();
            veiled.driveOff(helper.getLevel());
            for (UUID sophon : List.of(one, two)) {
                SophonRegistry.Entry entry = registry.entry(sophon);
                helper.assertTrue(entry != null && entry.dropped(), "each is let go of");
                ItemEntity item = lying(helper, where, sophon);
                helper.assertTrue(item != null, "each lies where it stood");
                // Someone in the mirror can pick it up.
                TestPlayer phased = TestSupport.phasedPlayer(helper, FIELD);
                ItemEntityPickupEvent.Pre pickup = new ItemEntityPickupEvent.Pre(phased, item);
                NeoForge.EVENT_BUS.post(pickup);
                helper.assertTrue(pickup.canPickup() != net.minecraft.util.TriState.FALSE,
                        "a Sophon can be picked up from the mirror");
                TestSupport.removePlayer(helper, phased);
            }
            TestSupport.discard(veiled);
            TestSupport.removePlayer(helper, player);
        });
    }

    // covers: veiled.blink
    @GameTest(template = TestSupport.FLOOR_17, batch = "veiled_blink", timeoutTicks = 200)
    public static void wallsDoNotKeepTheVeiledFromADouble(GameTestHelper helper) {
        TestSupport.track(helper);
        SuperpositionPodBlockEntity pod = pod(helper, POD_B, ModBlocks.POD_PLATING.get());
        // A sealed room of stone round the pod and its cradle.
        BlockPos centre = POD_B;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
                    boolean shell = Math.abs(dx) == 3 || Math.abs(dz) == 3 || dy == 3;
                    if (shell) helper.setBlock(centre.offset(dx, dy, dz), Blocks.STONE);
                }
            }
        }
        ServerPlayer player = TestSupport.player(helper, POD_A, GameType.SURVIVAL);
        unfoldInto(helper, player, pod);
        UUID sophon = pod.occupant();
        Veiled veiled = helper.spawn(ModEntities.VEILED.get(), new BlockPos(4, 2, 12));
        veiled.huntDouble(sophon);
        BlockPos inside = helper.absolutePos(centre);
        helper.succeedWhen(() -> {
            // Outside, against the wall, it stands 4.3 off; every spot it blinks to is at most 2.9, inside.
            double across = Math.hypot(veiled.getX() - (inside.getX() + 0.5), veiled.getZ() - (inside.getZ() + 0.5));
            helper.assertTrue(across < 3.0, "it blinks in past the walls, " + across + " away");
            TestSupport.discard(veiled);
            TestSupport.removePlayer(helper, player);
        });
    }

    // covers: veiled.blink
    @GameTest(template = TestSupport.FLOOR_17, batch = "veiled_blink_door", timeoutTicks = 200)
    public static void aShutDoorDoesNotKeepTheVeiledFromADouble(GameTestHelper helper) {
        TestSupport.track(helper);
        // A roomier room this time, with a door in the wall it comes up against, shut.
        BlockPos centre = new BlockPos(8, 3, 8);
        SuperpositionPodBlockEntity pod = pod(helper, centre, ModBlocks.POD_PLATING.get());
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
                    boolean shell = Math.abs(dx) == 5 || Math.abs(dz) == 5 || dy == 3;
                    if (shell) helper.setBlock(centre.offset(dx, dy, dz), Blocks.STONE);
                }
            }
        }
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST)
                .setValue(DoorBlock.OPEN, false);
        helper.setBlock(centre.offset(-5, -1, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(centre.offset(-5, 0, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        ServerPlayer player = TestSupport.player(helper, new BlockPos(1, 2, 1), GameType.SURVIVAL);
        UUID sophon = unfoldInto(helper, player, pod);
        Veiled veiled = helper.spawn(ModEntities.VEILED.get(), new BlockPos(1, 2, 8));
        veiled.huntDouble(sophon);
        BlockPos inside = helper.absolutePos(centre);
        helper.succeedWhen(() -> {
            // Outside, the nearest it can stand is 5.5 off; every spot it blinks to is at most 2.9, inside.
            double across = Math.hypot(veiled.getX() - (inside.getX() + 0.5), veiled.getZ() - (inside.getZ() + 0.5));
            helper.assertTrue(across < 3.0, "it blinks in past the door, " + across + " away");
            TestSupport.discard(veiled);
            TestSupport.removePlayer(helper, player);
        });
    }

    // covers: veiled.blink_keeps_off
    @GameTest(template = TestSupport.FLOOR_17, batch = "veiled_blink_off", timeoutTicks = 200)
    public static void itNeverBlinksInOnSomeoneInTheMirror(GameTestHelper helper) {
        TestSupport.track(helper);
        SuperpositionPodBlockEntity pod = pod(helper, POD_B, ModBlocks.POD_PLATING.get());
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
                    if (Math.abs(dx) == 3 || Math.abs(dz) == 3 || dy == 3) helper.setBlock(POD_B.offset(dx, dy, dz), Blocks.STONE);
                }
            }
        }
        ServerPlayer owner = TestSupport.player(helper, POD_A, GameType.SURVIVAL);
        UUID sophon = unfoldInto(helper, owner, pod);
        // Someone in the mirror, standing right by the pod inside the room.
        TestPlayer watcher = TestSupport.phasedPlayer(helper, POD_B.offset(-2, -1, 2), GameType.CREATIVE);
        // Close enough to reach the wall and be stuck there well before the check, as in the test above.
        Veiled veiled = helper.spawn(ModEntities.VEILED.get(), new BlockPos(4, 2, 12));
        veiled.huntDouble(sophon);
        BlockPos room = helper.absolutePos(POD_B);
        helper.runAfterDelay(150, () -> {
            // It may come right up to the outside of the wall, as close as it can walk; never blinks in beside them.
            boolean inside = Math.abs(veiled.getX() - (room.getX() + 0.5)) < 3.0
                    && Math.abs(veiled.getZ() - (room.getZ() + 0.5)) < 3.0;
            helper.assertTrue(!inside, "it does not blink into the room beside the watcher");
            helper.assertTrue(pod.hasDouble(), "and so cannot get at the double");
            TestSupport.discard(veiled);
            TestSupport.removePlayer(helper, watcher);
            TestSupport.removePlayer(helper, owner);
            helper.succeed();
        });
    }

    // covers: veiled.ward
    @GameTest(template = TestSupport.FLOOR_17, batch = "veiled_ward", timeoutTicks = 20)
    public static void theVeiledLeavesAWardedDoubleAlone(GameTestHelper helper) {
        TestSupport.track(helper);
        SuperpositionPodBlockEntity warded = pod(helper, POD_B, ModBlocks.POD_WARD_MODULE.get());
        ServerPlayer player = TestSupport.player(helper, FIELD, GameType.SURVIVAL);
        unfoldInto(helper, player, warded);
        Vec3 near = Vec3.atCenterOf(helper.absolutePos(POD_B));
        helper.assertTrue(Veiled.nearestUnguardedDouble(helper.getLevel(), near, 6.0) == null, "a warded double is not prey");
        SuperpositionPodBlockEntity open = pod(helper, POD_A, ModBlocks.POD_PLATING.get());
        UUID prey = unfoldInto(helper, player, open);
        helper.assertValueEqual(Veiled.nearestUnguardedDouble(helper.getLevel(), Vec3.atCenterOf(helper.absolutePos(POD_A)), 6.0),
                prey, "an unguarded one is");
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    // covers: pod.turret
    @GameTest(template = TestSupport.FLOOR_17, batch = "pod_turret", timeoutTicks = 20)
    public static void aProjectorOnAPodRunsOffIt(GameTestHelper helper) {
        TestSupport.track(helper);
        SuperpositionPodBlockEntity pod = pod(helper, POD_B, ModBlocks.POD_PLATING.get());
        helper.setBlock(POD_B.above(2), ModBlocks.DECOHERENCE_PROJECTOR.get());
        DecoherenceProjectorBlockEntity projector = helper.getBlockEntity(POD_B.above(2), DecoherenceProjectorBlockEntity.class);
        projector.getCells().setStackInSlot(0, new ItemStack(ModItems.ANOMALITE_CELL.get(), 16));
        int before = pod.getEnergyStorage().getEnergyStored();
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(projector.getEnergyStorage().getEnergyStored() > 0, "the Projector is powered");
            helper.assertTrue(pod.getEnergyStorage().getEnergyStored() < before, "from the pod beneath it");
            helper.succeed();
        });
    }

    // ---- helpers ----

    /** Where tests stand out in the open, away from any pod. */
    private static final BlockPos FIELD = new BlockPos(8, 2, 13);

    private static ItemStack tether(ServerPlayer player) {
        ItemStack tether = new ItemStack(ModItems.TETHER.get());
        TetherItem.setEnergy(tether, TetherItem.CAPACITY);
        player.setItemInHand(InteractionHand.MAIN_HAND, tether);
        return player.getItemInHand(InteractionHand.MAIN_HAND);
    }

    @org.jetbrains.annotations.Nullable
    private static FieldDouble fieldDouble(GameTestHelper helper, BlockPos at) {
        List<FieldDouble> found = helper.getLevel().getEntitiesOfClass(FieldDouble.class, new AABB(at).inflate(1.0));
        return found.isEmpty() ? null : found.getFirst();
    }

    @org.jetbrains.annotations.Nullable
    private static ItemEntity lying(GameTestHelper helper, BlockPos at, UUID sophon) {
        List<ItemEntity> found = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3.0), item -> {
            SophonBinding binding = item.getItem().get(ModDataComponents.SOPHON.get());
            return binding != null && binding.id().equals(sophon) && item.isAlive();
        });
        return found.isEmpty() ? null : found.getFirst();
    }

    private static final BlockPos POD_C = new BlockPos(12, 3, 4);

    private static RelayModuleBlockEntity relay(SuperpositionPodBlockEntity hub) {
        RelayModuleBlockEntity relay = Relay.hubOf(hub);
        if (relay == null) throw new IllegalStateException("no relay in the hub's cradle");
        return relay;
    }

    /** A Tesseract bound to the block at {@code relative}, as sneak-using it would. */
    private static ItemStack boundTo(GameTestHelper helper, BlockPos relative) {
        BlockPos at = helper.absolutePos(relative);
        ItemStack link = new ItemStack(ModItems.TESSERACT.get());
        link.set(ModDataComponents.BOUND_POS.get(), GlobalPos.of(helper.getLevel().dimension(), at));
        link.set(ModDataComponents.BOUND_SIDE.get(), Direction.UP);
        link.set(ModDataComponents.BOUND_BLOCK.get(), ModBlocks.SUPERPOSITION_POD.getId());
        return link;
    }

    private static void standIn(GameTestHelper helper, ServerPlayer player, BlockPos pod) {
        BlockPos at = helper.absolutePos(pod);
        player.snapTo(at.getX() + 0.5, at.getY() + 0.0625, at.getZ() + 0.5);
    }

    /** A whole pod at {@code at}: its cradle below, {@code edge} on each edge, full of power, facing south. */
    private static SuperpositionPodBlockEntity pod(GameTestHelper helper, BlockPos at, Block edge) {
        return pod(helper, at, edge, edge, edge, edge);
    }

    /** As above with its edges given north, east, south and west. */
    private static SuperpositionPodBlockEntity pod(GameTestHelper helper, BlockPos at, Block north, Block east, Block south,
                                                   Block west) {
        BlockPos floor = at.below();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean isEdge = (dx == 0) != (dz == 0);
                Block edge = dz < 0 ? north : dx > 0 ? east : dz > 0 ? south : west;
                helper.setBlock(floor.offset(dx, 0, dz), isEdge ? edge : ModBlocks.POD_CRADLE.get());
            }
        }
        BlockState lower = ModBlocks.SUPERPOSITION_POD.get().defaultBlockState()
                .setValue(SuperpositionPodBlock.FACING, Direction.SOUTH);
        helper.setBlock(at, lower);
        helper.setBlock(at.above(), lower.setValue(SuperpositionPodBlock.HALF, DoubleBlockHalf.UPPER));
        SuperpositionPodBlockEntity pod = helper.getBlockEntity(at, SuperpositionPodBlockEntity.class);
        pod.revalidate();
        TestSupport.fill(pod.getEnergyStorage());
        return pod;
    }

    /** A new Sophon for {@code player}, unfolded into {@code pod}. */
    private static UUID unfoldInto(GameTestHelper helper, ServerPlayer player, SuperpositionPodBlockEntity pod) {
        UUID id = SophonRegistry.get(helper.getLevel().getServer()).create(player.getUUID());
        pod.unfold(player, SophonItem.of(id, player.getUUID(), player.getGameProfile().name()));
        helper.assertValueEqual(pod.occupant(), id, "the double unfolds into the pod");
        return id;
    }

    /** An Unfolding Array with its pylons, stocked and powered. */
    private static UnfoldingArrayBlockEntity array(GameTestHelper helper) {
        TestSupport.track(helper);
        helper.setBlock(PAD, ModBlocks.UNFOLDING_ARRAY.get());
        for (BlockPos pylon : UnfoldingArrayBlockEntity.pylons(PAD)) helper.setBlock(pylon, ModBlocks.ARRAY_PYLON.get());
        UnfoldingArrayBlockEntity array = helper.getBlockEntity(PAD, UnfoldingArrayBlockEntity.class);
        TestSupport.fill(array.getEnergyStorage());
        array.getInventory().setStackInSlot(UnfoldingArrayBlockEntity.TESSERACT_SLOT, new ItemStack(ModItems.SEMI_STABLE_TESSERACT.get()));
        array.getInventory().setStackInSlot(UnfoldingArrayBlockEntity.FRAGMENT_SLOT,
                new ItemStack(ModItems.ANOMALY_FRAGMENT.get(), UnfoldingArrayBlockEntity.FRAGMENTS));
        array.getInventory().setStackInSlot(UnfoldingArrayBlockEntity.RESIDUE_SLOT,
                new ItemStack(ModItems.RIFT_RESIDUE.get(), UnfoldingArrayBlockEntity.RESIDUE));
        return array;
    }
}

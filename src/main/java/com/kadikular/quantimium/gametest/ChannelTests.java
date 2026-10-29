package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.block.LaserEmitterBlock;
import com.kadikular.quantimium.block.LaserPortBlock;
import com.kadikular.quantimium.block.LensMountBlock;
import com.kadikular.quantimium.block.entity.LaserPortBlockEntity;
import com.kadikular.quantimium.block.entity.LensMountBlockEntity;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.laser.Channels;
import com.kadikular.quantimium.laser.IsolatorSettings;
import com.kadikular.quantimium.laser.Lens;
import com.kadikular.quantimium.laser.LensUse;
import com.kadikular.quantimium.laser.Light;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Set;

/**
 * Laser channels: each lens works on its slot's channel (0 to 15, or All), and ports serve theirs. Light from one source
 * carries the same action on two channels as two streams, and a modifier changes only those on its own.
 */
public final class ChannelTests {

    private static final int Y = 2;
    private static final BlockPos SOURCE = new BlockPos(0, 0, 0);

    private ChannelTests() {}

    private static LensUse on(Lens lens, int channel) {
        return LensUse.of(lens).onChannel(channel);
    }

    private static Light.Stream stream(Light light, Lens action, int channel) {
        return light.streams().stream().filter(s -> s.action() == action && s.channel() == channel).findFirst().orElse(null);
    }

    // covers: laser.channels.streams
    @GameTest(template = TestSupport.FLOOR_9, batch = "channels", timeoutTicks = 20)
    public static void anActionOnTwoChannelsIsTwoStreams(GameTestHelper helper) {
        Light light = Light.from(SOURCE, 16).through(List.of(on(Lens.HARVEST, 0), on(Lens.HARVEST, 3), on(Lens.AMPLIFY, 3)));
        helper.assertValueEqual(light.streams().size(), 2, "Harvest on 0 and on 3");
        helper.assertValueEqual(stream(light, Lens.HARVEST, 0).amps(), 0, "the Amplify on 3 leaves channel 0 alone");
        helper.assertValueEqual(stream(light, Lens.HARVEST, 3).amps(), 1, "and strengthens channel 3's");
        // On All, it changes both.
        Light all = light.through(List.of(on(Lens.FILTER, Channels.ALL)));
        helper.assertTrue(stream(all, Lens.HARVEST, 0).filter() != null && stream(all, Lens.HARVEST, 3).filter() != null, "a Filter on All filters both");
        helper.succeed();
    }

    // covers: laser.channels.streams
    @GameTest(template = TestSupport.FLOOR_9, batch = "channels", timeoutTicks = 20)
    public static void joinedLightMergesOnlyTheSameChannel(GameTestHelper helper) {
        Light a = Light.from(new BlockPos(1, 0, 0), 16).through(List.of(on(Lens.DAMAGE, 0)));
        Light b = Light.from(new BlockPos(2, 0, 0), 16).through(List.of(on(Lens.DAMAGE, 0), on(Lens.DAMAGE, 5)));
        Light joined = Light.join(a, b);
        helper.assertValueEqual(joined.streams().size(), 2, "Damage on 0, paid by both, and Damage on 5");
        helper.assertValueEqual(stream(joined, Lens.DAMAGE, 0).payers().size(), 2, "both pay for channel 0's");
        helper.assertValueEqual(stream(joined, Lens.DAMAGE, 5).payers(), Set.of(new BlockPos(2, 0, 0)), "one for channel 5's");
        helper.succeed();
    }

    // covers: laser.channels.isolator
    @GameTest(template = TestSupport.FLOOR_9, batch = "channels", timeoutTicks = 20)
    public static void anIsolatorStopsOnlyItsChannel(GameTestHelper helper) {
        Light light = Light.from(SOURCE, 16).through(List.of(on(Lens.HARVEST, 0), on(Lens.HARVEST, 2)));
        LensUse isolator = new LensUse(Lens.ISOLATE, null, 0, IsolatorSettings.POWER_ONLY).onChannel(2);
        Light past = light.through(List.of(isolator));
        helper.assertTrue(stream(past, Lens.HARVEST, 0) != null, "channel 0's Harvest passes");
        helper.assertTrue(stream(past, Lens.HARVEST, 2) == null, "channel 2's is stopped");
        helper.assertValueEqual(light.through(List.of(isolator.onChannel(Channels.ALL))).streams().size(), 0, "on All, everything is");
        helper.succeed();
    }

    // covers: laser.channels.streams
    @GameTest(template = TestSupport.FLOOR_9, batch = "channels", timeoutTicks = 20)
    public static void aCavityRemembersEachStreamsChannel(GameTestHelper helper) {
        Light light = Light.from(SOURCE, 16).through(List.of(on(Lens.HARVEST, 7), on(Lens.SPEED, 7)));
        Light kept = Light.load(light.save(), SOURCE);
        helper.assertTrue(stream(kept, Lens.HARVEST, 7) != null, "Harvest still on channel 7");
        helper.assertValueEqual(kept.speeds(7), 1, "and its Speed lens");
        helper.assertValueEqual(kept.speeds(0), 0, "channel 0 not quickened");
        helper.succeed();
    }

    // covers: laser.channels.ports
    @GameTest(template = TestSupport.FLOOR_17, batch = "channels_ports", timeoutTicks = 120)
    public static void pickupBringsItBackToAPortOnItsChannel(GameTestHelper helper) {
        // Two collecting ports up the light, the nearer on channel 3: Harvest and Pickup on channel 0 pass it by.
        helper.setBlock(new BlockPos(1, Y, 8), ModBlocks.LASER_EMITTER_1.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(0, Y, 8), ModBlocks.CREATIVE_ENERGY_CELL.get());
        LaserPortBlockEntity far = port(helper, new BlockPos(3, Y, 8));
        LaserPortBlockEntity near = port(helper, new BlockPos(5, Y, 8));
        near.setChannel(3);
        helper.setBlock(new BlockPos(7, Y, 8), ModBlocks.LENS_MOUNT.get().defaultBlockState().setValue(LensMountBlock.FACING, Direction.EAST));
        LensMountBlockEntity mount = helper.getBlockEntity(new BlockPos(7, Y, 8), LensMountBlockEntity.class);
        mount.getLensSlots().setStackInSlot(0, new ItemStack(ModItems.HARVEST_LENS.get()));
        mount.getLensSlots().setStackInSlot(1, new ItemStack(ModItems.PICKUP_LENS.get()));
        helper.setBlock(new BlockPos(11, Y, 8), Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(far, Items.COBBLESTONE), 1, "the cobblestone went to the port on channel 0");
            helper.assertValueEqual(count(near, Items.COBBLESTONE), 0, "not the nearer one on channel 3");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.channels.streams
    @GameTest(template = TestSupport.FLOOR_17, batch = "channels_world", timeoutTicks = 120)
    public static void harvestWorksOnAnyChannel(GameTestHelper helper) {
        // Harvest on channel 3 on one row and on All on another: both cut their stone.
        for (int row = 0; row < 2; row++) {
            int z = 4 + row * 6;
            helper.setBlock(new BlockPos(1, Y, z), ModBlocks.LASER_EMITTER_1.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, Direction.EAST));
            helper.setBlock(new BlockPos(0, Y, z), ModBlocks.CREATIVE_ENERGY_CELL.get());
            helper.setBlock(new BlockPos(3, Y, z), ModBlocks.LENS_MOUNT.get().defaultBlockState().setValue(LensMountBlock.FACING, Direction.EAST));
            LensMountBlockEntity mount = helper.getBlockEntity(new BlockPos(3, Y, z), LensMountBlockEntity.class);
            mount.getLensSlots().setStackInSlot(0, new ItemStack(ModItems.HARVEST_LENS.get()));
            mount.setSlotChannel(0, row == 0 ? 3 : Channels.ALL);
            helper.setBlock(new BlockPos(7, Y, z), Blocks.STONE);
        }
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(7, Y, 4));
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(7, Y, 10));
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.channels.ports
    @GameTest(template = TestSupport.FLOOR_17, batch = "channels_world_pickup", timeoutTicks = 120)
    public static void pickupOnAChannelBringsItToItsPort(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, Y, 8), ModBlocks.LASER_EMITTER_1.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(0, Y, 8), ModBlocks.CREATIVE_ENERGY_CELL.get());
        LaserPortBlockEntity on0 = port(helper, new BlockPos(3, Y, 8));
        LaserPortBlockEntity on3 = port(helper, new BlockPos(5, Y, 8));
        on3.setChannel(3);
        helper.setBlock(new BlockPos(7, Y, 8), ModBlocks.LENS_MOUNT.get().defaultBlockState().setValue(LensMountBlock.FACING, Direction.EAST));
        LensMountBlockEntity mount = helper.getBlockEntity(new BlockPos(7, Y, 8), LensMountBlockEntity.class);
        mount.getLensSlots().setStackInSlot(0, new ItemStack(ModItems.HARVEST_LENS.get()));
        mount.getLensSlots().setStackInSlot(1, new ItemStack(ModItems.PICKUP_LENS.get()));
        mount.setSlotChannel(0, 3);
        mount.setSlotChannel(1, 3);
        helper.setBlock(new BlockPos(11, Y, 8), Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(on3, Items.COBBLESTONE), 1, "the cobblestone went to the port on channel 3");
            helper.assertValueEqual(count(on0, Items.COBBLESTONE), 0, "not the one on 0");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.spectroscope
    @GameTest(template = TestSupport.FLOOR_17, batch = "channels_spectroscope", timeoutTicks = 100)
    public static void theSpectroscopeSplitsALaserIntoItsLines(GameTestHelper helper) {
        // Harvest and Pickup on channel 3 into a port on 3: read off the mount, and off its beam in mid-air.
        helper.setBlock(new BlockPos(1, Y, 8), ModBlocks.LASER_EMITTER_1.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(0, Y, 8), ModBlocks.CREATIVE_ENERGY_CELL.get());
        port(helper, new BlockPos(3, Y, 8)).setChannel(3);
        helper.setBlock(new BlockPos(5, Y, 8), ModBlocks.LENS_MOUNT.get().defaultBlockState().setValue(LensMountBlock.FACING, Direction.EAST));
        LensMountBlockEntity mount = helper.getBlockEntity(new BlockPos(5, Y, 8), LensMountBlockEntity.class);
        mount.getLensSlots().setStackInSlot(0, new ItemStack(ModItems.HARVEST_LENS.get()));
        mount.getLensSlots().setStackInSlot(1, new ItemStack(ModItems.PICKUP_LENS.get()));
        mount.setSlotChannel(0, 3);
        mount.setSlotChannel(1, 3);
        helper.setBlock(new BlockPos(12, Y, 8), Blocks.OBSIDIAN);
        helper.runAfterDelay(40, () -> {
            var networks = com.kadikular.quantimium.laser.LaserNetworks.of(helper.getLevel());
            BlockPos at = helper.absolutePos(new BlockPos(5, Y, 8));
            var reading = networks.read(helper.getLevel(), at, -1);
            helper.assertTrue(reading != null, "a reading off the mount");
            var line = reading.lines().stream().filter(l -> l.lens() == Lens.HARVEST).findFirst().orElse(null);
            helper.assertTrue(line != null && line.channel() == 3 && line.has(Lens.PICKUP), "Harvest on channel 3, with Pickup: " + reading.lines());
            helper.assertTrue(reading.ports().stream().anyMatch(p -> p.channel() == 3 && (p.role() & 1) != 0), "the port on 3, collecting");
            // Looking across the beam, a block past the mount, from the side.
            var over = helper.absolutePos(new BlockPos(8, Y, 8)).getCenter();
            var aim = networks.aimedAt(over.add(0, 0, -3), over.add(0, 0, 3));
            helper.assertTrue(aim != null && aim.pos().equals(at), "aimed across its beam, the mount's: " + aim);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    private static LaserPortBlockEntity port(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.LASER_PORT.get().defaultBlockState().setValue(LaserPortBlock.FACING, Direction.EAST));
        return helper.getBlockEntity(pos, LaserPortBlockEntity.class);
    }

    private static int count(LaserPortBlockEntity port, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < LaserPortBlockEntity.SLOTS; slot++) {
            ItemStack stack = port.getItems().getStackInSlot(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }
}

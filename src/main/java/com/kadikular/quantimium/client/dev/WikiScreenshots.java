package com.kadikular.quantimium.client.dev;

import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import com.kadikular.quantimium.util.LegacyItems;
import net.minecraft.world.item.Item;
import com.kadikular.quantimium.block.entity.FieldControlBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumObservationChamberBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumEnergyHost;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.ContainmentHallStructure;
import com.kadikular.quantimium.block.entity.ContainmentHallBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumFoundryBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumSimulatorBlockEntity;
import com.kadikular.quantimium.block.entity.TesseractStabilizerBlockEntity;
import com.kadikular.quantimium.block.entity.ZenoFieldControllerBlockEntity;
import com.kadikular.quantimium.client.screen.ui.QuantumOverlay;
import com.kadikular.quantimium.client.screen.QuantumMachineScreen;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.network.RequestSideConfigPayload;
import com.kadikular.quantimium.network.RequestSlotConfigPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Captures the machine GUIs for the wiki. Only runs when the game is started with
 * {@code -Dquantimium.wikiShots=<folder>} (the {@code wikiShots} run, via tools/wiki_shots.sh): it
 * makes a fresh creative flat world, builds each machine with believable contents, opens its screen,
 * crops the panel out of the framebuffer, and quits.
 *
 * <p>Everything is driven from the client tick with fixed waits, because a screen needs a few ticks
 * of menu data before it shows what the machine is doing, and an overlay needs a server round trip.
 */
@EventBusSubscriber(modid = Quantimium.MODID, value = Dist.CLIENT)
public final class WikiScreenshots {

    private static final String PROPERTY = "quantimium.wikiShots";
    private static final String WORLD = "wiki_shots";

    private record Step(int delay, Runnable action) {}

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static boolean started;
    private static boolean planned;
    private static int countdown;
    private static Path out;

    private WikiScreenshots() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        String folder = System.getProperty(PROPERTY);
        if (folder == null) return;
        Minecraft minecraft = Minecraft.getInstance();

        if (!started) {
            if (minecraft.screen instanceof TitleScreen) {
                started = true;
                out = Path.of(folder);
                createWorld(minecraft);
            }
            return;
        }
        if (!planned) {
            if (minecraft.player != null && minecraft.getSingleplayerServer() != null) {
                planned = true;
                var server = minecraft.getSingleplayerServer();
                server.execute(() -> {
                    var rules = server.overworld().getGameRules();
                    rules.set(GameRules.ADVANCE_TIME, false, server);
                    rules.set(GameRules.SPAWN_MOBS, false, server);
                });
                plan(minecraft);
                countdown = STEPS.peek().delay();
            }
            return;
        }
        if (STEPS.isEmpty()) return;
        if (countdown > 0) {
            countdown--;
            return;
        }
        Step step = STEPS.poll();
        step.action().run();
        if (!STEPS.isEmpty()) countdown = STEPS.peek().delay();
    }

    private static void createWorld(Minecraft minecraft) {
        // Daylight and mob spawning are switched off once the world is running (see onClientTick).
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0L, false, false),
                registries -> registries.lookupOrThrow(Registries.WORLD_PRESET)
                        .getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                new TitleScreen());
    }

    /**
     * Where each screen's block stands, relative to the player. The single blocks stand in a row
     * behind the player, two apart; the hall and the foundry sit ahead and to the side. Everything is
     * within 8 blocks, or the menus would close themselves as out of reach.
     */
    private static final BlockPos[] SPOTS = {
            new BlockPos(-6, 0, -4), new BlockPos(-4, 0, -4), new BlockPos(-2, 0, -4), new BlockPos(0, 0, -4),
            new BlockPos(2, 0, -4), new BlockPos(4, 0, -4), new BlockPos(6, 0, -4),
            new BlockPos(0, 0, 7), new BlockPos(7, 0, 0), new BlockPos(-5, 0, 5), new BlockPos(-5, 0, -6),
            new BlockPos(5, 0, 5), new BlockPos(-7, 0, -1), new BlockPos(-7, 0, 1),
    };
    private static final int HALL = 7;
    private static final int FOUNDRY = 8;
    private static final int ZENO = 9;
    private static final int SUPERPOSITION = 10;
    private static final int QUANTUM_CHAMBER = 11;
    private static final int MAINTAINER = 12;
    private static final int REGULATOR = 13;
    private static final Identifier SUPERPOSITION_ID =
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "me_superposition_crafter");

    private static BlockPos slot(ServerPlayer player, int index) {
        return player.blockPosition().offset(SPOTS[index]);
    }

    /**
     * Recipe viewers draw their item lists beside the panel, over the tab column. For captures they are
     * switched off, through their own toggles, reached by name since both are optional.
     */
    private static void hideRecipeViewers() {
        try {
            Object toggles = Class.forName("mezz.jei.common.Internal").getMethod("getClientToggleState").invoke(null);
            if ((boolean) toggles.getClass().getMethod("isOverlayEnabled").invoke(toggles)) {
                toggles.getClass().getMethod("toggleOverlayEnabled").invoke(toggles);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // JEI not installed, or changed shape.
        }
        try {
            Class.forName("dev.emi.emi.config.EmiConfig").getField("enabled").setBoolean(null, false);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // EMI not installed, or changed shape.
        }
    }

    private static void plan(Minecraft minecraft) {
        hideRecipeViewers();
        // Let the world settle (chunks, the first sync) before building anything.
        then(40, () -> onServer(WikiScreenshots::build));

        shoot("basic_quantum_crafter", 0, null);
        shoot("quantum_crafter", 1, null);
        shoot("quantum_crafter_sides", 1, Overlay.SIDES);
        shoot("quantum_simulator", 2, null);
        shoot("quantum_simulator_slots", 2, Overlay.SLOTS);
        shoot("quantum_simulator_sides", 2, Overlay.SIDES);
        // Its screen is the side configuration; it opens that itself.
        shoot("tesseract_stabilizer", 3, null);
        shoot("observation_chamber", 4, null);
        shoot("quantum_observation_chamber", QUANTUM_CHAMBER, null);
        shoot("flux_maintainer", MAINTAINER, null);
        shoot("field_regulator", REGULATOR, null);
        shoot("flux_suppressor", 5, null);
        shoot("basic_anomaly_siphon", 6, null);
        shoot("anomaly_containment_hall", HALL, null);
        shoot("quantum_foundry", FOUNDRY, null);
        shoot("zeno_field_controller", ZENO, null);
        if (ModList.get().isLoaded("ae2")) {
            shoot("me_superposition_crafter", SUPERPOSITION, null);
            shoot("me_superposition_crafter_filter", SUPERPOSITION, Overlay.FILTER_VIEW);
            shoot("me_superposition_crafter_inputs", SUPERPOSITION, Overlay.INPUT_LIST);
        }

        if (ModList.get().isLoaded("ae2")) shootWorld("world_me_superposition_crafter");
        shootLens("world_mirror_lens");
        shootSuperposition();

        then(10, () -> {
            Quantimium.LOGGER.info("Wiki screenshots written to {}", out.toAbsolutePath());
            minecraft.stop();
        });
    }

    private enum Overlay { SIDES, SLOTS, FILTER_VIEW, INPUT_LIST }

    private static void shoot(String name, int index, Overlay overlay) {
        then(10, () -> onServer(player -> {
            BlockPos pos = slot(player, index);
            if (player.level().getBlockEntity(pos) instanceof MenuProvider provider) {
                player.openMenu(provider, pos);
            }
        }));
        if (overlay == Overlay.FILTER_VIEW || overlay == Overlay.INPUT_LIST) {
            then(30, () -> showFilterView(overlay == Overlay.INPUT_LIST));
        } else if (overlay != null) {
            then(30, () -> {
                BlockPos pos = slot(Minecraft.getInstance().getSingleplayerServer().getPlayerList()
                        .getPlayers().get(0), index);
                ClientPacketDistributor.sendToServer(overlay == Overlay.SIDES
                        ? new RequestSideConfigPayload(pos) : new RequestSlotConfigPayload(pos, false));
            });
        }
        // Park the cursor in a corner so no slot or tab shows a hover tooltip.
        then(30, WikiScreenshots::parkCursor);
        then(5, () -> capture(name));
        then(2, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) minecraft.player.closeContainer();
        });
    }

    /**
     * Opening a screen centres the cursor, right over the panel. Moving the real cursor is not always
     * reported back by GLFW, so the game's own copy of the position is set as well.
     */
    private static void parkCursor() {
        Minecraft minecraft = Minecraft.getInstance();
        GLFW.glfwSetCursorPos(minecraft.getWindow().handle(), 0, 0);
        try {
            for (String field : new String[] {"xpos", "ypos"}) {
                var handle = MouseHandler.class.getDeclaredField(field);
                handle.setAccessible(true);
                handle.setDouble(minecraft.mouseHandler, 0.0);
            }
        } catch (ReflectiveOperationException e) {
            Quantimium.LOGGER.warn("Could not park the cursor for wiki screenshots", e);
        }
    }

    /**
     * A picture of blocks in the world, not a screen: a pad well away from the capture room, lit by day,
     * seen from a fixed spot with the HUD hidden. Two Superposition Crafters: one on a small network
     * (energy cell and drive beside it, so both sides show a port plate) holding a furnace, one alone.
     */
    private static void shootWorld(String name) {
        BlockPos[] crafters = new BlockPos[2];
        then(5, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) minecraft.player.closeContainer();
        });
        then(5, () -> onServer(player -> {
            ServerLevel level = player.level();
            BlockPos pad = player.blockPosition().offset(0, 0, -40);
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    level.setBlockAndUpdate(pad.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState());
                    for (int dy = 0; dy <= 4; dy++) level.setBlockAndUpdate(pad.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
            BlockPos online = pad.offset(-2, 0, 0);
            buildSuperposition(level, online);
            level.setBlockAndUpdate(online.east(), BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:drive")).defaultBlockState());
            // A cable against its south face, laid as a player would, so the port plate there shows.
            ItemStack cable = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:fluix_glass_cable")));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, cable);
            BlockPos floor = online.south().below();
            cable.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(floor).add(0, 0.5, 0),
                            net.minecraft.core.Direction.UP, floor, false)));
            player.getInventory().clearContent();
            BlockPos offline = pad.offset(3, 0, 0);
            level.setBlockAndUpdate(offline, BuiltInRegistries.BLOCK.getValue(SUPERPOSITION_ID).defaultBlockState());
            crafters[0] = online;
            crafters[1] = offline;
            lookAt(player, online);
        }));
        then(60, () -> Minecraft.getInstance().options.hideGui = true);
        then(5, () -> captureWorld(name));
        then(2, () -> onServer(player -> lookAt(player, crafters[1])));
        then(40, () -> captureWorld(name + "_offline"));
        then(2, () -> Minecraft.getInstance().options.hideGui = false);
    }

    /** An armour stand wearing a Mirror Lens, on a pad of its own, turned to face the camera. */
    private static void shootLens(String name) {
        BlockPos[] stand = new BlockPos[1];
        then(5, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) minecraft.player.closeContainer();
        });
        then(5, () -> onServer(player -> {
            ServerLevel level = player.level();
            BlockPos pad = player.blockPosition().offset(0, 0, 40);
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    level.setBlockAndUpdate(pad.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState());
                    for (int dy = 0; dy <= 4; dy++) level.setBlockAndUpdate(pad.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
            net.minecraft.world.entity.decoration.ArmorStand armorStand =
                    new net.minecraft.world.entity.decoration.ArmorStand(level, pad.getX() + 0.5, pad.getY(), pad.getZ() + 0.5);
            // Facing the camera, which lookAt puts to the south-east.
            armorStand.setYRot(-35.0f);
            armorStand.setYHeadRot(-35.0f);
            armorStand.setYBodyRot(-35.0f);
            armorStand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                    new ItemStack(com.kadikular.quantimium.init.ModItems.MIRROR_LENS.get()));
            level.addFreshEntity(armorStand);
            stand[0] = pad.above();
            lookAt(player, stand[0]);
        }));
        then(60, () -> Minecraft.getInstance().options.hideGui = true);
        then(5, () -> captureWorld(name));
        // And on a player, seen from the front, with the stand out of the way.
        then(2, () -> onServer(player -> player.level().getEntities(net.minecraft.world.entity.EntityType.ARMOR_STAND,
                armorStand -> true).forEach(net.minecraft.world.entity.Entity::discard)));
        then(2, () -> onServer(player -> player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                new ItemStack(com.kadikular.quantimium.init.ModItems.MIRROR_LENS.get()))));
        then(2, () -> Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        then(20, () -> captureWorld(name + "_player"));
        then(2, () -> {
            Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            onServer(player -> player.getInventory().clearContent());
        });
        then(2, () -> Minecraft.getInstance().options.hideGui = false);
    }

    /**
     * The Superposition Pod and the Unfolding Array, on a pad of their own to the west: a pod holding
     * the player's double beside an empty one, the player half way through being unfolded (seen from
     * behind), and the pod's screen from inside the empty pod.
     */
    private static void shootSuperposition() {
        BlockPos[] at = new BlockPos[4];
        then(5, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) minecraft.player.closeContainer();
        });
        then(5, () -> onServer(player -> {
            ServerLevel level = player.level();
            BlockPos pad = player.blockPosition().offset(-40, 0, 0);
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    level.setBlockAndUpdate(pad.offset(dx, -1, dz), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                    for (int dy = 0; dy <= 5; dy++) level.setBlockAndUpdate(pad.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
            BlockPos full = pad.offset(-2, 1, -2);
            BlockPos empty = pad.offset(2, 1, -2);
            BlockPos outpost = pad.offset(6, 1, -2);
            podAt(level, full, player, true);
            podAt(level, empty, player, false);
            podAt(level, outpost, player, false);
            if (level.getBlockEntity(outpost) instanceof com.kadikular.quantimium.block.entity.SuperpositionPodBlockEntity pod) {
                pod.setCustomName(net.minecraft.network.chat.Component.literal("Outpost"));
            }
            // The empty pod is a hub: a Relay on its west edge, bound to the outpost.
            BlockPos relayAt = empty.offset(-1, -1, 0);
            level.setBlockAndUpdate(relayAt, ModBlocks.POD_RELAY_MODULE.get().defaultBlockState());
            if (level.getBlockEntity(relayAt) instanceof com.kadikular.quantimium.block.entity.RelayModuleBlockEntity relay) {
                ItemStack link = new ItemStack(com.kadikular.quantimium.init.ModItems.TESSERACT.get());
                link.set(com.kadikular.quantimium.init.ModDataComponents.BOUND_POS.get(),
                        net.minecraft.core.GlobalPos.of(level.dimension(), outpost));
                link.set(com.kadikular.quantimium.init.ModDataComponents.BOUND_SIDE.get(), net.minecraft.core.Direction.UP);
                link.set(com.kadikular.quantimium.init.ModDataComponents.BOUND_BLOCK.get(), ModBlocks.SUPERPOSITION_POD.getId());
                relay.getInventory().setStackInSlot(0, link);
            }
            at[3] = relayAt;
            BlockPos array = pad.offset(0, 0, 3);
            level.setBlockAndUpdate(array, ModBlocks.UNFOLDING_ARRAY.get().defaultBlockState());
            for (BlockPos pylon : com.kadikular.quantimium.block.entity.UnfoldingArrayBlockEntity.pylons(array)) {
                level.setBlockAndUpdate(pylon, ModBlocks.ARRAY_PYLON.get().defaultBlockState());
            }
            at[0] = full;
            at[1] = empty;
            at[2] = array;
            player.teleportTo(level, pad.getX() + 0.5, pad.getY(), pad.getZ() + 2.5, java.util.Set.of(), 180.0f, 8.0f, true);
        }));
        then(60, () -> Minecraft.getInstance().options.hideGui = true);
        then(5, () -> captureWorld("world_superposition_pod"));
        // Unfolding: on the pad, seen from behind and above.
        then(2, () -> onServer(player -> {
            ServerLevel level = player.level();
            player.teleportTo(level, at[2].getX() + 0.5, at[2].getY() + 0.375, at[2].getZ() + 0.5, java.util.Set.of(), 150.0f, 0.0f, true);
            if (level.getBlockEntity(at[2]) instanceof com.kadikular.quantimium.block.entity.UnfoldingArrayBlockEntity array) {
                TestFill.fill(array);
                array.pressButton(player);
            }
        }));
        then(2, () -> Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
        then(300, () -> captureWorld("world_unfolding_array"));
        then(2, () -> Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        then(2, () -> Minecraft.getInstance().options.hideGui = false);
        // The screen, from inside the empty pod.
        then(2, () -> onServer(player -> {
            ServerLevel level = player.level();
            player.teleportTo(level, at[1].getX() + 0.5, at[1].getY() + 0.0625, at[1].getZ() + 0.5, java.util.Set.of(), 0.0f, 0.0f, true);
            if (level.getBlockEntity(at[1]) instanceof com.kadikular.quantimium.block.entity.SuperpositionPodBlockEntity pod) {
                com.kadikular.quantimium.superposition.Superposition.openScreen(player, pod);
            }
        }));
        then(30, WikiScreenshots::parkCursor);
        then(5, () -> captureWorld("superposition_pod_screen"));
        then(2, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen != null) minecraft.screen.onClose();
        });
        then(5, () -> onServer(player -> {
            if (player.level().getBlockEntity(at[3]) instanceof MenuProvider provider) player.openMenu(provider, at[3]);
        }));
        then(30, WikiScreenshots::parkCursor);
        then(5, () -> capture("pod_relay_module"));
        then(2, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) minecraft.player.closeContainer();
        });
        // A field double left out in the open, and the Tether's screen.
        then(5, () -> onServer(player -> {
            ServerLevel level = player.level();
            BlockPos spot = at[2].offset(3, 0, 1);
            com.kadikular.quantimium.entity.FieldDouble body = com.kadikular.quantimium.init.ModEntities.FIELD_DOUBLE.get().create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if (body != null) {
                body.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 200.0f, 0.0f);
                body.become(player, java.util.UUID.randomUUID());
                body.setYRot(200.0f);
                body.setYBodyRot(200.0f);
                body.setYHeadRot(200.0f);
                level.addFreshEntity(body);
            }
            player.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() - 2.5, java.util.Set.of(), 0.0f, 10.0f, true);
        }));
        then(40, () -> Minecraft.getInstance().options.hideGui = true);
        then(5, () -> captureWorld("world_field_double"));
        then(2, () -> Minecraft.getInstance().options.hideGui = false);
        then(2, () -> onServer(player -> {
            ItemStack tether = new ItemStack(com.kadikular.quantimium.init.ModItems.TETHER.get());
            com.kadikular.quantimium.item.TetherItem.setEnergy(tether, 820_000);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tether);
            com.kadikular.quantimium.superposition.Superposition.openTether(player, tether);
        }));
        then(30, WikiScreenshots::parkCursor);
        then(5, () -> captureWorld("tether_screen"));
        then(2, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen != null) minecraft.screen.onClose();
            onServer(player -> player.getInventory().clearContent());
        });
    }

    /** A whole, powered pod at {@code pos}, facing south, holding the player's double if {@code full}. */
    private static void podAt(ServerLevel level, BlockPos pos, ServerPlayer player, boolean full) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean edge = (dx == 0) != (dz == 0);
                level.setBlockAndUpdate(pos.offset(dx, -1, dz), (edge
                        ? (dx == 1 ? ModBlocks.POD_RESCUE_MODULE.get() : ModBlocks.POD_PLATING.get())
                        : ModBlocks.POD_CRADLE.get()).defaultBlockState());
            }
        }
        var lower = ModBlocks.SUPERPOSITION_POD.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.SuperpositionPodBlock.FACING, net.minecraft.core.Direction.SOUTH);
        level.setBlockAndUpdate(pos, lower);
        level.setBlockAndUpdate(pos.above(), lower.setValue(com.kadikular.quantimium.block.SuperpositionPodBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        if (!(level.getBlockEntity(pos) instanceof com.kadikular.quantimium.block.entity.SuperpositionPodBlockEntity pod)) return;
        pod.claim(player);
        pod.getEnergyStorage().setEnergy(1_400_000);
        pod.revalidate();
        if (full) {
            var registry = com.kadikular.quantimium.superposition.SophonRegistry.get(level.getServer());
            java.util.UUID id = registry.create(player.getUUID());
            pod.unfold(player, com.kadikular.quantimium.item.SophonItem.of(id, player.getUUID(), player.getGameProfile().name()));
            pod.setCustomName(net.minecraft.network.chat.Component.literal("Home"));
        } else {
            pod.setCustomName(net.minecraft.network.chat.Component.literal("Mine"));
        }
    }

    /** Stocks and powers an Unfolding Array for a capture. */
    private static final class TestFill {
        static void fill(com.kadikular.quantimium.block.entity.UnfoldingArrayBlockEntity array) {
            array.getEnergyStorage().setEnergy(array.getEnergyStorage().getMaxEnergyStored());
            array.getInventory().setStackInSlot(0, new ItemStack(com.kadikular.quantimium.init.ModItems.SEMI_STABLE_TESSERACT.get()));
            array.getInventory().setStackInSlot(1, new ItemStack(com.kadikular.quantimium.init.ModItems.ANOMALY_FRAGMENT.get(), 4));
            array.getInventory().setStackInSlot(2, new ItemStack(com.kadikular.quantimium.init.ModItems.RIFT_RESIDUE.get()));
        }
    }

    /**
     * Stand on the pad a few blocks south-east of {@code target}, looking at its middle. On the floor,
     * not above it: a creative player who is not flying drops, and would keep the angle meant for up there.
     */
    private static void lookAt(ServerPlayer player, BlockPos target) {
        double x = target.getX() + 2.3;
        double y = target.getY();
        double z = target.getZ() + 3.1;
        double dx = target.getX() + 0.5 - x;
        double dy = target.getY() + 0.5 - (y + player.getEyeHeight());
        double dz = target.getZ() + 0.5 - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.teleportTo(player.level(), x, y, z, java.util.Set.of(), yaw, pitch, true);
    }

    /** The middle of the frame, where the pad is, as a PNG. */
    private static void captureWorld(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        // The frame is read back from the GPU and handed over once it's ready.
        Screenshot.takeScreenshot(minecraft.getMainRenderTarget(), taken -> {
            try (NativeImage frame = taken) {
                int width = frame.getWidth() * 3 / 4;
                int height = frame.getHeight() * 3 / 4;
                int x0 = (frame.getWidth() - width) / 2;
                int y0 = (frame.getHeight() - height) / 2;
                try (NativeImage crop = new NativeImage(width, height, false)) {
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) crop.setPixel(x, y, frame.getPixel(x0 + x, y0 + y));
                    }
                    Files.createDirectories(out);
                    crop.writeToFile(out.resolve(name + ".png"));
                }
            } catch (Exception e) {
                Quantimium.LOGGER.error("Wiki screenshot {} failed", name, e);
            }
        });
    }

    /** The Superposition Crafter's filter view; reached by name, as the class only exists with AE2. */
    private static void showFilterView(boolean inputs) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof AbstractContainerScreen<?> screen)) return;
        try {
            screen.getMenu().getClass().getMethod("setFilterView", boolean.class).invoke(screen.getMenu(), true);
            screen.getMenu().getClass().getMethod("setInputList", boolean.class).invoke(screen.getMenu(), inputs);
        } catch (ReflectiveOperationException e) {
            Quantimium.LOGGER.warn("Could not open the filter view for wiki screenshots", e);
        }
    }

    private static void then(int wait, Runnable action) {
        STEPS.add(new Step(wait, action));
    }

    private static void onServer(Consumer<ServerPlayer> action) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayers().get(0)));
    }

    private static void build(ServerPlayer player) {
        ServerLevel level = player.level();
        // Some mods hand a new player a guide book; the hotbar should be empty in every capture.
        player.getInventory().clearContent();
        backdrop(level, player);

        // Basic crafter: a crafting table and a few materials, so the preview shows a spread of crafts.
        BlockPos basic = slot(player, 0);
        level.setBlockAndUpdate(basic, ModBlocks.BASIC_QUANTUM_CRAFTER.get().defaultBlockState());
        if (level.getBlockEntity(basic) instanceof QuantumCrafterBlockEntity crafter) {
            crafter.getInventory().setStackInSlot(QuantumCrafterBlockEntity.CATALYST_SLOT, new ItemStack(Items.CRAFTING_TABLE));
            crafter.getInventory().setStackInSlot(0, new ItemStack(Items.OAK_PLANKS, 32));
            crafter.getInventory().setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 24));
            crafter.getInventory().setStackInSlot(2, new ItemStack(Items.IRON_INGOT, 18));
            crafter.getEnergyStorage().setEnergy(600_000);
        }

        // Quantum crafter: a furnace smelting ores.
        BlockPos quantum = slot(player, 1);
        level.setBlockAndUpdate(quantum, ModBlocks.QUANTUM_CRAFTER.get().defaultBlockState());
        if (level.getBlockEntity(quantum) instanceof QuantumCrafterBlockEntity crafter) {
            crafter.getInventory().setStackInSlot(QuantumCrafterBlockEntity.CATALYST_SLOT, new ItemStack(Items.FURNACE));
            crafter.getInventory().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 64));
            crafter.getInventory().setStackInSlot(1, new ItemStack(Items.RAW_COPPER, 40));
            crafter.getInventory().setStackInSlot(2, new ItemStack(Items.RAW_GOLD, 12));
            crafter.getInventory().setStackInSlot(3, new ItemStack(Items.SAND, 64));
            crafter.getEnergyStorage().setEnergy(42_000_000);
        }

        // Simulator: an engaged furnace at 4×, fed with iron and coal.
        BlockPos simulator = slot(player, 2);
        level.setBlockAndUpdate(simulator, ModBlocks.QUANTUM_SIMULATOR.get().defaultBlockState());
        level.setBlockAndUpdate(simulator.above(), Blocks.FURNACE.defaultBlockState());
        if (level.getBlockEntity(simulator) instanceof QuantumSimulatorBlockEntity sim) {
            sim.getEnergyStorage().setEnergy(6_500_000);
            sim.toggleEngage(null);
            sim.setBatchSize(4);
            sim.getInventory().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 64));
            sim.getInventory().setStackInSlot(1, new ItemStack(Items.COAL, 32));
        }

        // Stabilizer: docked with a Tesseract bound to the top of a stocked chest.
        BlockPos stabilizer = slot(player, 3);
        BlockPos chest = stabilizer.north(2);
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(chest) instanceof ChestBlockEntity box) {
            box.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
            box.setItem(1, new ItemStack(Items.REDSTONE, 40));
        }
        level.setBlockAndUpdate(stabilizer, ModBlocks.TESSERACT_STABILIZER.get().defaultBlockState());
        if (level.getBlockEntity(stabilizer) instanceof TesseractStabilizerBlockEntity dock) {
            ItemStack link = new ItemStack(ModItems.TESSERACT.get());
            link.set(ModDataComponents.BOUND_POS.get(), GlobalPos.of(level.dimension(), chest));
            link.set(ModDataComponents.BOUND_SIDE.get(), Direction.UP);
            link.set(ModDataComponents.BOUND_BLOCK.get(), BuiltInRegistries.BLOCK.getKey(Blocks.CHEST));
            dock.getInventory().setStackInSlot(TesseractStabilizerBlockEntity.LINK_SLOT, link);
        }

        place(level, slot(player, 4), ModBlocks.OBSERVATION_CHAMBER.get().defaultBlockState());
        place(level, slot(player, QUANTUM_CHAMBER), ModBlocks.QUANTUM_OBSERVATION_CHAMBER.get().defaultBlockState());
        if (level.getBlockEntity(slot(player, QUANTUM_CHAMBER)) instanceof QuantumObservationChamberBlockEntity chamber) {
            chamber.getEnergyStorage().setEnergy(QuantumObservationChamberBlockEntity.ENERGY_CAPACITY * 2 / 3);
            chamber.getInventory().setStackInSlot(0, new ItemStack(ModItems.UNREALISED_MATTER.get(), 48));
            chamber.getInventory().setStackInSlot(1, new ItemStack(ModItems.UNREALISED_MATTER.get(), 64));
        }
        place(level, slot(player, MAINTAINER), ModBlocks.FLUX_MAINTAINER.get().defaultBlockState());
        place(level, slot(player, REGULATOR), ModBlocks.FIELD_REGULATOR.get().defaultBlockState());
        for (int index : new int[] {MAINTAINER, REGULATOR}) {
            if (level.getBlockEntity(slot(player, index)) instanceof FieldControlBlockEntity control) {
                // Full, and a Medium floor: it is holding, not straining, by the time it is shot.
                control.getEnergyStorage().setEnergy(FieldControlBlockEntity.ENERGY_CAPACITY);
                control.setFloor(com.kadikular.quantimium.flux.FluxBand.MEDIUM);
            }
        }
        place(level, slot(player, 5), ModBlocks.FLUX_SUPPRESSOR.get().defaultBlockState());
        place(level, slot(player, 6), ModBlocks.BASIC_ANOMALY_SIPHON.get().defaultBlockState());
        for (int index : new int[] {5, 6}) {
            if (level.getBlockEntity(slot(player, index)) instanceof FieldControlBlockEntity control) {
                control.getEnergyStorage().setEnergy(FieldControlBlockEntity.ENERGY_CAPACITY * 2 / 3);
            }
        }
        buildHall(level, slot(player, HALL));
        if (ModList.get().isLoaded("ae2")) buildSuperposition(level, slot(player, SUPERPOSITION));
        place(level, slot(player, ZENO), ModBlocks.ZENO_FIELD_CONTROLLER.get().defaultBlockState());
        if (level.getBlockEntity(slot(player, ZENO)) instanceof ZenoFieldControllerBlockEntity zeno) {
            zeno.setMode(ZenoFieldControllerBlockEntity.MODE_ACCELERATE);
            zeno.setRadius(4);
            zeno.setFactor(8);
            zeno.getEnergyStorage().setEnergy(ZenoFieldControllerBlockEntity.ENERGY_CAPACITY);
        }
        buildFoundry(level, slot(player, FOUNDRY));
    }

    /**
     * Walls the player into a small black room, so the strip beside a panel's tabs and anything else
     * the crop catches is plain dark instead of whatever the world happens to show.
     */
    private static void backdrop(ServerLevel level, ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
                    boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == -1 || dy == 3;
                    if (shell) level.setBlockAndUpdate(feet.offset(dx, dy, dz), Blocks.BLACK_CONCRETE.defaultBlockState());
                }
            }
        }
        player.setYRot(0.0f);
        player.setXRot(0.0f);
    }

    /** The 3×3 plinth ring and all four arms: a pillar with its attunement tank, and the conduit to it. */
    private static void ringAndArms(ServerLevel level, BlockPos centre, net.minecraft.world.level.block.state.BlockState middle) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlockAndUpdate(centre.offset(dx, 0, dz), dx == 0 && dz == 0
                        ? middle : ModBlocks.QUANTUM_FOUNDRY_PLINTH.get().defaultBlockState());
            }
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            level.setBlockAndUpdate(centre.relative(direction, 2), ModBlocks.QUANTUM_FOUNDRY_CONDUIT.get().defaultBlockState());
            level.setBlockAndUpdate(centre.relative(direction, 3), ModBlocks.QUANTUM_FOUNDRY_PILLAR.get().defaultBlockState());
            level.setBlockAndUpdate(centre.relative(direction, 3).above(),
                    ModBlocks.QUANTUM_FOUNDRY_ATTUNEMENT_TANK.get().defaultBlockState());
        }
    }

    private static void buildHall(ServerLevel level, BlockPos centre) {
        ringAndArms(level, centre, ModBlocks.ANOMALY_CONTAINMENT_HALL.get().defaultBlockState());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos column = centre.offset(dx, 0, dz);
                boolean middle = dx == 0 && dz == 0;
                for (int dy = 1; dy <= ContainmentHallStructure.WALL_HEIGHT; dy++) {
                    level.setBlockAndUpdate(column.above(dy), middle
                            ? Blocks.AIR.defaultBlockState() : ModBlocks.QUANTUM_ATTUNED_GLASS.get().defaultBlockState());
                }
                level.setBlockAndUpdate(column.above(ContainmentHallStructure.CAP_OFFSET),
                        ModBlocks.QUANTUM_FOUNDRY_PLINTH.get().defaultBlockState());
            }
        }
        if (level.getBlockEntity(centre) instanceof ContainmentHallBlockEntity hall) {
            hall.revalidateStructure();
            hall.getEnergyStorage().setEnergy(hall.getEnergyStorage().getMaxEnergyStored() * 3 / 4);
            hall.getResidue().setStackInSlot(0, new ItemStack(ModItems.RIFT_RESIDUE.get(), 12));
        }
    }

    private static void buildFoundry(ServerLevel level, BlockPos centre) {
        ringAndArms(level, centre, ModBlocks.QUANTUM_FOUNDRY_CONTROLLER.get().defaultBlockState());
        if (level.getBlockEntity(centre) instanceof QuantumFoundryBlockEntity foundry) {
            foundry.revalidateStructure();
            foundry.getEnergyStorage().setEnergy(foundry.getEnergyStorage().getMaxEnergyStored() / 2);
        }
    }

    /** With a creative energy cell beside it, so it is online, a furnace catalyst and a short filter. */
    private static void buildSuperposition(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.getValue(SUPERPOSITION_ID).defaultBlockState());
        level.setBlockAndUpdate(pos.west(), BuiltInRegistries.BLOCK.getValue(
                Identifier.parse("ae2:creative_energy_cell")).defaultBlockState());
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof QuantumEnergyHost host) host.getEnergyStorage().setEnergy(60_000_000);
        var catalyst = LegacyItems.legacy(level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, pos, null));
        if (catalyst != null) catalyst.insertItem(0, new ItemStack(Items.FURNACE), false);
        try {
            var setFilter = be.getClass().getMethod("setFilterSlot", int.class, ItemStack.class);
            Item[] listed = {Items.IRON_INGOT, Items.COPPER_INGOT, Items.GOLD_INGOT, Items.GLASS, Items.SMOOTH_STONE};
            for (int i = 0; i < listed.length; i++) setFilter.invoke(be, i, new ItemStack(listed[i]));
            // Inputs never to use: every ore, by tag, and oak logs.
            setFilter.invoke(be, 27, new ItemStack(Items.IRON_ORE));
            be.getClass().getMethod("cycleFilterTag", int.class).invoke(be, 27);
            setFilter.invoke(be, 28, new ItemStack(Items.OAK_LOG));
        } catch (ReflectiveOperationException e) {
            Quantimium.LOGGER.warn("Could not fill the filter for wiki screenshots", e);
        }
    }

    private static void place(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        level.setBlockAndUpdate(pos, state);
    }

    /** Crops the open panel (or its overlay) out of the last frame and writes it as a PNG. */
    private static void capture(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof AbstractContainerScreen<?> screen)) {
            Quantimium.LOGGER.warn("Wiki screenshot {}: no screen open", name);
            return;
        }
        double scale = minecraft.getWindow().getGuiScale();
        int tabs = screen instanceof QuantumMachineScreen<?> machine ? machine.tabColumnWidth() : 0;
        Rect2i area = new Rect2i(screen.getGuiLeft(), screen.getGuiTop(), screen.getXSize() + tabs, screen.getYSize());
        if (screen instanceof QuantumMachineScreen<?> machine) {
            QuantumOverlay overlay = machine.currentOverlay();
            if (overlay != null && overlay.bounds() != null) area = overlay.bounds();
        }
        Rect2i shot = area;
        Screenshot.takeScreenshot(minecraft.getMainRenderTarget(), taken -> {
            try (NativeImage frame = taken) {
                int x = (int) Math.round(shot.getX() * scale);
                int y = (int) Math.round(shot.getY() * scale);
                int width = Math.min((int) Math.round(shot.getWidth() * scale), frame.getWidth() - x);
                int height = Math.min((int) Math.round(shot.getHeight() * scale), frame.getHeight() - y);
                try (NativeImage crop = new NativeImage(width, height, false)) {
                    for (int py = 0; py < height; py++) {
                        for (int px = 0; px < width; px++) {
                            crop.setPixel(px, py, frame.getPixel(x + px, y + py));
                        }
                    }
                    clearBackdrop(crop);
                    Files.createDirectories(out);
                    crop.writeToFile(out.resolve(name + ".png"));
                }
            } catch (Exception e) {
                Quantimium.LOGGER.error("Wiki screenshot {} failed", name, e);
            }
        });
    }

    /**
     * The black room behind the panel, as the screen's dimming leaves it: #0d0d0d, and #0c0d0d in
     * places. NativeImage pixels are ARGB.
     */
    private static final java.util.Set<Integer> BACKDROP = java.util.Set.of(0xFF0D0D0D, 0xFF0C0D0D);

    /**
     * Makes the backdrop round the panel transparent: the strip beside the tabs, the corners the
     * panel's rounded edge leaves. Filled in from the edges, so a pixel of the same dark inside a
     * panel is kept. tools/gui_backdrop.py does the same to images already captured.
     */
    private static void clearBackdrop(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        for (int x = 0; x < width; x++) {
            queue.add(new int[] {x, 0});
            queue.add(new int[] {x, height - 1});
        }
        for (int y = 0; y < height; y++) {
            queue.add(new int[] {0, y});
            queue.add(new int[] {width - 1, y});
        }
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            int x = at[0], y = at[1];
            if (x < 0 || y < 0 || x >= width || y >= height || !BACKDROP.contains(image.getPixel(x, y))) continue;
            image.setPixel(x, y, 0);
            queue.add(new int[] {x + 1, y});
            queue.add(new int[] {x - 1, y});
            queue.add(new int[] {x, y + 1});
            queue.add(new int[] {x, y - 1});
        }
    }
}

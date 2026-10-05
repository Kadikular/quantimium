package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.ContainmentHallStructure;
import com.kadikular.quantimium.entity.MirrorEndermite;
import com.kadikular.quantimium.entity.Veiled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import com.kadikular.quantimium.block.entity.ContainmentHallBlockEntity;
import com.kadikular.quantimium.block.entity.RiftAnchorBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumEnergyStorage;
import com.kadikular.quantimium.flux.ChunkFlux;
import com.kadikular.quantimium.flux.FluxEvents;
import com.kadikular.quantimium.init.ModAttachments;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.item.DecoherenceLanceItem;
import com.kadikular.quantimium.phase.MirrorPhase;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.phys.Vec3;
import com.kadikular.quantimium.phase.FluxRift;
import com.kadikular.quantimium.phase.FluxRiftManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Building blocks for Quantimium's GameTests.
 *
 * <p>Tests share one world, laid out in a grid, and several of our systems reach well past a test's
 * own floor: flux writes a 5×5 of chunks, a hall shields and pulls anomaly around it and can take an
 * Veiled from 32 blocks, rifts live in a per-dimension list. So every test that touches those
 * runs in its own batch (batches run one after another), and cleans up what it built before it
 * succeeds: a hall left running would go on shielding and capturing in later tests.
 *
 * <p>Each test names the wiki mechanics it covers in a {@code covers:} comment; the wiki's coverage
 * report reads them.
 */
public final class TestSupport {

    /**
     * The GameTest template floors: smooth stone with air above. The test framework places a template
     * one block up, so in test coordinates the floor is at y 1: stand things on y 2.
     */
    public static final String FLOOR_9 = "floor_9";
    public static final String FLOOR_17 = "floor_17";

    private TestSupport() {}

    /** The block entity at {@code relative}, whatever it is. */
    @org.jspecify.annotations.Nullable
    public static BlockEntity blockEntity(GameTestHelper helper, BlockPos relative) {
        return helper.getLevel().getBlockEntity(helper.absolutePos(relative));
    }

    // ---- Cleanup, pass or fail ----

    private static final Set<GameTestInfo> TRACKED = ConcurrentHashMap.newKeySet();
    private static final Map<GameTestInfo, List<ServerPlayer>> PLAYERS = new ConcurrentHashMap<>();

    /**
     * Clears up after a test however it ends. An assertion that fails stops the test before its own
     * cleanup line, and what it leaves (a stand-in player keeping an Veiled alive, a hall still
     * reaching 32 blocks, a rift in the dimension's list) breaks tests that run later. So when the test
     * passes or fails, this removes its players, the rifts standing on its floor, any Veiled and
     * mites in or near it, and every Quantimium machine left on it. Called by the helpers below, so a
     * test using them is covered; idempotent.
     */
    public static void track(GameTestHelper helper) {
        GameTestInfo info = helper.testInfo;
        if (!TRACKED.add(info)) return;
        ServerLevel level = helper.getLevel();
        AABB bounds = helper.getBounds().inflate(2.0);
        info.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo test) {}

            @Override
            public void testPassed(GameTestInfo test, GameTestRunner runner) {
                sweep(level, bounds, info);
            }

            @Override
            public void testFailed(GameTestInfo test, GameTestRunner runner) {
                sweep(level, bounds, info);
            }

            @Override
            public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {}
        });
    }

    private static void sweep(ServerLevel level, AABB bounds, GameTestInfo info) {
        TRACKED.remove(info);
        for (ServerPlayer player : PLAYERS.getOrDefault(info, List.of())) {
            if (!player.isRemoved()) level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
        PLAYERS.remove(info);
        // Machines first: breaking a hall lets its captive out, which the entity pass then removes.
        BlockPos.betweenClosedStream(bounds).forEach(pos -> {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null && Quantimium.MODID.equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).getNamespace())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        });
        for (FluxRift rift : List.copyOf(FluxRiftManager.rifts(level))) {
            if (bounds.contains(Vec3.atCenterOf(rift.anchor()))) FluxRiftManager.remove(level, rift);
        }
        AABB near = bounds.inflate(48.0);
        level.getEntitiesOfClass(Veiled.class, near).forEach(TestSupport::discard);
        level.getEntitiesOfClass(MirrorEndermite.class, near).forEach(TestSupport::discard);
    }

    /** A rift standing on {@code relative}, removed when the test ends. */
    public static FluxRift rift(GameTestHelper helper, BlockPos relative, int stage) {
        track(helper);
        return FluxRiftManager.spawn(helper.getLevel(), helper.absolutePos(relative), stage);
    }

    /** A Rift Seed used on the anchor at {@code anchor}. */
    public static FluxRift seed(GameTestHelper helper, BlockPos anchor) {
        track(helper);
        FluxRift rift = FluxRiftManager.seed(helper.getLevel(), helper.absolutePos(anchor));
        // As the block does when a seed is used on it.
        if (helper.getBlockEntity(anchor, RiftAnchorBlockEntity.class) instanceof RiftAnchorBlockEntity block) block.holdNow(helper.getLevel());
        return rift;
    }

    /** Sets the flux and anomaly of the chunk holding {@code relative}, and restarts its drain clock. */
    public static void setField(GameTestHelper helper, BlockPos relative, double flux, double anomaly) {
        ServerLevel level = helper.getLevel();
        LevelChunk chunk = level.getChunkAt(helper.absolutePos(relative));
        ChunkFlux data = chunk.getData(ModAttachments.CHUNK_FLUX);
        data.clear();
        data.setField(flux, anomaly);
        if (!data.isEmpty()) FluxEvents.markActive(level, chunk.getPos());
        data.setLastDrainTick(level.getGameTime());
        chunk.markUnsaved();
    }

    /**
     * Empties the field, pending pools and lingering containment included, in every chunk the test's
     * floor touches and two beyond. Machines emit into the 3×3 round them and the field spreads, so a
     * test before this one may have left any of them charged, and a hall's containment lasts a second
     * or two after it's gone; this one should leave nothing for the next.
     */
    public static void clearField(GameTestHelper helper) {
        AABB bounds = helper.getBounds();
        int minX = SectionPos.blockToSectionCoord((int) Math.floor(bounds.minX)) - 2;
        int maxX = SectionPos.blockToSectionCoord((int) Math.floor(bounds.maxX)) + 2;
        int minZ = SectionPos.blockToSectionCoord((int) Math.floor(bounds.minZ)) - 2;
        int maxZ = SectionPos.blockToSectionCoord((int) Math.floor(bounds.maxZ)) + 2;
        ServerLevel level = helper.getLevel();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                LevelChunk chunk = level.getChunk(x, z);
                ChunkFlux data = chunk.getData(ModAttachments.CHUNK_FLUX);
                data.clear();
                data.clearContainment();
                data.setLastDrainTick(level.getGameTime());
                chunk.markUnsaved();
            }
        }
    }

    public static void fill(QuantumEnergyStorage storage) {
        storage.setEnergy(storage.getMaxEnergyStored());
    }

    /**
     * A Containment Hall with its controller at {@code centre}: plinth base, three layers of glass
     * round a clear cell, plinth cap, and {@code arms} arms in compass order (north, east, south,
     * west). Formed straight away rather than on its next once-a-second check.
     */
    public static ContainmentHallBlockEntity buildHall(GameTestHelper helper, BlockPos centre, int arms) {
        track(helper);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos column = centre.offset(dx, 0, dz);
                boolean middle = dx == 0 && dz == 0;
                helper.setBlock(column, middle ? ModBlocks.ANOMALY_CONTAINMENT_HALL.get() : ModBlocks.QUANTUM_FOUNDRY_PLINTH.get());
                for (int dy = 1; dy <= ContainmentHallStructure.WALL_HEIGHT; dy++) {
                    helper.setBlock(column.above(dy), middle ? Blocks.AIR : ModBlocks.QUANTUM_ATTUNED_GLASS.get());
                }
                helper.setBlock(column.above(ContainmentHallStructure.CAP_OFFSET), ModBlocks.QUANTUM_FOUNDRY_PLINTH.get());
            }
        }
        for (int slot = 0; slot < arms; slot++) {
            Direction direction = ContainmentHallStructure.armDirection(slot);
            helper.setBlock(centre.relative(direction, 2), ModBlocks.QUANTUM_FOUNDRY_CONDUIT.get());
            helper.setBlock(centre.relative(direction, 3), ModBlocks.QUANTUM_FOUNDRY_PILLAR.get());
            helper.setBlock(centre.relative(direction, 3).above(), ModBlocks.QUANTUM_FOUNDRY_ATTUNEMENT_TANK.get());
        }
        ContainmentHallBlockEntity hall = helper.getBlockEntity(centre, ContainmentHallBlockEntity.class);
        hall.revalidateStructure();
        return hall;
    }

    /** A formed hall, charged and stocked with residue. */
    public static ContainmentHallBlockEntity readyHall(GameTestHelper helper, BlockPos centre, int arms, int residue) {
        ContainmentHallBlockEntity hall = buildHall(helper, centre, arms);
        fill(hall.getEnergyStorage());
        if (residue > 0) hall.getResidue().setStackInSlot(0, new ItemStack(ModItems.RIFT_RESIDUE.get(), residue));
        return hall;
    }

    /** Breaks the hall's controller, so it stops shielding and reaching for Veiled in later tests. */
    public static void removeHall(GameTestHelper helper, BlockPos centre) {
        helper.setBlock(centre, Blocks.AIR);
    }

    /** Gone outright, so no later test meets it. */
    public static void removeRift(GameTestHelper helper, FluxRift rift) {
        if (rift != null) FluxRiftManager.remove(helper.getLevel(), rift);
    }

    /**
     * A stand-in player at {@code relative}, so things that need someone nearby (an Veiled
     * despawns without one) stay put. Added straight to the level: it counts as a player there but
     * never logs in, so no login handlers try to talk to a client that is not there. Creative, so
     * nothing hunts it. Remove it when done.
     */
    public static ServerPlayer player(GameTestHelper helper, BlockPos relative) {
        return player(helper, relative, GameType.CREATIVE);
    }

    /** A stand-in player in the given game mode. In survival it can be hurt and hunted. */
    public static TestPlayer player(GameTestHelper helper, BlockPos relative, GameType mode) {
        track(helper);
        ServerLevel level = helper.getLevel();
        TestPlayer player = new TestPlayer(level, new GameProfile(UUID.randomUUID(), "quantimium-test"));
        BlockPos at = helper.absolutePos(relative);
        player.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0f, 0.0f);
        player.gameMode.changeGameModeForPlayer(mode);
        level.addNewPlayer(player);
        PLAYERS.computeIfAbsent(helper.testInfo, key -> new CopyOnWriteArrayList<>()).add(player);
        return player;
    }

    /**
     * A survival stand-in already in the mirror, the way a tear takes a player there but without
     * the return tear: nothing else in the test needs a way home.
     */
    public static TestPlayer phasedPlayer(GameTestHelper helper, BlockPos relative) {
        return phasedPlayer(helper, relative, GameType.SURVIVAL);
    }

    /** A creative one is never picked as a target, so nothing it does is mixed up with shadowing. */
    public static TestPlayer phasedPlayer(GameTestHelper helper, BlockPos relative, GameType mode) {
        TestPlayer player = player(helper, relative, mode);
        MirrorPhase.enter(player, false);
        return player;
    }

    /** Turns the player's head so its eyes look straight at {@code target}. */
    public static void aimAt(ServerPlayer player, Vec3 target) {
        player.lookAt(EntityAnchorArgument.Anchor.EYES, target);
    }

    /** A Decoherence Lance charged full. */
    public static ItemStack chargedLance() {
        ItemStack lance = new ItemStack(ModItems.DECOHERENCE_LANCE.get());
        DecoherenceLanceItem.setEnergy(lance, DecoherenceLanceItem.CAPACITY);
        return lance;
    }

    /**
     * One tick of holding the Lance's beam, as holding use does: charge spent, target found from
     * where the player looks, and acted on. {@code held} counts ticks since the beam started.
     */
    public static void lanceTick(ServerPlayer player, ItemStack lance, int held) {
        lance.onUseTick(player.level(), player, lance.getUseDuration(player) - held);
    }

    public static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        if (!player.isRemoved()) helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }

    /** Removes an entity that cannot be killed (the Veiled), without drops or effects. */
    public static void discard(Entity entity) {
        if (entity != null && !entity.isRemoved()) entity.discard();
    }
}

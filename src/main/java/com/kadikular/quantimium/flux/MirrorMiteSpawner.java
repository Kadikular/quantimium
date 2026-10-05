package com.kadikular.quantimium.flux;

import com.kadikular.quantimium.entity.MirrorEndermite;
import com.kadikular.quantimium.init.ModEntities;
import com.kadikular.quantimium.phase.MirrorPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** Sparse flux-scaled mirror mites around phased players. Light level is ignored. */
public final class MirrorMiteSpawner {

    private static final double SCAN_RANGE = 32.0;

    private MirrorMiteSpawner() {}

    public static void tick(ServerPlayer player) {
        if (!MirrorPhase.isPhased(player)) return;
        ServerLevel level = player.level();
        QuantumFlux.Neighbourhood field = QuantumFlux.chunk(level, player.blockPosition());
        FluxBand band = FluxBand.of(Math.max(field.flux(), field.anomaly()));
        int cap = cap(band);
        if (cap <= 0) return;

        AABB scan = player.getBoundingBox().inflate(SCAN_RANGE);
        int nearby = level.getEntities(ModEntities.MIRROR_ENDERMITE.get(), scan, mite -> true).size();
        if (nearby >= cap) return;
        if (level.getRandom().nextDouble() >= chance(band)) return;

        spawnNear(level, player.blockPosition());
    }

    /** One mite on open floor 6–14 blocks from {@code origin}, or null if nowhere fits. */
    @Nullable
    public static MirrorEndermite spawnNear(ServerLevel level, BlockPos origin) {
        return spawnNear(level, origin, mite -> {});
    }

    /** As above, with {@code prepare} run before the mite joins the level, while tracking is still undecided. */
    @Nullable
    public static MirrorEndermite spawnNear(ServerLevel level, BlockPos origin, Consumer<MirrorEndermite> prepare) {
        BlockPos pos = findSite(level, origin, level.getRandom());
        if (pos == null) return null;
        MirrorEndermite mite = ModEntities.MIRROR_ENDERMITE.get().create(level, EntitySpawnReason.EVENT);
        if (mite == null) return null;
        mite.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                level.getRandom().nextFloat() * 360.0f, 0.0f);
        mite.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
        mite.setPersistenceRequired();
        prepare.accept(mite);
        level.addFreshEntity(mite);
        return mite;
    }

    private static int cap(FluxBand band) {
        return switch (band) {
            case LOW -> 0;
            case MEDIUM -> 2;
            case HIGH -> 3;
            case CRITICAL -> 5;
            case SINGULARITY -> 8;
        };
    }

    private static double chance(FluxBand band) {
        return switch (band) {
            case LOW -> 0.0;
            case MEDIUM -> 0.02;
            case HIGH -> 0.05;
            case CRITICAL -> 0.10;
            case SINGULARITY -> 0.16;
        };
    }

    private static BlockPos findSite(ServerLevel level, BlockPos origin, RandomSource random) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double distance = 6.0 + random.nextDouble() * 8.0;
            int x = origin.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = origin.getZ() + Mth.floor(Math.sin(angle) * distance);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - origin.getY()) > 10) {
                y = origin.getY();
                BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, y, z);
                boolean found = false;
                for (int step = 0; step < 10; step++) {
                    if (level.getBlockState(cursor).isAir()
                            && level.getBlockState(cursor.above()).isAir()
                            && level.getBlockState(cursor.below()).isSolid()) {
                        y = cursor.getY();
                        found = true;
                        break;
                    }
                    cursor.move(0, -1, 0);
                }
                if (!found) continue;
            }
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.isLoaded(pos)) continue;
            if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) continue;
            if (!level.getBlockState(pos.below()).isSolid()) continue;
            if (!level.noCollision(ModEntities.MIRROR_ENDERMITE.get().getSpawnAABB(
                    pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) {
                continue;
            }
            return pos;
        }
        return null;
    }
}

package com.kadikular.quantimium.client;

import com.kadikular.quantimium.laser.LaserBeam;
import com.kadikular.quantimium.laser.LaserBeamView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** The lasers the client draws, for what needs to know where they are before the server says (a Spectroscope's click). */
public final class ClientBeams {

    private ClientBeams() {}

    /** Whether {@code player}'s look meets a laser within {@code reach}, short of the first solid block. */
    public static boolean aimedAt(Level level, Player player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        var hit = level.clip(new ClipContext(eye, eye.add(look.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double far = hit.getType() == HitResult.Type.MISS ? reach : hit.getLocation().distanceTo(eye) + 0.3;
        int range = (int) Math.ceil(reach) + 64;
        ChunkPos centre = ChunkPos.containing(BlockPos.containing(eye));
        int chunks = Math.ceilDiv(range, 16);
        for (int cx = centre.x() - chunks; cx <= centre.x() + chunks; cx++) {
            for (int cz = centre.z() - chunks; cz <= centre.z() + chunks; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (BlockEntity entity : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (!(entity instanceof LaserBeamView view)) continue;
                    for (LaserBeam beam : view.syncedBeams()) {
                        if (meets(beam.points(), eye, look, far)) return true;
                    }
                }
            }
        }
        return false;
    }

    /** Whether the look from {@code eye} along {@code look}, out to {@code far}, passes within half a block of the line through {@code points}. */
    private static boolean meets(List<Vec3> points, Vec3 eye, Vec3 look, double far) {
        for (int p = 0; p + 1 < points.size(); p++) {
            Vec3 a = points.get(p), b = points.get(p + 1);
            for (double gone = 0; gone <= far; gone += 0.2) {
                Vec3 at = eye.add(look.scale(gone));
                Vec3 ab = b.subtract(a);
                double length = ab.dot(ab);
                double t = length == 0 ? 0 : Math.clamp(at.subtract(a).dot(ab) / length, 0.0, 1.0);
                if (at.distanceToSqr(a.add(ab.scale(t))) <= 0.25) return true;
            }
        }
        return false;
    }
}

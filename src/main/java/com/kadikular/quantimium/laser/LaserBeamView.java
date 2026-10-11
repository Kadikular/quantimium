package com.kadikular.quantimium.laser;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/** A block whose beams the client draws: the beams as the server last showed them. */
public interface LaserBeamView {

    /** Where {@code beam} starts and stops, for drawing: its own ends, unless the block knows better (an area prism's beam into a moving entity). */
    default net.minecraft.world.phys.Vec3[] endsOf(LaserBeam beam) {
        return beam.ends();
    }

    /** How much wider than usual to draw {@code beam}: a cavity's held light glows wider. */
    default float widthScale(LaserBeam beam) {
        return 1.0f;
    }


    List<LaserBeam> syncedBeams();

    /** Whether two sets of beams look the same: same shape, and the same step of brightness. */
    static boolean sameLook(List<LaserBeam> a, List<LaserBeam> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            LaserBeam x = a.get(i);
            LaserBeam y = b.get(i);
            if (!x.sameRun(y) || x.live() != y.live() || x.lenses() != y.lenses() || LaserNetworks.brightness(x.flow()) != LaserNetworks.brightness(y.flow())) {
                return false;
            }
        }
        return true;
    }

    static CompoundTag save(List<LaserBeam> beams) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (LaserBeam beam : beams) list.add(beam.save());
        tag.put("List", list);
        return tag;
    }

    static List<LaserBeam> load(BlockPos from, CompoundTag tag) {
        List<LaserBeam> beams = new ArrayList<>();
        for (Tag entry : tag.getListOrEmpty("List")) {
            if (entry instanceof CompoundTag beam) beams.add(LaserBeam.load(from, beam));
        }
        return List.copyOf(beams);
    }
}

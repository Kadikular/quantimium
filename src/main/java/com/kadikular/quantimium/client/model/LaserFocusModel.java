package com.kadikular.quantimium.client.model;

import com.kadikular.quantimium.block.entity.LaserFocusBlockEntity;
import com.kadikular.quantimium.block.entity.LaserFocusBlockEntity.Core;
import com.kadikular.quantimium.block.entity.LaserFocusBlockEntity.Part;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A Laser Focus block's model: its focus, and what's on each face, drawn from what its block entity holds (its
 * {@link LaserFocusBlockEntity#LAYOUT layout}) rather than from block states, of which there would be tens of thousands.
 * Each piece is an ordinary block model, turned to its face.
 */
public final class LaserFocusModel implements DynamicBlockStateModel {

    private final BlockStateModel laser;
    private final BlockStateModel clear;
    private final Map<Part, Map<Direction, BlockStateModel>> parts;
    private final int flags;

    private LaserFocusModel(BlockStateModel laser, BlockStateModel clear, Map<Part, Map<Direction, BlockStateModel>> parts) {
        this.laser = laser;
        this.clear = clear;
        this.parts = parts;
        int flags = laser.materialFlags() | clear.materialFlags();
        for (Map<Direction, BlockStateModel> faces : parts.values()) for (BlockStateModel model : faces.values()) flags |= model.materialFlags();
        this.flags = flags;
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> out) {
        Integer layout = level.getModelData(pos).get(LaserFocusBlockEntity.LAYOUT);
        // Not known yet (or an item, or a preview): a plain focus.
        int packed = layout != null ? layout : Core.LASER.ordinal();
        long seed = random.nextLong();
        Core core = LaserFocusBlockEntity.coreOf(packed);
        if (core != Core.NONE) {
            random.setSeed(seed);
            (core == Core.CLEAR ? clear : laser).collectParts(level, pos, state, random, out);
        }
        for (Direction face : Direction.values()) {
            Map<Direction, BlockStateModel> faces = parts.get(LaserFocusBlockEntity.partOf(packed, face));
            BlockStateModel model = faces == null ? null : faces.get(face);
            if (model == null) continue;
            random.setSeed(seed);
            model.collectParts(level, pos, state, random, out);
        }
    }

    @Override
    @Nullable
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        Integer layout = level.getModelData(pos).get(LaserFocusBlockEntity.LAYOUT);
        return new GeometryKey(layout != null ? layout : Core.LASER.ordinal(), this);
    }

    private record GeometryKey(int layout, LaserFocusModel model) {}

    @Override
    public Material.Baked particleMaterial() {
        return laser.particleMaterial();
    }

    @Override
    public int materialFlags() {
        return flags;
    }

    /** Read from its blockstate file: the two focuses, and each attachment turned to each face. */
    public record Unbaked(BlockStateModel.Unbaked laser, BlockStateModel.Unbaked clear, Map<Direction, BlockStateModel.Unbaked> stand,
                          Map<Direction, BlockStateModel.Unbaked> emitter, Map<Direction, BlockStateModel.Unbaked> cover)
            implements CustomUnbakedBlockStateModel {

        private static final Codec<Map<Direction, BlockStateModel.Unbaked>> FACES = Codec.unboundedMap(Direction.CODEC, BlockStateModel.Unbaked.CODEC);

        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                BlockStateModel.Unbaked.CODEC.fieldOf("focus").forGetter(Unbaked::laser),
                BlockStateModel.Unbaked.CODEC.fieldOf("clear_focus").forGetter(Unbaked::clear),
                FACES.fieldOf("stand").forGetter(Unbaked::stand),
                FACES.fieldOf("emitter").forGetter(Unbaked::emitter),
                FACES.fieldOf("cover").forGetter(Unbaked::cover)).apply(instance, Unbaked::new));

        @Override
        public BlockStateModel bake(ModelBaker baker) {
            Map<Part, Map<Direction, BlockStateModel>> parts = new EnumMap<>(Part.class);
            parts.put(Part.STAND, bake(stand, baker));
            parts.put(Part.EMITTER, bake(emitter, baker));
            parts.put(Part.COVER, bake(cover, baker));
            return new LaserFocusModel(laser.bake(baker), clear.bake(baker), parts);
        }

        private static Map<Direction, BlockStateModel> bake(Map<Direction, BlockStateModel.Unbaked> faces, ModelBaker baker) {
            Map<Direction, BlockStateModel> out = new EnumMap<>(Direction.class);
            faces.forEach((face, model) -> out.put(face, model.bake(baker)));
            return out;
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            laser.resolveDependencies(resolver);
            clear.resolveDependencies(resolver);
            for (Map<Direction, BlockStateModel.Unbaked> faces : List.of(stand, emitter, cover)) {
                for (BlockStateModel.Unbaked model : faces.values()) model.resolveDependencies(resolver);
            }
        }

        @Override
        public MapCodec<? extends CustomUnbakedBlockStateModel> codec() {
            return MAP_CODEC;
        }
    }
}

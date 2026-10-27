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
    /** Each attachment's model on each face, by {@link #key}: its part, and for a receptacle its role and whether lit. */
    private final Map<String, Map<Direction, BlockStateModel>> parts;
    private final int flags;

    private LaserFocusModel(BlockStateModel laser, BlockStateModel clear, Map<String, Map<Direction, BlockStateModel>> parts) {
        this.laser = laser;
        this.clear = clear;
        this.parts = parts;
        int flags = laser.materialFlags() | clear.materialFlags();
        for (Map<Direction, BlockStateModel> faces : parts.values()) for (BlockStateModel model : faces.values()) flags |= model.materialFlags();
        this.flags = flags;
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> out) {
        Long layout = level.getModelData(pos).get(LaserFocusBlockEntity.LAYOUT);
        // Not known yet (or an item, or a preview): a plain focus.
        long packed = layout != null ? layout : Core.LASER.ordinal();
        long seed = random.nextLong();
        Core core = LaserFocusBlockEntity.coreOf(packed);
        if (core != Core.NONE) {
            random.setSeed(seed);
            (core == Core.CLEAR ? clear : laser).collectParts(level, pos, state, random, out);
        }
        for (Direction face : Direction.values()) {
            Part part = LaserFocusBlockEntity.partOf(packed, face);
            if (part == Part.NONE) continue;
            Map<Direction, BlockStateModel> faces = parts.get(key(part, LaserFocusBlockEntity.lookOf(packed, face)));
            BlockStateModel model = faces == null ? null : faces.get(face);
            if (model == null) continue;
            random.setSeed(seed);
            model.collectParts(level, pos, state, random, out);
        }
    }

    @Override
    @Nullable
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        Long layout = level.getModelData(pos).get(LaserFocusBlockEntity.LAYOUT);
        return new GeometryKey(layout != null ? layout : Core.LASER.ordinal(), this);
    }

    private record GeometryKey(long layout, LaserFocusModel model) {}

    /** The name of {@code part}'s model with {@code look}: "stand", "emitter", "receptacle_provider_lit". */
    static String key(Part part, int look) {
        if (part == Part.RECEPTACLE) {
            var roles = com.kadikular.quantimium.laser.LogisticsMember.Role.values();
            return "receptacle_" + roles[Math.min(look / 2, roles.length - 1)].getSerializedName() + ((look & 1) != 0 ? "_lit" : "");
        }
        return part.name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public Material.Baked particleMaterial() {
        return laser.particleMaterial();
    }

    @Override
    public int materialFlags() {
        return flags;
    }

    /** Read from its blockstate file: the two focuses, and each attachment (by {@link #key}) turned to each face. */
    public record Unbaked(BlockStateModel.Unbaked laser, BlockStateModel.Unbaked clear, Map<String, Map<Direction, BlockStateModel.Unbaked>> parts)
            implements CustomUnbakedBlockStateModel {

        private static final Codec<Map<Direction, BlockStateModel.Unbaked>> FACES = Codec.unboundedMap(Direction.CODEC, BlockStateModel.Unbaked.CODEC);

        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                BlockStateModel.Unbaked.CODEC.fieldOf("focus").forGetter(Unbaked::laser),
                BlockStateModel.Unbaked.CODEC.fieldOf("clear_focus").forGetter(Unbaked::clear),
                Codec.unboundedMap(Codec.STRING, FACES).fieldOf("parts").forGetter(Unbaked::parts)).apply(instance, Unbaked::new));

        @Override
        public BlockStateModel bake(ModelBaker baker) {
            Map<String, Map<Direction, BlockStateModel>> baked = new java.util.HashMap<>();
            parts.forEach((key, faces) -> {
                Map<Direction, BlockStateModel> out = new EnumMap<>(Direction.class);
                faces.forEach((face, model) -> out.put(face, model.bake(baker)));
                baked.put(key, out);
            });
            return new LaserFocusModel(laser.bake(baker), clear.bake(baker), baked);
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            laser.resolveDependencies(resolver);
            clear.resolveDependencies(resolver);
            for (Map<Direction, BlockStateModel.Unbaked> faces : parts.values()) {
                for (BlockStateModel.Unbaked model : faces.values()) model.resolveDependencies(resolver);
            }
        }

        @Override
        public MapCodec<? extends CustomUnbakedBlockStateModel> codec() {
            return MAP_CODEC;
        }
    }
}

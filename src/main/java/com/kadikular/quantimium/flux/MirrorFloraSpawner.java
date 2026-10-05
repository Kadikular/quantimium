package com.kadikular.quantimium.flux;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.registries.BuiltInRegistries;
import com.kadikular.quantimium.block.QuantumFoundryStructure;
import com.kadikular.quantimium.init.ModAttachments;
import com.kadikular.quantimium.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Bounded once-per-second seeding for persistent mirror flora in active field chunks. */
public final class MirrorFloraSpawner {

    private static final long SALT = 0x51D3A70EC11EL;
    private static final long KIND_SALT = 0x2F5B9C1D77A3L;
    private static final long FOLD_PRIME = 0x100000001B3L;
    private static final Direction[] HORIZONTAL = Direction.Plane.HORIZONTAL.stream().toArray(Direction[]::new);

    private MirrorFloraSpawner() {}

    public static boolean tick(ServerLevel level, LevelChunk chunk, ChunkFlux field) {
        MirrorFloraData flora = chunk.getData(ModAttachments.MIRROR_FLORA);
        boolean changed = prune(level, flora, pressureBand(field));
        FluxBand band = pressureBand(field);
        if (band == FluxBand.LOW) {
            if (changed) chunk.markUnsaved();
            return changed;
        }

        int attempts = switch (band) {
            case LOW -> 0;
            case MEDIUM -> 8;
            case HIGH -> 16;
            case CRITICAL -> 28;
            case SINGULARITY -> 40;
        };
        int allowance = switch (band) {
            case LOW -> 0;
            case MEDIUM -> 1;
            case HIGH -> 2;
            case CRITICAL -> 3;
            case SINGULARITY -> 4;
        };

        RandomSource random = level.getRandom();
        int vines = 0;
        int plants = 0;
        int hanging = 0;
        for (int attempt = 0; attempt < attempts && vines < allowance; attempt++) {
            if (trySeedVine(level, chunk, flora, band, random)) {
                vines++;
                changed = true;
            }
        }
        for (int attempt = 0; attempt < attempts && plants < allowance; attempt++) {
            if (trySeedFloor(level, chunk, flora, band, random)) {
                plants++;
                changed = true;
            }
        }
        for (int attempt = 0; attempt < attempts && hanging < allowance; attempt++) {
            if (trySeedHanging(level, chunk, flora, band, random)) {
                hanging++;
                changed = true;
            }
        }
        changed |= spreadVines(level, flora, band, random);
        if (changed) chunk.markUnsaved();
        return changed;
    }

    public static FluxBand pressureBand(ServerLevel level, BlockPos pos) {
        QuantumFlux.Neighbourhood field = QuantumFlux.chunk(level, pos);
        return FluxBand.of(Math.max(field.flux(), field.anomaly()));
    }

    static FluxBand pressureBand(ChunkFlux data) {
        return FluxBand.of(Math.max(data.flux(), data.anomaly()));
    }

    /**
     * Stable eligibility means lowering the field removes a predictable subset rather than
     * reshuffling every patch. Higher bands monotonically admit more faces.
     */
    public static boolean selected(BlockPos pos, Direction face, FluxBand band) {
        int cutoff = switch (band) {
            case LOW -> 7;
            case MEDIUM -> 19;
            case HIGH -> 32;
            case CRITICAL -> 45;
            case SINGULARITY -> 64;
        };

        return (int) (scramble(pos, face, SALT) >>> 56) < cutoff;
    }

    /**
     * {@link BlockPos#asLong()} stores Y in the low bits and multiplicative mixing never carries
     * information downwards, so reading low bits of a mixed packed position tracks the Y level
     * almost exactly: a flat surface would be uniformly eligible or uniformly barren. Fold the
     * coordinates in separately and read the high bits instead.
     */
    private static long scramble(BlockPos pos, Direction face, long salt) {
        long hash = salt;
        hash = (hash ^ pos.getX()) * FOLD_PRIME;
        hash = (hash ^ pos.getY()) * FOLD_PRIME;
        hash = (hash ^ pos.getZ()) * FOLD_PRIME;
        hash = (hash ^ face.ordinal()) * FOLD_PRIME;
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;
        hash *= 0xc4ceb9fe1a85ec53L;
        return hash ^ (hash >>> 33);
    }

    private static boolean trySeedVine(ServerLevel level, LevelChunk chunk, MirrorFloraData flora,
                                       FluxBand band, RandomSource random) {
        int x = chunk.getPos().getMinBlockX() + random.nextInt(16);
        int z = chunk.getPos().getMinBlockZ() + random.nextInt(16);
        int y = randomColumnY(level, x, z, random);
        if (y == Integer.MIN_VALUE) return false;
        Direction outward = HORIZONTAL[random.nextInt(HORIZONTAL.length)];
        BlockPos host = new BlockPos(x, y, z);
        BlockPos target = host.relative(outward);
        if (!level.isLoaded(target)) return false;

        Direction supportFace = outward.getOpposite();
        if (!selected(target, supportFace, band)) return false;
        if (!level.getBlockState(target).isAir() || !supportsVine(level, target, supportFace)) {
            return false;
        }
        MirrorFloraData.Entry current = flora.get(target);
        if (current != null && current.kind() != MirrorFloraData.Kind.VINE) return false;
        return flora.addVineFace(target, supportFace);
    }

    private static boolean trySeedFloor(ServerLevel level, LevelChunk chunk, MirrorFloraData flora,
                                        FluxBand band, RandomSource random) {
        int x = chunk.getPos().getMinBlockX() + random.nextInt(16);
        int z = chunk.getPos().getMinBlockZ() + random.nextInt(16);
        int hostY = randomFloorHost(level, x, z, random);
        if (hostY == Integer.MIN_VALUE) return false;
        BlockPos host = new BlockPos(x, hostY, z);
        return placeFloor(level, flora, host, host.above(), band);
    }

    private static boolean trySeedHanging(ServerLevel level, LevelChunk chunk, MirrorFloraData flora,
                                          FluxBand band, RandomSource random) {
        int x = chunk.getPos().getMinBlockX() + random.nextInt(16);
        int z = chunk.getPos().getMinBlockZ() + random.nextInt(16);
        int hostY = randomCeilingHost(level, x, z, random);
        if (hostY == Integer.MIN_VALUE) return false;
        BlockPos host = new BlockPos(x, hostY, z);
        return placeHanging(level, flora, host, host.below(), band);
    }

    /**
     * Deterministic Low-band dressing so unloaded wilderness still has sparse flora after a chunk
     * loads, without waiting for flux to mark the chunk active.
     */
    public static boolean seedBaseline(ServerLevel level, LevelChunk chunk) {
        MirrorFloraData flora = chunk.getData(ModAttachments.MIRROR_FLORA);
        boolean changed = false;
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                changed |= seedColumn(level, flora, minX + localX, minZ + localZ, FluxBand.LOW);
            }
        }
        if (changed) chunk.markUnsaved();
        return changed;
    }

    private static boolean placeFloor(ServerLevel level, MirrorFloraData flora, BlockPos host,
                                      BlockPos target, FluxBand band) {
        if (!level.isLoaded(target) || !level.getBlockState(target).isAir()) return false;
        if (flora.get(target) != null) return false;
        if (!selected(target, Direction.DOWN, band)) return false;

        MirrorFloraData.Kind kind = floorKind(target);
        BlockState plant = plantState(kind);
        if (!supportsFloor(level, host, plant)) return false;

        if (kind == MirrorFloraData.Kind.TALL_GRASS) {
            BlockPos above = target.above();
            if (!level.isLoaded(above) || !level.getBlockState(above).isAir()
                    || flora.get(above) != null) {
                return false;
            }
        }
        return flora.put(MirrorFloraData.Entry.floor(target, kind));
    }

    private static boolean placeHanging(ServerLevel level, MirrorFloraData flora, BlockPos host,
                                        BlockPos target, FluxBand band) {
        if (!level.isLoaded(target) || !level.getBlockState(target).isAir()) return false;
        if (flora.get(target) != null) return false;
        if (!selected(target, Direction.UP, band)) return false;
        BlockState plant = ModBlocks.MIRROR_HANGING_ROOTS.get().defaultBlockState();
        if (!supportsCeiling(level, host, plant)) return false;
        return flora.put(MirrorFloraData.Entry.floor(target, MirrorFloraData.Kind.HANGING_ROOTS));
    }

    /** Walk a column and dress every floor/ceiling transition, including caves. */
    private static boolean seedColumn(ServerLevel level, MirrorFloraData flora, int x, int z,
                                      FluxBand band) {
        int minY = level.getMinY() + 1;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (top <= minY) return false;
        boolean changed = false;
        BlockState previous = level.getBlockState(new BlockPos(x, minY - 1, z));
        for (int y = minY; y <= top; y++) {
            BlockState current = level.getBlockState(new BlockPos(x, y, z));
            boolean air = current.isAir();
            boolean wasAir = previous.isAir();
            if (air && !wasAir) {
                changed |= placeFloor(level, flora, new BlockPos(x, y - 1, z), new BlockPos(x, y, z), band);
            } else if (!air && wasAir) {
                changed |= placeHanging(level, flora, new BlockPos(x, y, z), new BlockPos(x, y - 1, z), band);
            }
            previous = current;
        }
        return changed;
    }

    private static int randomColumnY(ServerLevel level, int x, int z, RandomSource random) {
        int minY = level.getMinY() + 1;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (top <= minY) return Integer.MIN_VALUE;
        return minY + random.nextInt(top - minY);
    }

    private static int randomFloorHost(ServerLevel level, int x, int z, RandomSource random) {
        int minY = level.getMinY() + 1;
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (surface < minY) return Integer.MIN_VALUE;
        if (random.nextInt(4) == 0) return surface;
        int start = minY + random.nextInt(surface - minY + 1);
        for (int y = start; y >= minY; y--) {
            BlockPos host = new BlockPos(x, y, z);
            if (!level.getBlockState(host).isAir() && level.getBlockState(host.above()).isAir()) {
                return y;
            }
        }
        return surface;
    }

    private static int randomCeilingHost(ServerLevel level, int x, int z, RandomSource random) {
        int minY = level.getMinY() + 2;
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (surface < minY) return Integer.MIN_VALUE;
        int start = minY + random.nextInt(surface - minY + 1);
        for (int y = start; y <= surface; y++) {
            BlockPos host = new BlockPos(x, y, z);
            if (!level.getBlockState(host).isAir() && level.getBlockState(host.below()).isAir()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    public static BlockState plantState(MirrorFloraData.Kind kind) {
        return switch (kind) {
            case SHORT_GRASS -> ModBlocks.MIRROR_SHORT_GRASS.get().defaultBlockState();
            case DEAD_BUSH -> ModBlocks.MIRROR_DEAD_BUSH.get().defaultBlockState();
            case TALL_GRASS -> ModBlocks.MIRROR_TALL_GRASS.get().defaultBlockState();
            case VINE, HANGING_ROOTS -> throw new IllegalStateException(kind + " is not floor flora");
        };
    }

    private static boolean spreadVines(ServerLevel level, MirrorFloraData flora, FluxBand band,
                                       RandomSource random) {
        int denominator = switch (band) {
            case LOW -> Integer.MAX_VALUE;
            case MEDIUM -> 8;
            case HIGH -> 5;
            case CRITICAL -> 3;
            case SINGULARITY -> 2;
        };
        boolean changed = false;
        for (MirrorFloraData.Entry entry : flora.mutableSnapshot()) {
            if (entry.kind() != MirrorFloraData.Kind.VINE || random.nextInt(denominator) != 0) {
                continue;
            }
            Direction source = randomFace(entry, random);
            if (source == null) continue;
            Direction step = HORIZONTAL[random.nextInt(HORIZONTAL.length)];
            BlockPos target = entry.pos().relative(step);
            Direction support = step.getOpposite();
            if (!level.isLoaded(target) || !selected(target, support, band)) continue;
            if (!level.getBlockState(target).isAir() || !supportsVine(level, target, support)) {
                continue;
            }

            LevelChunk targetChunk = level.getChunkAt(target);
            MirrorFloraData targetFlora = targetChunk.getData(ModAttachments.MIRROR_FLORA);
            MirrorFloraData.Entry current = targetFlora.get(target);
            if (current != null && current.kind() != MirrorFloraData.Kind.VINE) continue;
            if (targetFlora.addVineFace(target, support)) {
                targetChunk.markUnsaved();
                changed = true;
            }
        }
        return changed;
    }

    private static Direction randomFace(MirrorFloraData.Entry entry, RandomSource random) {
        Direction picked = null;
        int seen = 0;
        for (Direction face : HORIZONTAL) {
            if (!entry.hasFace(face)) continue;
            seen++;
            if (random.nextInt(seen) == 0) picked = face;
        }
        return picked;
    }

    public static MirrorFloraData.Kind floorKind(BlockPos pos) {
        int roll = (int) (scramble(pos, Direction.UP, KIND_SALT) >>> 61);
        if (roll < 4) return MirrorFloraData.Kind.SHORT_GRASS;
        if (roll < 6) return MirrorFloraData.Kind.TALL_GRASS;
        return MirrorFloraData.Kind.DEAD_BUSH;
    }

    private static boolean prune(ServerLevel level, MirrorFloraData flora, FluxBand band) {
        boolean changed = false;
        for (MirrorFloraData.Entry entry : flora.mutableSnapshot()) {
            BlockPos pos = entry.pos();
            if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) {
                changed |= flora.remove(pos);
                continue;
            }
            if (entry.kind() == MirrorFloraData.Kind.VINE) {
                int faces = entry.faces();
                for (Direction face : HORIZONTAL) {
                    if (entry.hasFace(face)
                            && (!selected(pos, face, band) || !supportsVine(level, pos, face))) {
                        faces &= ~(1 << face.ordinal());
                    }
                }
                if (faces == 0) {
                    changed |= flora.remove(pos);
                } else if (faces != entry.faces()) {
                    changed |= flora.put(new MirrorFloraData.Entry(
                            entry.packedPos(), entry.kind(), faces));
                }
                continue;
            }

            if (entry.kind() == MirrorFloraData.Kind.HANGING_ROOTS) {
                BlockState plant = ModBlocks.MIRROR_HANGING_ROOTS.get().defaultBlockState();
                if (!selected(pos, Direction.UP, band) || !supportsCeiling(level, pos.above(), plant)) {
                    changed |= flora.remove(pos);
                }
                continue;
            }

            BlockState plant = plantState(entry.kind());
            boolean valid = selected(pos, Direction.DOWN, band)
                    && supportsFloor(level, pos.below(), plant);
            if (entry.kind() == MirrorFloraData.Kind.TALL_GRASS) {
                valid &= level.getBlockState(pos.above()).isAir();
            }
            if (!valid) changed |= flora.remove(pos);
        }
        return changed;
    }

    public static boolean supportsFloor(BlockGetter level, BlockPos host, BlockState plant) {
        BlockState state = level.getBlockState(host);
        if (sterile(state)) return false;
        if (state.canSustainPlant(level, host, Direction.UP, plant).isFalse()) return false;
        return Block.isFaceFull(state.getBlockSupportShape(level, host), Direction.UP)
                || Block.isFaceFull(state.getCollisionShape(level, host), Direction.UP);
    }

    public static boolean supportsCeiling(BlockGetter level, BlockPos host, BlockState plant) {
        BlockState state = level.getBlockState(host);
        if (sterile(state)) return false;
        if (state.canSustainPlant(level, host, Direction.DOWN, plant).isFalse()) return false;
        return Block.isFaceFull(state.getBlockSupportShape(level, host), Direction.DOWN)
                || Block.isFaceFull(state.getCollisionShape(level, host), Direction.DOWN);
    }

    public static boolean supportsVine(BlockGetter level, BlockPos target, Direction face) {
        BlockPos host = target.relative(face);
        BlockState state = level.getBlockState(host);
        if (sterile(state)) return false;
        BlockState vine = ModBlocks.MIRROR_VINE.get().defaultBlockState();
        if (state.canSustainPlant(level, host, face.getOpposite(), vine).isFalse()) return false;
        return Block.isFaceFull(state.getBlockSupportShape(level, host), face.getOpposite())
                || Block.isFaceFull(state.getCollisionShape(level, host), face.getOpposite());
    }

    /**
     * Live machinery grows nothing.
     *
     * <p>Every seeding path and the prune pass reach the world through a support check, so refusing
     * formed multiblock parts here keeps flora out of a Containment Hall's cell — whose floor,
     * ceiling and four walls are all formed parts — and clears any that got in before this rule
     * existed. It has to be done at the source: flora are records rather than blocks, so the hall
     * cannot vaporise them the way it does an intruding block, and the spawner would deterministically
     * reseed the same face a second later anyway.
     *
     * <p>Any Quantimium block with a block entity counts too: a Projector's orb or a stabiliser's
     * emitter sprouting grass reads as the machine being overgrown rather than working.
     */
    private static boolean sterile(BlockState state) {
        if (state.hasProperty(QuantumFoundryStructure.FORMED) && state.getValue(QuantumFoundryStructure.FORMED)) {
            return true;
        }
        return state.hasBlockEntity()
                && Quantimium.MODID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace());
    }
}

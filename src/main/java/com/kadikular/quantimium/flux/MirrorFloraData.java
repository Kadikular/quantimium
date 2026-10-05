package com.kadikular.quantimium.flux;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent mirror flora for one chunk. These are render records, not block states, so the
 * backing world remains genuine air for redstone, observers, pathfinding, and other mods.
 */
public final class MirrorFloraData {

    public enum Kind {
        VINE,
        SHORT_GRASS,
        TALL_GRASS,
        DEAD_BUSH,
        HANGING_ROOTS;

        private static final Codec<Kind> CODEC = Codec.STRING.xmap(
                name -> Kind.valueOf(name.toUpperCase(java.util.Locale.ROOT)),
                kind -> kind.name().toLowerCase(java.util.Locale.ROOT));
    }

    public record Entry(long packedPos, Kind kind, int faces) {
        private static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("pos").forGetter(Entry::packedPos),
                Kind.CODEC.fieldOf("kind").forGetter(Entry::kind),
                Codec.INT.optionalFieldOf("faces", 0).forGetter(Entry::faces)
        ).apply(instance, Entry::new));

        public static Entry floor(BlockPos pos, Kind kind) {
            return new Entry(pos.asLong(), kind, 0);
        }

        public static Entry vine(BlockPos pos, Direction supportFace) {
            return new Entry(pos.asLong(), Kind.VINE, faceBit(supportFace));
        }

        public BlockPos pos() {
            return BlockPos.of(packedPos);
        }

        public boolean hasFace(Direction face) {
            return (faces & faceBit(face)) != 0;
        }

        public Entry withFace(Direction face) {
            return new Entry(packedPos, Kind.VINE, faces | faceBit(face));
        }

        private static int faceBit(Direction face) {
            return 1 << face.ordinal();
        }
    }

    public static final Codec<MirrorFloraData> CODEC = Entry.CODEC.listOf()
            .xmap(MirrorFloraData::new, data -> List.copyOf(data.entries.values()));

    private final Map<Long, Entry> entries = new LinkedHashMap<>();

    public MirrorFloraData() {}

    private MirrorFloraData(List<Entry> entries) {
        for (Entry entry : entries) this.entries.put(entry.packedPos(), entry);
    }

    public Collection<Entry> entries() {
        return List.copyOf(entries.values());
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public Entry get(BlockPos pos) {
        return entries.get(pos.asLong());
    }

    public boolean put(Entry entry) {
        Entry previous = entries.put(entry.packedPos(), entry);
        return !entry.equals(previous);
    }

    public boolean addVineFace(BlockPos pos, Direction face) {
        Entry current = get(pos);
        if (current == null) return put(Entry.vine(pos, face));
        if (current.kind() != Kind.VINE || current.hasFace(face)) return false;
        return put(current.withFace(face));
    }

    public boolean remove(BlockPos pos) {
        return entries.remove(pos.asLong()) != null;
    }

    public void replace(Collection<Entry> next) {
        entries.clear();
        for (Entry entry : next) entries.put(entry.packedPos(), entry);
    }

    public List<Entry> mutableSnapshot() {
        return new ArrayList<>(entries.values());
    }
}

package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.item.LensItem;
import com.kadikular.quantimium.laser.Channels;
import com.kadikular.quantimium.laser.LensUse;
import com.kadikular.quantimium.laser.RelayMode;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The emitters on a laser block's faces (a Laser Focus, a Laser Relay, an Isolator): each one's lens, the channel that
 * lens works on, how each listens to redstone, and so whether each lets light out now. The block says which faces have
 * an emitter, and what follows a change (see {@link EmitterHolder}).
 */
public final class EmitterFaces {

    /** The lens in each emitter that has one. */
    private final Map<Direction, ItemStack> lenses = new EnumMap<>(Direction.class);
    /** How each emitter that's been set listens to redstone; the rest follow {@link #mode}. */
    private final Map<Direction, RelayMode> modes = new EnumMap<>(Direction.class);
    /** Each emitter's lens's channel; 0 unless set. */
    private final Map<Direction, Integer> channels = new EnumMap<>(Direction.class);
    /** For emitters on a pulse: since when each is letting light through. */
    private final Map<Direction, Long> pulses = new EnumMap<>(Direction.class);
    /** How its emitters listen to redstone, but for those set one by one. */
    private RelayMode mode = RelayMode.IGNORED;
    /** Whether a redstone signal reaches it, as last told. */
    private boolean powered;

    // ---- lenses and channels ----

    /** The lens in the emitter on {@code face}: empty for none. */
    public ItemStack lens(Direction face) {
        return lenses.getOrDefault(face, ItemStack.EMPTY);
    }

    public void setLens(Direction face, ItemStack stack) {
        if (stack.isEmpty()) lenses.remove(face);
        else lenses.put(face, stack);
    }

    /** Every lens it holds. */
    public Collection<ItemStack> lenses() {
        return lenses.values();
    }

    /** Forgets its lenses without dropping them: they've gone with it, or it's broken in creative. */
    public void clearLenses() {
        lenses.clear();
    }

    public int channelOf(Direction face) {
        return channels.getOrDefault(face, 0);
    }

    public void setChannel(Direction face, int channel) {
        int to = Channels.of(channel);
        if (to == 0) channels.remove(face);
        else channels.put(face, to);
    }

    /** What light leaving by {@code face} goes through: its lens, on its channel. */
    public List<LensUse> lensUses(Direction face) {
        ItemStack stack = lens(face);
        if (!(stack.getItem() instanceof LensItem lens)) return List.of();
        return List.of(LensUse.of(stack, lens.lens()).onChannel(channelOf(face)));
    }

    /** The emitter on {@code face} is taken off: its own mode, channel and pulse go. Its lens, or empty for none. */
    public ItemStack remove(Direction face) {
        ItemStack lens = lenses.remove(face);
        modes.remove(face);
        channels.remove(face);
        pulses.remove(face);
        return lens == null ? ItemStack.EMPTY : lens;
    }

    // ---- redstone ----

    public RelayMode mode() {
        return mode;
    }

    /** Steps every emitter on to the next way of listening to redstone, together. */
    public void cycleMode() {
        mode = mode.next();
        modes.clear();
        pulses.clear();
    }

    public RelayMode modeOf(Direction face) {
        return modes.getOrDefault(face, mode);
    }

    /** Steps the emitter on {@code face} on to its next way of listening to redstone. */
    public void cycleMode(Direction face) {
        modes.put(face, modeOf(face).next());
        pulses.remove(face);
    }

    /** Whether any emitter listens to redstone. */
    public boolean switches(Iterable<Direction> emitters) {
        if (mode != RelayMode.IGNORED) return true;
        for (Direction face : emitters) if (modeOf(face) != RelayMode.IGNORED) return true;
        return false;
    }

    public boolean powered() {
        return powered;
    }

    /** Whether a signal reaches it, as found on loading: no pulse opens. */
    public void setPowered(boolean powered) {
        this.powered = powered;
    }

    /** Whether a signal reaches it now; one arriving opens a pulse on each of {@code emitters} waiting for one. */
    public void signal(boolean signal, @Nullable Level level, Iterable<Direction> emitters) {
        if (signal && !powered && level != null) {
            for (Direction face : emitters) if (modeOf(face) == RelayMode.PULSE) pulses.put(face, level.getGameTime());
        }
        powered = signal;
    }

    /** Whether the emitter on {@code face} lets light out now. */
    public boolean on(Direction face, @Nullable Level level) {
        return switch (modeOf(face)) {
            case IGNORED -> true;
            case WITH_SIGNAL -> powered;
            case WITHOUT_SIGNAL -> !powered;
            case PULSE -> {
                Long at = pulses.get(face);
                yield at != null && level != null && level.getGameTime() - at <= RelayMode.PULSE_TICKS;
            }
        };
    }

    public void pulseDone() {
        pulses.clear();
    }

    public void pulseDone(Direction face) {
        pulses.remove(face);
    }

    // ---- moving and saving ----

    /**
     * Takes on {@code from}'s emitters, those on {@code faces}: their lenses (copied), their channels and their own
     * redstone modes, with its shared mode. What a Laser Relay becoming a Laser Focus keeps.
     */
    public void copyFrom(EmitterFaces from, Set<Direction> faces) {
        lenses.clear();
        modes.clear();
        channels.clear();
        pulses.clear();
        mode = from.mode;
        for (Direction face : faces) {
            ItemStack lens = from.lens(face);
            if (!lens.isEmpty()) lenses.put(face, lens.copy());
            RelayMode own = from.modes.get(face);
            if (own != null) modes.put(face, own);
            int channel = from.channelOf(face);
            if (channel != 0) channels.put(face, channel);
        }
    }

    public void save(ValueOutput out) {
        out.putInt("Mode", mode.ordinal());
        for (Direction face : Direction.values()) {
            ItemStack lens = lens(face);
            if (!lens.isEmpty()) out.store("Lens_" + face.getSerializedName(), ItemStack.CODEC, lens);
            RelayMode own = modes.get(face);
            if (own != null) out.putInt("Mode_" + face.getSerializedName(), own.ordinal());
            if (channels.containsKey(face)) out.putInt("Channel_" + face.getSerializedName(), channels.get(face));
        }
    }

    public void load(ValueInput in) {
        mode = RelayMode.byOrdinal(in.getIntOr("Mode", 0));
        lenses.clear();
        modes.clear();
        channels.clear();
        for (Direction face : Direction.values()) {
            in.read("Lens_" + face.getSerializedName(), ItemStack.CODEC).ifPresent(stack -> lenses.put(face, stack));
            int own = in.getIntOr("Mode_" + face.getSerializedName(), -1);
            if (own >= 0) modes.put(face, RelayMode.byOrdinal(own));
            int channel = Channels.of(in.getIntOr("Channel_" + face.getSerializedName(), 0));
            if (channel != 0) channels.put(face, channel);
        }
    }
}

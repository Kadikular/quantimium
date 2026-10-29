package com.kadikular.quantimium.laser;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a Spectroscope reads off a laser: the laser leaving a block (or one of its beams), split into its lines. Sent to
 * the player's screen.
 *
 * @param where   the block it leaves, and the face for one beam
 * @param reach   how far a run of it reaches
 * @param sources how many sources it comes from
 * @param marks   {@link Light}'s marks: UV, Phantom, in the mirror, unpowered
 * @param boosts  Range lenses met
 * @param flow    the power it carries, FE/t
 * @param lines   one per stream: an action on a channel
 * @param ports   the Laser Ports, Tanks and Controllers its actions use, up the laser
 * @param logistics the channels of the Logistics Controllers whose laser it is
 */
public record SpectroscopeReading(Component where, int reach, int sources, int marks, int boosts, long flow,
                                  List<Line> lines, List<Port> ports, List<Integer> logistics) {

    /** One action on one channel: its modifiers (bits of {@link Lens}), Amplify and Speed lenses met, what it's doing now. */
    public record Line(int action, int channel, int modifiers, int amps, int speed, int ports, Optional<Component> said) {
        public Lens lens() {
            return Lens.values()[Math.clamp(action, 0, Lens.values().length - 1)];
        }

        public boolean has(Lens modifier) {
            return (modifiers & 1 << modifier.ordinal()) != 0;
        }
    }

    /** A port its actions use: its name, where, its channel, and whether it collects, sends, or both (1, 2, 3). */
    public record Port(Component name, BlockPos pos, int channel, int role) {}

    private static final StreamCodec<RegistryFriendlyByteBuf, Line> LINE = StreamCodec.of(
            (buf, line) -> {
                buf.writeVarInt(line.action);
                buf.writeVarInt(line.channel);
                buf.writeVarInt(line.modifiers);
                buf.writeVarInt(line.amps);
                buf.writeVarInt(line.speed);
                buf.writeVarInt(line.ports);
                ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC.encode(buf, line.said);
            },
            buf -> new Line(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC.decode(buf)));

    private static final StreamCodec<RegistryFriendlyByteBuf, Port> PORT = StreamCodec.of(
            (buf, port) -> {
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, port.name);
                buf.writeBlockPos(port.pos);
                buf.writeVarInt(port.channel);
                buf.writeVarInt(port.role);
            },
            buf -> new Port(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpectroscopeReading> STREAM_CODEC = StreamCodec.of(
            (buf, reading) -> {
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, reading.where);
                buf.writeVarInt(reading.reach);
                buf.writeVarInt(reading.sources);
                buf.writeVarInt(reading.marks);
                buf.writeVarInt(reading.boosts);
                buf.writeVarLong(reading.flow);
                LINE.apply(ByteBufCodecs.list(256)).encode(buf, reading.lines);
                PORT.apply(ByteBufCodecs.list(256)).encode(buf, reading.ports);
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)).encode(buf, reading.logistics);
            },
            buf -> new SpectroscopeReading(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarLong(),
                    new ArrayList<>(LINE.apply(ByteBufCodecs.list(256)).decode(buf)),
                    new ArrayList<>(PORT.apply(ByteBufCodecs.list(256)).decode(buf)),
                    new ArrayList<>(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)).decode(buf))));

    public boolean uv() {
        return (marks & Light.UV) != 0;
    }

    public boolean phantom() {
        return (marks & Light.PHANTOM) != 0;
    }

    public boolean mirrored() {
        return (marks & Light.MIRRORED) != 0;
    }

    public boolean unpowered() {
        return (marks & Light.UNPOWERED) != 0;
    }
}

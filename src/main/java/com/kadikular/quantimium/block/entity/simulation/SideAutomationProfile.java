package com.kadikular.quantimium.block.entity.simulation;

import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Declares which automation surfaces a machine actually supports.
 *
 * <p>This is enforced by the block entity as well as used by the screen. A hidden face therefore
 * cannot be re-enabled by an old client packet or stale saved configuration.
 */
public record SideAutomationProfile(int itemSides, int fluidSides) {

    public static final int ALL_SIDES = (1 << Direction.values().length) - 1;
    public static final SideAutomationProfile ITEMS_AND_FLUIDS =
            new SideAutomationProfile(ALL_SIDES, ALL_SIDES);

    public static final StreamCodec<FriendlyByteBuf, SideAutomationProfile> STREAM_CODEC =
            StreamCodec.of(
                    (buf, profile) -> {
                        buf.writeByte(profile.itemSides);
                        buf.writeByte(profile.fluidSides);
                    },
                    buf -> new SideAutomationProfile(
                            Byte.toUnsignedInt(buf.readByte()),
                            Byte.toUnsignedInt(buf.readByte())));

    public SideAutomationProfile {
        itemSides &= ALL_SIDES;
        fluidSides &= ALL_SIDES;
    }

    public static int sidesExcept(Direction... disabled) {
        int mask = ALL_SIDES;
        for (Direction side : disabled) mask &= ~(1 << side.get3DDataValue());
        return mask;
    }

    public boolean supportsItems(Direction side) {
        return (itemSides & (1 << side.get3DDataValue())) != 0;
    }

    public boolean supportsFluids(Direction side) {
        return (fluidSides & (1 << side.get3DDataValue())) != 0;
    }

    public boolean hasItems() {
        return itemSides != 0;
    }

    public boolean hasFluids() {
        return fluidSides != 0;
    }

    public List<SideConfig> sanitize(List<SideConfig> configs) {
        List<SideConfig> normalized = SideConfig.normalize(configs);
        List<SideConfig> result = new ArrayList<>(Direction.values().length);
        for (Direction side : Direction.values()) {
            SideConfig config = normalized.get(side.get3DDataValue());
            result.add(config.withMediaEnabled(supportsItems(side), supportsFluids(side)));
        }
        return result;
    }
}

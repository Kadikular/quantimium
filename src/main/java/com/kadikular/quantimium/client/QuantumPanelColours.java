// Path: src/main/java/com/kadikular/quantimium/client/QuantumPanelColours.java
package com.kadikular.quantimium.client;

import java.util.Set;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.state.properties.Property;
import com.kadikular.quantimium.block.QuantumSimulatorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Colours for the simulator's lit panels. The band around the chassis and the emitter on top carry
 * separate tint indices so the top can match the containment field while the sides stay white.
 */
public final class QuantumPanelColours {

    public static final int BAND_TINT = 0;
    public static final int EMITTER_TINT = 1;

    /** Unpowered panels, dark enough to pass for part of the chassis rather than a light left on. */
    private static final int DORMANT = 0xFF4B5054;
    private static final int RUNNING = 0xFFFFFFFF;
    /** The blue the tesseract shell is drawn in, so the emitter reads as the field's source. */
    private static final int FIELD = 0xFF4FB4FF;

    private QuantumPanelColours() {
    }

    public static int of(boolean active, int tintIndex) {
        if (!active) return DORMANT;
        return tintIndex == EMITTER_TINT ? FIELD : RUNNING;
    }

    public static int of(BlockState state, int tintIndex) {
        return of(state.hasProperty(QuantumSimulatorBlock.ACTIVE) && state.getValue(QuantumSimulatorBlock.ACTIVE), tintIndex);
    }

    /** Tint layer {@code tintIndex} of a placed simulator, which follows its {@code ACTIVE} state. */
    public static BlockTintSource source(int tintIndex) {
        return new BlockTintSource() {
            @Override
            public int color(BlockState state) {
                return of(state, tintIndex);
            }

            @Override
            public Set<Property<?>> relevantProperties() {
                return Set.of(QuantumSimulatorBlock.ACTIVE);
            }
        };
    }
}

package com.kadikular.quantimium.client.screen.ui;

import com.kadikular.quantimium.block.entity.simulation.SideMode;

/**
 * One palette for every Quantimium screen. The values are the ones the hand-rolled popups already
 * used, gathered here so a new panel matches the existing ones without copying literals around.
 */
public final class QuantumUiColours {

    /** Dim behind a modal so the container underneath reads as out of reach. */
    public static final int BACKDROP = 0xB0000000;
    public static final int PANEL = 0xF0140C1E;
    public static final int BORDER = 0xFF4A3F7A;
    public static final int ACCENT = 0xFF00E5FF;

    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_DIM = 0xFFAAAAAA;
    public static final int TEXT_MUTED = 0xFF888888;
    public static final int TEXT_WARN = 0xFFFF6060;
    public static final int TEXT_OK = 0xFF60FF80;

    public static final int TAB_ACTIVE = 0xFF305080;
    public static final int TAB_IDLE = 0xFF202030;

    public static final int ROW = 0x30FFFFFF;
    public static final int ROW_HOVER = 0x60FFFFFF;
    public static final int ROW_LOCKED = 0x50FF4040;

    public static final int CHIP_OFF = 0xFF402830;
    public static final int CHIP_IN = 0xFF30B060;
    public static final int CHIP_OUT = 0xFFB07030;

    public static final int ENERGY_FILL = 0xFFCC2222;
    public static final int SELECTION = 0xFFFFFFFF;

    private QuantumUiColours() {}

    /** Solid fill for a face tile, strong enough to read the mode at a glance. */
    public static int modeFill(SideMode mode) {
        return switch (mode) {
            case DISABLED -> 0xFF3A3040;
            case INPUT -> 0xFF2F7A48;
            case OUTPUT -> 0xFF8A5A28;
            case BOTH -> 0xFF3A5AA0;
        };
    }

    /** Translucent wash for a row or a background band behind other content. */
    public static int modeTint(SideMode mode) {
        return switch (mode) {
            case DISABLED -> 0x60403040;
            case INPUT -> 0x60408050;
            case OUTPUT -> 0x60805030;
            case BOTH -> 0x60405090;
        };
    }
}

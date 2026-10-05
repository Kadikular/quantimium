package com.kadikular.quantimium.client.screen.ui;

/**
 * The text colours that go with each panel palette in {@code tools/gui_panels.py}. A machine's panel
 * is painted in its tier's chassis colours (light stone, deepslate, obsidian), so text that reads on
 * one would vanish on another; screens ask their theme instead of picking colours themselves.
 */
public enum PanelTheme {
    /** Light stone: dark text. */
    LOW(0xFF22201D, 0xFF1E1C1A, 0xFF3C3A37, 0xFF14606A, 0xFF7A4A00, 0xFF9A1F1A),
    /** Deepslate. */
    MID(0xFF78B4FF, 0xFFDCDCE4, 0xFF9C9CA8, 0xFF78B4FF, 0xFFE8B050, 0xFFFF6A5E),
    /** Obsidian, as the Foundry panel. */
    HIGH(QuantumUiColours.ACCENT, 0xFFC8CDE0, 0xFF7A82A6, QuantumUiColours.ACCENT, 0xFFE8B050, 0xFFFF5A50);

    public final int title;
    public final int text;
    public final int dim;
    private final int ok;
    private final int warn;
    private final int bad;

    PanelTheme(int title, int text, int dim, int ok, int warn, int bad) {
        this.title = title;
        this.text = text;
        this.dim = dim;
        this.ok = ok;
        this.warn = warn;
        this.bad = bad;
    }

    /** How a status reads at a glance: working, waiting, needs attention, or stopped. */
    public enum Tone { OK, IDLE, WARN, BAD }

    public int tone(Tone tone) {
        return switch (tone) {
            case OK -> ok;
            case IDLE -> dim;
            case WARN -> warn;
            case BAD -> bad;
        };
    }
}

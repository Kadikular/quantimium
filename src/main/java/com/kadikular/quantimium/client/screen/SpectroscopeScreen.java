package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.laser.Channels;
import com.kadikular.quantimium.laser.Lens;
import com.kadikular.quantimium.laser.SpectroscopeReading;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * What a Spectroscope read off a laser. Above, its spectrum: a line for each action at that lens's place (ordered by its
 * colour, red to violet), as tall as its Amplify lenses make it and as wide as its Speed lenses, a notch for a Filter, a
 * dot for Pickup; logistics a band at the end, a tick for each port. Then the channels it carries, to look at one; and a
 * table of its actions and the ports they use.
 */
public class SpectroscopeScreen extends Screen {

    private static final int WIDTH = 280;
    private static final int HEIGHT = 216;
    private static final int SPECTRUM_X = 12;
    private static final int SPECTRUM_Y = 30;
    private static final int SPECTRUM_W = 256;
    private static final int SPECTRUM_H = 64;
    /** Where the band of logistics starts, after the actions' places. */
    private static final int BAND_W = 34;
    private static final int CHIPS_Y = SPECTRUM_Y + SPECTRUM_H + 8;
    private static final int TABLE_Y = CHIPS_Y + 16;
    private static final int ROW = 10;

    private static final int PANEL = 0xF0090B16;
    private static final int INNER = 0xFF05060D;
    private static final int EDGE = 0xFF23407A;
    private static final int TEXT = 0xFFDDE6FA;
    private static final int DIM = 0xFF8595B8;
    private static final int FAINT = 0xFF3A4668;
    private static final int LOGISTICS = 0xFF3F6FD8;

    /** The lenses that are lines of a spectrum, ordered by their colour's hue: what places them. */
    private static final List<Lens> PLACES = places();

    private final SpectroscopeReading reading;
    /** The channel looked at, or {@link Channels#ALL} for every one. */
    private int channel = Channels.ALL;
    private int scroll;
    private int left;
    private int top;

    public SpectroscopeScreen(SpectroscopeReading reading) {
        super(Component.translatable("item.quantimium.spectroscope"));
        this.reading = reading;
    }

    private static List<Lens> places() {
        List<Lens> lenses = new ArrayList<>();
        for (Lens lens : Lens.values()) {
            if (lens.action || lens == Lens.PICKUP || lens == Lens.SMELT || lens == Lens.CRUSH || lens == Lens.PHANTOM) lenses.add(lens);
        }
        lenses.sort(java.util.Comparator.comparingDouble(lens -> hue(lens.colour)));
        return lenses;
    }

    /** A colour's hue, 0 (red) to 1. */
    private static double hue(int colour) {
        double r = (colour >> 16 & 0xFF) / 255.0, g = (colour >> 8 & 0xFF) / 255.0, b = (colour & 0xFF) / 255.0;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), span = max - min;
        if (span == 0) return 0;
        double hue = max == r ? (g - b) / span : max == g ? 2 + (b - r) / span : 4 + (r - g) / span;
        return (hue / 6 + 1) % 1;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<SpectroscopeReading.Line> shown() {
        return reading.lines().stream().filter(line -> channel == Channels.ALL || Channels.serves(channel, line.channel())).toList();
    }

    private List<SpectroscopeReading.Port> shownPorts() {
        return reading.ports().stream().filter(port -> channel == Channels.ALL || Channels.serves(port.channel(), channel)).toList();
    }

    /** The channels it carries anything on: actions, and logistics. */
    private List<Integer> channels() {
        TreeSet<Integer> all = new TreeSet<>();
        for (var line : reading.lines()) all.add(line.channel());
        all.addAll(reading.logistics());
        List<Integer> list = new ArrayList<>();
        list.add(Channels.ALL);
        for (int each : all) if (each != Channels.ALL) list.add(each);
        return list;
    }

    // ---- drawing ----

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(graphics);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, PANEL);
        outline(graphics, left, top, WIDTH, HEIGHT, EDGE);
        graphics.text(font, title, left + 10, top + 8, TEXT, false);
        Component where = reading.where();
        graphics.text(font, where, left + WIDTH - 10 - font.width(where), top + 8, DIM, false);

        List<Component> tip = spectrum(graphics, mouseX, mouseY);
        List<Component> chips = chips(graphics, mouseX, mouseY);
        if (chips != null) tip = chips;
        List<Component> table = table(graphics, mouseX, mouseY);
        if (table != null) tip = table;
        footer(graphics);
        if (tip != null) graphics.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
    }

    private void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x, y, x + w, y + 1, colour);
        graphics.fill(x, y + h - 1, x + w, y + h, colour);
        graphics.fill(x, y, x + 1, y + h, colour);
        graphics.fill(x + w - 1, y, x + w, y + h, colour);
    }

    /** Where {@code lens}'s place is across the spectrum. */
    private int placeX(Lens lens) {
        int index = PLACES.indexOf(lens);
        int span = SPECTRUM_W - BAND_W - 16;
        return left + SPECTRUM_X + 8 + (index < 0 ? 0 : index * span / Math.max(1, PLACES.size() - 1));
    }

    /** The spectrum; what's under the mouse, as a tooltip. */
    @Nullable
    private List<Component> spectrum(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x0 = left + SPECTRUM_X, y0 = top + SPECTRUM_Y;
        graphics.fill(x0, y0, x0 + SPECTRUM_W, y0 + SPECTRUM_H, INNER);
        outline(graphics, x0, y0, SPECTRUM_W, SPECTRUM_H, FAINT);
        int base = y0 + SPECTRUM_H - 6;
        // The axis: each place a faint tick in its colour, so where an action would show is known.
        for (Lens lens : PLACES) graphics.fill(placeX(lens), base + 1, placeX(lens) + 1, base + 4, dim(lens.colour, 0x60));
        if (reading.lines().isEmpty()) {
            Component plain = Component.translatable("gui.quantimium.spectroscope.plain");
            graphics.text(font, plain, x0 + (SPECTRUM_W - BAND_W - font.width(plain)) / 2, y0 + SPECTRUM_H / 2 - 4, DIM, false);
        }
        List<Component> tip = null;
        // Lines of one action on several channels sit side by side, a channel's number over each.
        java.util.Map<Lens, Integer> seen = new java.util.EnumMap<>(Lens.class);
        for (SpectroscopeReading.Line line : shown()) {
            Lens lens = line.lens();
            int offset = seen.merge(lens, 1, Integer::sum) - 1;
            int x = placeX(lens) + offset * 4;
            int height = Math.min(SPECTRUM_H - 14, 22 + line.amps() * 9);
            int half = line.speed();
            int top = base - height;
            int colour = lens.colour | 0xFF000000;
            // Range lenses: a faint line beside it for each.
            for (int r = 1; r <= reading.boosts(); r++) graphics.fill(x + half + 1 + r * 2, top + 6, x + half + 2 + r * 2, base, dim(colour, 0x50));
            if (line.has(Lens.HARMONIC)) {
                graphics.fill(x - half - 3, top + 10, x - half - 2, base, dim(colour, 0x80));
                graphics.fill(x + half + 3, top + 10, x + half + 4, base, dim(colour, 0x80));
            }
            graphics.fill(x - half, top, x + half + 1, base, colour);
            // A glow at its foot, as a line in a real spectrum blooms.
            graphics.fill(x - half - 1, base - 3, x + half + 2, base, dim(colour, 0x70));
            if (line.has(Lens.VOID)) graphics.fill(x, top + 2, x + 1, base, 0xFF000000);
            if (line.has(Lens.FILTER)) graphics.fill(x - half, top + height / 3, x + half + 1, top + height / 3 + 2, INNER);
            if (line.has(Lens.PICKUP) && lens != Lens.PICKUP) graphics.fill(x - 1, top - 4, x + 2, top - 1, 0xFF5BE38A);
            if (channel == Channels.ALL && channels().size() > 2) {
                String label = Channels.label(line.channel()).getString();
                drawSmall(graphics, label, x + 1, top - (line.has(Lens.PICKUP) ? 10 : 6), DIM);
            }
            if (mouseX >= x - half - 2 && mouseX <= x + half + 2 && mouseY >= top - 4 && mouseY <= base) tip = lineTip(line);
        }
        // Logistics: a band at the end, a tick for each port its actions use.
        int bandX = x0 + SPECTRUM_W - BAND_W;
        graphics.fill(bandX, y0 + 1, bandX + 1, y0 + SPECTRUM_H - 1, FAINT);
        boolean logistics = !reading.logistics().isEmpty()
                && (channel == Channels.ALL || reading.logistics().stream().anyMatch(each -> Channels.serves(channel, each)));
        if (logistics) graphics.fill(bandX + 4, y0 + 12, bandX + BAND_W - 4, base, dim(LOGISTICS, 0x90));
        List<SpectroscopeReading.Port> ports = shownPorts();
        for (int i = 0; i < ports.size() && i < 12; i++) {
            int px = bandX + 5 + i * 2;
            graphics.fill(px, y0 + 6, px + 1, y0 + 11, 0xFFE4DCFF);
        }
        if (mouseX >= bandX && mouseX < x0 + SPECTRUM_W && mouseY >= y0 && mouseY < y0 + SPECTRUM_H) {
            List<Component> band = new ArrayList<>();
            band.add(Component.translatable(logistics ? "gui.quantimium.spectroscope.logistics" : "gui.quantimium.spectroscope.no_logistics"));
            band.add(Component.translatable("gui.quantimium.spectroscope.ports", ports.size()).withStyle(ChatFormatting.GRAY));
            tip = band;
        }
        return tip;
    }

    private List<Component> lineTip(SpectroscopeReading.Line line) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable(line.lens().itemKey()).withColor(line.lens().colour & 0xFFFFFF));
        tip.add(Component.translatable("gui.quantimium.channel", Channels.label(line.channel())).withStyle(ChatFormatting.GRAY));
        MutableComponent modifiers = modifiers(line);
        if (!modifiers.getString().isEmpty()) tip.add(modifiers.withStyle(ChatFormatting.GRAY));
        if (line.ports() > 0) tip.add(Component.translatable("gui.quantimium.spectroscope.ports", line.ports()).withStyle(ChatFormatting.GRAY));
        line.said().ifPresent(said -> tip.add(said.copy().withStyle(ChatFormatting.DARK_GRAY)));
        return tip;
    }

    /** A line's modifiers, by name: "Pickup, Amplify ×2, Speed". */
    private static MutableComponent modifiers(SpectroscopeReading.Line line) {
        MutableComponent text = Component.empty();
        boolean first = true;
        for (Lens lens : Lens.values()) {
            if (lens.action || lens == line.lens() || !line.has(lens)) continue;
            if (!first) text.append(", ");
            first = false;
            text.append(Component.translatable(lens.itemKey()));
            int times = lens == Lens.AMPLIFY ? line.amps() : lens == Lens.SPEED ? line.speed() : 1;
            if (times > 1) text.append(" ×" + times);
        }
        return text;
    }

    /** A number in half-size text, centred on {@code x}. */
    private void drawSmall(GuiGraphicsExtractor graphics, String text, int x, int y, int colour) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(0.5f, 0.5f);
        graphics.text(font, text, -font.width(text) / 2, 0, colour, false);
        graphics.pose().popMatrix();
    }

    private static int dim(int colour, int alpha) {
        return alpha << 24 | colour & 0xFFFFFF;
    }

    // ---- channels ----

    private int chipX(int index) {
        return left + SPECTRUM_X + index * 20;
    }

    @Nullable
    private List<Component> chips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<Integer> channels = channels();
        graphics.text(font, Component.translatable("gui.quantimium.spectroscope.channels"), left + SPECTRUM_X, top + CHIPS_Y - 1, DIM, false);
        int labelW = font.width(Component.translatable("gui.quantimium.spectroscope.channels")) + 6;
        List<Component> tip = null;
        for (int i = 0; i < channels.size(); i++) {
            int x = chipX(i) + labelW;
            int y = top + CHIPS_Y - 2;
            boolean on = channels.get(i) == channel;
            boolean over = mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 11;
            graphics.fill(x, y, x + 18, y + 11, on ? 0xFF2A3F78 : over ? 0xFF1A2448 : 0xFF0E1428);
            outline(graphics, x, y, 18, 11, on ? 0xFF7FB2FF : FAINT);
            String label = Channels.label(channels.get(i)).getString();
            graphics.text(font, label, x + 9 - font.width(label) / 2, y + 2, on ? TEXT : DIM, false);
            if (over) tip = List.of(Component.translatable(channels.get(i) == Channels.ALL ? "gui.quantimium.spectroscope.all"
                    : "gui.quantimium.spectroscope.only", Channels.label(channels.get(i))));
        }
        return tip;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        List<Integer> channels = channels();
        int labelW = font.width(Component.translatable("gui.quantimium.spectroscope.channels")) + 6;
        for (int i = 0; i < channels.size(); i++) {
            int x = chipX(i) + labelW;
            int y = top + CHIPS_Y - 2;
            if (event.x() >= x && event.x() < x + 18 && event.y() >= y && event.y() < y + 11) {
                channel = channels.get(i);
                scroll = 0;
                if (minecraft != null) minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
        return true;
    }

    // ---- the table ----

    private int tableRows() {
        return (HEIGHT - TABLE_Y - 22) / ROW;
    }

    /** One row of the table: its swatch colour (0 for none), its text, and its tooltip. */
    private record Row(int colour, Component text, Component channel, @Nullable List<Component> tip) {}

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        for (SpectroscopeReading.Line line : shown()) {
            MutableComponent text = Component.translatable(line.lens().itemKey());
            MutableComponent modifiers = modifiers(line);
            if (!modifiers.getString().isEmpty()) text.append(Component.literal(" + ").withStyle(ChatFormatting.DARK_GRAY)).append(modifiers.withStyle(ChatFormatting.GRAY));
            rows.add(new Row(line.lens().colour, text, Channels.label(line.channel()), lineTip(line)));
        }
        for (SpectroscopeReading.Port port : shownPorts()) {
            String role = switch (port.role()) {
                case 1 -> "collects";
                case 2 -> "sends";
                default -> "both";
            };
            MutableComponent text = port.name().copy().append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("gui.quantimium.spectroscope.port." + role).withStyle(ChatFormatting.GRAY));
            rows.add(new Row(0, text, Channels.label(port.channel()), List.of(port.name(),
                    Component.literal(port.pos().toShortString()).withStyle(ChatFormatting.DARK_GRAY))));
        }
        return rows;
    }

    @Nullable
    private List<Component> table(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = left + SPECTRUM_X, y = top + TABLE_Y;
        graphics.text(font, Component.translatable("gui.quantimium.spectroscope.what"), x + 8, y, DIM, false);
        Component ch = Component.translatable("gui.quantimium.spectroscope.channel");
        graphics.text(font, ch, x + SPECTRUM_W - font.width(ch), y, DIM, false);
        graphics.fill(x, y + 9, x + SPECTRUM_W, y + 10, FAINT);
        List<Row> rows = rows();
        scroll = Math.min(scroll, Math.max(0, rows.size() - tableRows()));
        List<Component> tip = null;
        for (int i = 0; i < tableRows() && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int ry = y + 12 + i * ROW;
            if (row.colour() != 0) graphics.fill(x, ry + 1, x + 5, ry + 7, row.colour() | 0xFF000000);
            else graphics.fill(x + 1, ry + 3, x + 4, ry + 5, 0xFFE4DCFF);
            graphics.text(font, font.substrByWidth(row.text(), SPECTRUM_W - 40).getString(), x + 8, ry, TEXT, false);
            String label = row.channel().getString();
            graphics.text(font, label, x + SPECTRUM_W - font.width(label), ry, TEXT, false);
            if (mouseX >= x && mouseX < x + SPECTRUM_W && mouseY >= ry - 1 && mouseY < ry + ROW - 1) tip = row.tip();
        }
        if (rows.isEmpty()) graphics.text(font, Component.translatable("gui.quantimium.spectroscope.nothing"), x + 8, y + 12, DIM, false);
        if (rows.size() > tableRows()) {
            String more = (scroll + 1) + "–" + Math.min(rows.size(), scroll + tableRows()) + " / " + rows.size();
            drawSmall(graphics, more, x + SPECTRUM_W / 2, y + 12 + tableRows() * ROW, DIM);
        }
        return tip;
    }

    /** Reach, sources, power, and what the laser is besides. */
    private void footer(GuiGraphicsExtractor graphics) {
        MutableComponent line = Component.translatable("gui.quantimium.spectroscope.footer", reading.reach(), reading.sources(),
                String.format(Locale.ROOT, "%,d", reading.flow()));
        if (reading.uv()) line.append(" · ").append(Component.translatable("item.quantimium.uv_lens"));
        if (reading.phantom()) line.append(" · ").append(Component.translatable("item.quantimium.phantom_lens"));
        if (reading.mirrored()) line.append(" · ").append(Component.translatable("gui.quantimium.spectroscope.mirrored"));
        if (reading.unpowered()) line.append(" · ").append(Component.translatable("gui.quantimium.spectroscope.unpowered"));
        graphics.fill(left + 8, top + HEIGHT - 16, left + WIDTH - 8, top + HEIGHT - 15, FAINT);
        graphics.text(font, font.substrByWidth(line, WIDTH - 20).getString(), left + 10, top + HEIGHT - 12, DIM, false);
    }
}

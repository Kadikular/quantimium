package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.laser.Channels;
import com.kadikular.quantimium.laser.Lens;
import com.kadikular.quantimium.laser.SpectroscopeReading;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * What a Spectroscope read off a laser. Above, its spectrum by wavelength, shortest on the left: a line for each lens
 * the laser carries at its {@link Lens#wavelength()}, and the bands of power and logistics, each over its range in the
 * colours of its wavelengths. Then the channels it carries, to look at one; and below, what's going on: each action with
 * the lenses changing it, and each port with the actions using it, as icons.
 */
public class SpectroscopeScreen extends Screen {

    private static final int WIDTH = 280;
    private static final int HEIGHT = 236;
    private static final int SPECTRUM_X = 12;
    private static final int SPECTRUM_Y = 26;
    private static final int SPECTRUM_W = 256;
    private static final int SPECTRUM_H = 64;
    /** The wavelengths across the spectrum, nm. */
    private static final int NM_FROM = 340;
    private static final int NM_TO = 870;
    /** The bands: power (the plain laser's azure) and logistics (its deeper blue). */
    private static final int POWER_FROM = 478, POWER_TO = 512;
    private static final int LOGISTICS_FROM = 428, LOGISTICS_TO = 462;
    private static final int CHIPS_Y = SPECTRUM_Y + SPECTRUM_H + 14;
    private static final int TABLE_Y = CHIPS_Y + 16;
    private static final int ROW = 18;

    private static final int PANEL = 0xF0090B16;
    private static final int INNER = 0xFF05060D;
    private static final int EDGE = 0xFF23407A;
    private static final int TEXT = 0xFFDDE6FA;
    private static final int DIM = 0xFF8595B8;
    private static final int FAINT = 0xFF3A4668;

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

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- what's shown ----

    private boolean onChannel(int carried) {
        return channel == Channels.ALL || Channels.serves(channel, carried);
    }

    private List<SpectroscopeReading.Line> shown() {
        return reading.lines().stream().filter(line -> onChannel(line.channel())).toList();
    }

    private List<SpectroscopeReading.Port> shownPorts() {
        return reading.ports().stream().filter(port -> onChannel(port.channel())).toList();
    }

    /** Every lens the laser carries on the channel looked at, and the channels each is on. */
    private Map<Lens, TreeSet<Integer>> lenses() {
        Map<Lens, TreeSet<Integer>> lenses = new EnumMap<>(Lens.class);
        for (SpectroscopeReading.Line line : shown()) {
            lenses.computeIfAbsent(line.lens(), k -> new TreeSet<>()).add(line.channel());
            for (Lens lens : Lens.values()) {
                if (lens != line.lens() && line.has(lens)) lenses.computeIfAbsent(lens, k -> new TreeSet<>()).add(line.channel());
            }
        }
        // What changes the laser itself, on every channel.
        if (reading.boosts() > 0) lenses.computeIfAbsent(Lens.RANGE, k -> new TreeSet<>()).add(Channels.ALL);
        if (reading.uv()) lenses.computeIfAbsent(Lens.UV, k -> new TreeSet<>()).add(Channels.ALL);
        if (reading.phantom()) lenses.computeIfAbsent(Lens.PHANTOM, k -> new TreeSet<>()).add(Channels.ALL);
        return lenses;
    }

    private boolean logistics() {
        return reading.logistics().stream().anyMatch(this::onChannel);
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

    /** Where {@code nm} is across the spectrum. */
    private int nmX(double nm) {
        return left + SPECTRUM_X + 2 + (int) Math.round((nm - NM_FROM) * (SPECTRUM_W - 4) / (double) (NM_TO - NM_FROM));
    }

    /** The wavelength at column {@code x}. */
    private double xNm(int x) {
        return NM_FROM + (x - left - SPECTRUM_X - 2) * (NM_TO - NM_FROM) / (double) (SPECTRUM_W - 4);
    }

    /**
     * The colour light of {@code nm} looks: the visible range as a rainbow, fading to a dim violet into the ultraviolet
     * and a dim red into the infrared.
     */
    static int nmColour(double nm) {
        double r, g, b;
        if (nm < 380) {
            r = 0.35; g = 0.0; b = 0.55;
        } else if (nm < 440) {
            r = -(nm - 440) / 60; g = 0; b = 1;
        } else if (nm < 490) {
            r = 0; g = (nm - 440) / 50; b = 1;
        } else if (nm < 510) {
            r = 0; g = 1; b = -(nm - 510) / 20;
        } else if (nm < 580) {
            r = (nm - 510) / 70; g = 1; b = 0;
        } else if (nm < 645) {
            r = 1; g = -(nm - 645) / 65; b = 0;
        } else {
            r = 1; g = 0; b = 0;
        }
        // Dimmer at the ends, as an eye sees them.
        double fall = nm < 420 ? 0.3 + 0.7 * Math.max(0, nm - 380) / 40 : nm > 700 ? 0.3 + 0.7 * Math.max(0, 780 - nm) / 80 : 1.0;
        if (nm < 380 || nm > 780) fall = 0.3;
        return 0xFF000000 | (int) (r * fall * 255) << 16 | (int) (g * fall * 255) << 8 | (int) (b * fall * 255);
    }

    /** The spectrum; what's under the mouse, as a tooltip. */
    @Nullable
    private List<Component> spectrum(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x0 = left + SPECTRUM_X, y0 = top + SPECTRUM_Y;
        graphics.fill(x0, y0, x0 + SPECTRUM_W, y0 + SPECTRUM_H, INNER);
        outline(graphics, x0, y0, SPECTRUM_W, SPECTRUM_H, FAINT);
        int base = y0 + SPECTRUM_H - 5;
        // The axis: the wavelengths' own colours, dim, along the foot.
        for (int x = x0 + 2; x < x0 + SPECTRUM_W - 2; x++) graphics.fill(x, base + 1, x + 1, base + 4, dim(nmColour(xNm(x)), 0x70));
        for (int nm = 400; nm <= 800; nm += 100) {
            int x = nmX(nm);
            graphics.fill(x, base - 2, x + 1, base + 1, FAINT);
            drawSmall(graphics, Integer.toString(nm), x, y0 + SPECTRUM_H + 2, DIM);
        }
        drawSmall(graphics, "UV", nmX(358), y0 + SPECTRUM_H + 2, DIM);
        drawSmall(graphics, "IR", nmX(842), y0 + SPECTRUM_H + 2, DIM);
        List<Component> tip = null;

        // Bands: power, while it carries any; logistics, while it's a network's. Low, in their wavelengths' colours.
        if (!reading.unpowered() && reading.flow() >= 0) tip = band(graphics, POWER_FROM, POWER_TO, base, 16, "gui.quantimium.spectroscope.power", mouseX, mouseY, tip);
        if (logistics()) tip = band(graphics, LOGISTICS_FROM, LOGISTICS_TO, base, 24, "gui.quantimium.spectroscope.logistics", mouseX, mouseY, tip);

        // A line for each lens it carries, at its wavelength.
        Map<Lens, TreeSet<Integer>> lenses = lenses();
        if (lenses.isEmpty() && !logistics()) {
            Component plain = Component.translatable("gui.quantimium.spectroscope.plain");
            graphics.text(font, plain, x0 + (SPECTRUM_W - font.width(plain)) / 2, y0 + 10, DIM, false);
        }
        for (Map.Entry<Lens, TreeSet<Integer>> entry : lenses.entrySet()) {
            Lens lens = entry.getKey();
            int x = nmX(lens.wavelength());
            int colour = lens.colour | 0xFF000000;
            int height = lens.action ? SPECTRUM_H - 14 : SPECTRUM_H - 26;
            graphics.fill(x - 1, base - height, x + 2, base, dim(colour, 0x50));
            graphics.fill(x, base - height, x + 1, base, colour);
            if (mouseX >= x - 2 && mouseX <= x + 3 && mouseY >= base - height && mouseY <= base) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable(lens.itemKey()).withColor(lens.colour & 0xFFFFFF));
                lines.add(Component.translatable("gui.quantimium.spectroscope.nm", lens.wavelength()).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.quantimium.spectroscope.on", channelList(entry.getValue())).withStyle(ChatFormatting.GRAY));
                tip = lines;
            }
        }
        return tip;
    }

    /** A band over {@code from}-{@code to} nm, {@code height} high; its tooltip if the mouse is over it. */
    @Nullable
    private List<Component> band(GuiGraphicsExtractor graphics, int from, int to, int base, int height, String key, int mouseX, int mouseY,
                                 @Nullable List<Component> tip) {
        int x1 = nmX(from), x2 = nmX(to);
        for (int x = x1; x <= x2; x++) {
            // Softer towards its edges, as a band of light is.
            double edge = Math.min(x - x1, x2 - x) / (double) Math.max(1, (x2 - x1) / 2);
            int h = (int) Math.round(height * (0.45 + 0.55 * Math.sqrt(Math.max(0, edge))));
            graphics.fill(x, base - h, x + 1, base, dim(nmColour(xNm(x)), 0xA0));
        }
        if (mouseX >= x1 && mouseX <= x2 && mouseY >= base - height && mouseY <= base) {
            return List.of(Component.translatable(key), Component.translatable("gui.quantimium.spectroscope.nm_range", from, to).withStyle(ChatFormatting.GRAY));
        }
        return tip;
    }

    private static Component channelList(TreeSet<Integer> channels) {
        StringBuilder text = new StringBuilder();
        for (int each : channels) {
            if (!text.isEmpty()) text.append(", ");
            text.append(Channels.label(each).getString());
        }
        return Component.literal(text.toString());
    }

    /** Text at half size, centred on {@code x}. */
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
        return left + SPECTRUM_X + font.width(Component.translatable("gui.quantimium.spectroscope.channels")) + 6 + index * 20;
    }

    @Nullable
    private List<Component> chips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<Integer> channels = channels();
        graphics.text(font, Component.translatable("gui.quantimium.spectroscope.channels"), left + SPECTRUM_X, top + CHIPS_Y, DIM, false);
        List<Component> tip = null;
        for (int i = 0; i < channels.size(); i++) {
            int x = chipX(i);
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
        for (int i = 0; i < channels.size(); i++) {
            int x = chipX(i);
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

    // ---- what's going on: the table ----

    private int tableRows() {
        return (HEIGHT - TABLE_Y - 20) / ROW;
    }

    /** An icon in a row: the item drawn, how many (Amplify ×2), and its name on hover. */
    private record Icon(ItemStack stack, int times, List<Component> tip) {}

    /** One row: what it's about (an action lens, a port), what changes or uses it, its channel. */
    private record Row(Icon main, List<Icon> by, int channel) {}

    private static Icon lensIcon(Lens lens, int times, @Nullable Component more) {
        ItemStack stack = lensStack(lens);
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable(lens.itemKey()).withColor(lens.colour & 0xFFFFFF));
        if (more != null) tip.add(more.copy().withStyle(ChatFormatting.DARK_GRAY));
        return new Icon(stack, times, tip);
    }

    /** The item of {@code lens}: its icon. */
    private static ItemStack lensStack(Lens lens) {
        String key = lens.itemKey();
        var id = net.minecraft.resources.Identifier.tryParse(key.replaceFirst("^item\\.", "").replaceFirst("\\.", ":"));
        var item = id == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        for (SpectroscopeReading.Line line : shown()) {
            List<Icon> by = new ArrayList<>();
            for (Lens lens : Lens.values()) {
                if (lens.action || lens == line.lens() || !line.has(lens)) continue;
                by.add(lensIcon(lens, lens == Lens.AMPLIFY ? line.amps() : lens == Lens.SPEED ? line.speed() : 1, null));
            }
            rows.add(new Row(lensIcon(line.lens(), 1, line.said().orElse(null)), by, line.channel()));
        }
        for (SpectroscopeReading.Port port : shownPorts()) {
            List<Component> tip = new ArrayList<>();
            tip.add(port.icon().getHoverName());
            tip.add(Component.translatable("gui.quantimium.spectroscope.port." + switch (port.role()) {
                case 1 -> "collects";
                case 2 -> "sends";
                default -> "both";
            }).withStyle(ChatFormatting.GRAY));
            tip.add(Component.literal(port.pos().toShortString()).withStyle(ChatFormatting.DARK_GRAY));
            List<Icon> by = new ArrayList<>();
            for (int action : port.actions()) by.add(lensIcon(Lens.values()[Math.clamp(action, 0, Lens.values().length - 1)], 1, null));
            rows.add(new Row(new Icon(port.icon(), 1, tip), by, port.channel()));
        }
        return rows;
    }

    @Nullable
    private List<Component> table(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = left + SPECTRUM_X, y = top + TABLE_Y;
        graphics.fill(x, y - 2, x + SPECTRUM_W, y - 1, FAINT);
        List<Row> rows = rows();
        scroll = Math.min(scroll, Math.max(0, rows.size() - tableRows()));
        List<Component> tip = null;
        for (int i = 0; i < tableRows() && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int ry = y + 1 + i * ROW;
            tip = icon(graphics, row.main(), x, ry, mouseX, mouseY, tip);
            // A gap, then what changes it (or, for a port, the actions using it).
            graphics.fill(x + 21, ry + 3, x + 22, ry + 13, FAINT);
            for (int j = 0; j < row.by().size(); j++) tip = icon(graphics, row.by().get(j), x + 26 + j * 18, ry, mouseX, mouseY, tip);
            String label = Channels.label(row.channel()).getString();
            graphics.text(font, label, x + SPECTRUM_W - font.width(label) - 2, ry + 4, TEXT, false);
            if (mouseX >= x + SPECTRUM_W - 22 && mouseX < x + SPECTRUM_W && mouseY >= ry && mouseY < ry + 16) {
                tip = List.of(Component.translatable("gui.quantimium.channel", Channels.label(row.channel())));
            }
        }
        if (rows.isEmpty()) graphics.text(font, Component.translatable("gui.quantimium.spectroscope.nothing"), x + 4, y + 5, DIM, false);
        if (rows.size() > tableRows()) {
            String more = (scroll + 1) + "–" + Math.min(rows.size(), scroll + tableRows()) + " / " + rows.size();
            drawSmall(graphics, more, x + SPECTRUM_W / 2, y + 2 + tableRows() * ROW, DIM);
        }
        return tip;
    }

    @Nullable
    private List<Component> icon(GuiGraphicsExtractor graphics, Icon icon, int x, int y, int mouseX, int mouseY, @Nullable List<Component> tip) {
        if (!icon.stack().isEmpty()) graphics.item(icon.stack(), x, y);
        if (icon.times() > 1) {
            graphics.nextStratum();
            graphics.text(font, "×" + icon.times(), x + 17 - font.width("×" + icon.times()), y + 9, TEXT, true);
        }
        return mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16 ? icon.tip() : tip;
    }

    /** Reach, sources, power, and what the laser is besides. */
    private void footer(GuiGraphicsExtractor graphics) {
        var line = Component.translatable("gui.quantimium.spectroscope.footer", reading.reach(), reading.sources(),
                String.format(Locale.ROOT, "%,d", reading.flow()));
        if (reading.mirrored()) line.append(" · ").append(Component.translatable("gui.quantimium.spectroscope.mirrored"));
        if (reading.unpowered()) line.append(" · ").append(Component.translatable("gui.quantimium.spectroscope.unpowered"));
        graphics.fill(left + 8, top + HEIGHT - 16, left + WIDTH - 8, top + HEIGHT - 15, FAINT);
        graphics.text(font, font.substrByWidth(line, WIDTH - 20).getString(), left + 10, top + HEIGHT - 12, DIM, false);
    }
}

package com.kadikular.quantimium.client.screen;

import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import com.kadikular.quantimium.block.entity.SuperpositionPodBlockEntity;
import com.kadikular.quantimium.network.OpenPodScreenPayload;
import com.kadikular.quantimium.network.OpenStashPayload;
import com.kadikular.quantimium.network.PodActionPayload;
import com.kadikular.quantimium.network.RenamePodPayload;
import com.kadikular.quantimium.superposition.PodModule;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A Superposition Pod's screen, drawn as a hologram rather than a stone panel: on the left a radar
 * with this pod at its centre and your doubles round it by bearing and distance (north up, distance
 * on a log scale, other dimensions out on the rim); on the right the same doubles as a list, with what
 * a swap to each costs. Pick one and swap, standing inside; the power bar shows what the swap would
 * take out of the buffer.
 */
public class PodScreen extends Screen {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 200;
    private static final int RADAR_RADIUS = 58;
    private static final int ROW_HEIGHT = 24;
    private static final int VISIBLE_ROWS = 5;

    private static final int PANEL = 0xE6070A16;
    private static final int PANEL_INNER = 0xC00B1024;
    private static final int LINE = 0xFF23407A;
    private static final int ACCENT = 0xFF3485FF;
    private static final int BRIGHT = 0xFF7FB2FF;
    private static final int WHITE = 0xFFD6E6FF;
    private static final int TEXT = 0xFFDDE6FA;
    private static final int DIM = 0xFF8595B8;
    private static final int FAINT = 0xFF4A5678;
    private static final int VIOLET = 0xFFB196FF;
    private static final int WARN = 0xFFFF8A7A;
    private static final int GOOD = 0xFF7FE0B8;
    /** A field double: out in the open, unguarded. */
    private static final int FIELD = 0xFFF0B35A;

    private OpenPodScreenPayload view;
    @Nullable
    private UUID selected;
    private int scroll;
    private int left;
    private int top;
    private final long opened = Util.getMillis();

    public PodScreen(OpenPodScreenPayload view) {
        super(Component.literal(view.name()));
        this.view = view;
        selectDefault();
    }

    public BlockPos pos() {
        return view.pos();
    }

    /** Fresh numbers from the server, keeping the selection if it still exists. */
    public void update(OpenPodScreenPayload fresh) {
        view = fresh;
        if (selected == null || find(selected) == null) selectDefault();
    }

    private void selectDefault() {
        selected = null;
        for (OpenPodScreenPayload.Destination destination : view.destinations()) {
            if (!destination.has(OpenPodScreenPayload.FLAG_FOLDED)) {
                selected = destination.id();
                return;
            }
        }
    }

    @Nullable
    private OpenPodScreenPayload.Destination find(UUID id) {
        for (OpenPodScreenPayload.Destination destination : view.destinations()) {
            if (destination.id().equals(id)) return destination;
        }
        return null;
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

    // ---- layout ----

    private int radarX() {
        return left + 12 + RADAR_RADIUS;
    }

    private int radarY() {
        return top + 34 + RADAR_RADIUS;
    }

    private int listX() {
        return left + 142;
    }

    private int listY() {
        return top + 32;
    }

    private int listWidth() {
        return WIDTH - 142 - 10;
    }

    private int barY() {
        return top + HEIGHT - 46;
    }

    private int swapButtonX() {
        return left + WIDTH - 10 - 70;
    }

    private int buttonY() {
        return top + HEIGHT - 26;
    }

    private int anchorButtonX() {
        return left + WIDTH - 10 - 70 - 8 - 84;
    }

    // ---- drawing ----

    private int lastMouseX;
    private int lastMouseY;

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        float time = (Util.getMillis() - opened) / 50.0f;
        float appear = Mth.clamp(time / 6.0f, 0.0f, 1.0f);
        frame(graphics, time, appear);
        header(graphics);
        radar(graphics, time, mouseX, mouseY);
        list(graphics, mouseX, mouseY);
        power(graphics);
        buttons(graphics, mouseX, mouseY);
        status(graphics);
        sideTabs(graphics, mouseX, mouseY);
        if (nameBox != null) nameBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(graphics);
    }

    private void frame(GuiGraphicsExtractor graphics, float time, float appear) {
        // The panel unfolds from a line across the middle as it opens.
        int half = (int) (HEIGHT / 2.0f * appear);
        int middle = top + HEIGHT / 2;
        graphics.fill(left, middle - half, left + WIDTH, middle + half, PANEL);
        if (appear < 1.0f) return;
        graphics.fill(left + 4, top + 26, left + WIDTH - 4, top + HEIGHT - 52, PANEL_INNER);
        outline(graphics, left, top, WIDTH, HEIGHT, LINE);
        // Brighter brackets at the corners, as on a lens.
        int c = 10;
        corner(graphics, left, top, c, 1, 1);
        corner(graphics, left + WIDTH - 1, top, c, -1, 1);
        corner(graphics, left, top + HEIGHT - 1, c, 1, -1);
        corner(graphics, left + WIDTH - 1, top + HEIGHT - 1, c, -1, -1);
        // Scanlines drift down the glass.
        int drift = (int) (time * 0.5f) % 3;
        for (int y = top + 1 + drift; y < top + HEIGHT - 1; y += 3) {
            graphics.fill(left + 1, y, left + WIDTH - 1, y + 1, 0x0A7FB2FF);
        }
    }

    private void corner(GuiGraphicsExtractor graphics, int x, int y, int length, int dx, int dy) {
        int x1 = x + dx * length;
        int y1 = y + dy * length;
        graphics.fill(Math.min(x, x1), y, Math.max(x, x1) + 1, y + 1, BRIGHT);
        graphics.fill(x, Math.min(y, y1), x + 1, Math.max(y, y1) + 1, BRIGHT);
    }

    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x, y, x + w, y + 1, colour);
        graphics.fill(x, y + h - 1, x + w, y + h, colour);
        graphics.fill(x, y, x + 1, y + h, colour);
        graphics.fill(x + w - 1, y, x + w, y + h, colour);
    }

    private void header(GuiGraphicsExtractor graphics) {
        // The pod's name, which is the whole title: click it to rename the pod.
        if (nameBox == null) {
            boolean hovered = !view.tether() && within(lastMouseX, lastMouseY, left + 8, top + 5, font.width(title()) + 4, 13);
            graphics.text(font, title(), left + 10, top + 8, WHITE, false);
            if (hovered) graphics.fill(left + 10, top + 17, left + 10 + font.width(title()), top + 18, BRIGHT);
        }
        // Sophons as pips: filled for each you have, hollow for what the soul has left.
        int pipX = left + WIDTH - 10 - view.maxSophons() * 8;
        Component label = Component.translatable("gui.quantimium.pod.sophons");
        graphics.text(font, label, pipX - font.width(label) - 5, top + 8, DIM, false);
        for (int i = 0; i < view.maxSophons(); i++) {
            int x = pipX + i * 8;
            if (i < view.sophons()) {
                graphics.fill(x, top + 8, x + 6, top + 14, BRIGHT);
            } else {
                outline(graphics, x, top + 8, 6, 6, FAINT);
            }
        }
        // This pod's modules, as chips after its name.
        int chipX = left + 10 + (nameBox == null ? font.width(title()) : NAME_BOX_WIDTH) + 8;
        for (PodModule module : PodModule.values()) {
            if (module == PodModule.PLATING || (view.modules() & module.bit()) == 0) continue;
            badge(graphics, chipX, top + 8, chipLetter(module), chipColour(module));
            chipX += 11;
        }
        graphics.fill(left + 8, top + 21, left + WIDTH - 8, top + 22, LINE);
    }

    /** What the header says: the pod's name, or that this is a Tether. */
    private String title() {
        return view.tether() ? Component.translatable("gui.quantimium.tether").getString() : view.name();
    }

    /** A double's name in the list: its pod's, or where it is when it is not in one. */
    private String nameOf(OpenPodScreenPayload.Destination destination) {
        BlockPos at = destination.pos();
        if (destination.has(OpenPodScreenPayload.FLAG_FIELD)) {
            return Component.translatable("gui.quantimium.pod.field", at.getX(), at.getY(), at.getZ()).getString();
        }
        if (destination.has(OpenPodScreenPayload.FLAG_DROPPED)) {
            return Component.translatable("gui.quantimium.pod.dropped", at.getX(), at.getY(), at.getZ()).getString();
        }
        if (destination.has(OpenPodScreenPayload.FLAG_TAKEN)) {
            return Component.translatable("gui.quantimium.pod.taken").getString();
        }
        return destination.name();
    }

    // ---- the tabs down the right, outside the panel ----

    private record SideTab(Component label, List<Component> tip, Runnable action) {}

    private List<SideTab> tabs() {
        List<SideTab> tabs = new ArrayList<>();
        if (view.stash()) {
            tabs.add(new SideTab(Component.translatable("gui.quantimium.pod.stash"),
                    List.of(Component.translatable("gui.quantimium.pod.stash.tip")),
                    () -> ClientPacketDistributor.sendToServer(OpenStashPayload.INSTANCE)));
        }
        if (view.tether()) return tabs;
        tabs.add(new SideTab(Component.translatable(view.listed() ? "gui.quantimium.pod.listed" : "gui.quantimium.pod.unlisted"),
                List.of(Component.translatable("gui.quantimium.pod.listed.tip")),
                () -> ClientPacketDistributor.sendToServer(new PodActionPayload(view.pos(), PodActionPayload.TOGGLE_LISTED, Util.NIL_UUID))));
        if (view.occupied()) {
            tabs.add(new SideTab(Component.translatable("gui.quantimium.pod.fold"),
                    List.of(Component.translatable("gui.quantimium.pod.fold.tip")),
                    () -> {
                        ClientPacketDistributor.sendToServer(new PodActionPayload(view.pos(), PodActionPayload.FOLD, Util.NIL_UUID));
                        onClose();
                    }));
        }
        return tabs;
    }

    private int tabX() {
        return left + WIDTH + 3;
    }

    private int tabY(int index) {
        return top + 6 + index * (TAB_HEIGHT + 3);
    }

    private void sideTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<SideTab> tabs = tabs();
        for (int i = 0; i < tabs.size(); i++) {
            int x = tabX();
            int y = tabY(i);
            boolean hovered = within(mouseX, mouseY, x, y, TAB_WIDTH, TAB_HEIGHT);
            graphics.fill(x, y, x + TAB_WIDTH, y + TAB_HEIGHT, hovered ? 0xF03485FF : PANEL);
            outline(graphics, x, y, TAB_WIDTH, TAB_HEIGHT, hovered ? WHITE : LINE);
            graphics.fill(x, y + 2, x + 1, y + TAB_HEIGHT - 2, BRIGHT);
            graphics.centeredText(font, tabs.get(i).label(), x + TAB_WIDTH / 2, y + 4, hovered ? WHITE : TEXT);
            if (hovered) graphics.setComponentTooltipForNextFrame(font, tabs.get(i).tip(), mouseX, mouseY);
        }
    }

    // ---- renaming ----

    private static final int NAME_BOX_WIDTH = 150;
    private static final int TAB_WIDTH = 58;
    private static final int TAB_HEIGHT = 16;

    @Nullable
    private EditBox nameBox;

    private void startRenaming() {
        nameBox = new EditBox(font, left + 8, top + 4, NAME_BOX_WIDTH, 14, Component.translatable("gui.quantimium.pod.rename"));
        nameBox.setMaxLength(SuperpositionPodBlockEntity.MAX_NAME);
        nameBox.setValue(view.name());
        nameBox.setTextColor(WHITE);
        // Input only: the panel is painted over what the screen draws for its widgets, so it is drawn after.
        addWidget(nameBox);
        setFocused(nameBox);
        nameBox.setFocused(true);
    }

    private void finishRenaming(boolean keep) {
        if (nameBox == null) return;
        if (keep && !nameBox.getValue().strip().equals(view.name())) {
            ClientPacketDistributor.sendToServer(new RenamePodPayload(view.pos(), nameBox.getValue()));
        }
        removeWidget(nameBox);
        nameBox = null;
    }

    private static String chipLetter(PodModule module) {
        return switch (module) {
            case RESCUE -> "R";
            case REGENERATION -> "+";
            case HARDENING -> "H";
            case WARD -> "W";
            case STASH -> "S";
            case CHARGE -> "C";
            case RELAY -> "L";
            case RECOVERY -> "V";
            default -> "";
        };
    }

    private static int chipColour(PodModule module) {
        return switch (module) {
            case RESCUE -> VIOLET;
            case REGENERATION -> GOOD;
            case WARD -> WHITE;
            default -> BRIGHT;
        };
    }

    /** The radar: rings, a turning sweep, this pod in the middle and a blip for each double. */
    private void radar(GuiGraphicsExtractor graphics, float time, int mouseX, int mouseY) {
        int cx = radarX();
        int cy = radarY();
        for (int ring = 1; ring <= 3; ring++) circle(graphics, cx, cy, RADAR_RADIUS * ring / 3, ring == 3 ? LINE : 0xFF16264A);
        graphics.fill(cx - RADAR_RADIUS, cy, cx + RADAR_RADIUS + 1, cy + 1, 0xFF16264A);
        graphics.fill(cx, cy - RADAR_RADIUS, cx + 1, cy + RADAR_RADIUS + 1, 0xFF16264A);
        graphics.text(font, "N", cx - 2, cy - RADAR_RADIUS - 9, FAINT, false);

        // The sweep, with a fading wake behind it.
        float sweep = time * 0.06f;
        for (int trail = 0; trail < 18; trail++) {
            float angle = sweep - trail * 0.035f;
            int alpha = (int) (110 * (1.0f - trail / 18.0f));
            for (int r = 3; r < RADAR_RADIUS; r += 2) {
                int x = cx + Math.round(Mth.cos(angle) * r);
                int y = cy + Math.round(Mth.sin(angle) * r);
                graphics.fill(x, y, x + 1, y + 1, (alpha << 24) | 0x3485FF);
            }
        }

        // This pod: you are here.
        float pulse = 0.5f + 0.5f * Mth.sin(time * 0.2f);
        graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, WHITE);
        circle(graphics, cx, cy, 4 + (int) (pulse * 3), ((int) (160 * (1 - pulse)) << 24) | 0x7FB2FF);

        double far = 64;
        for (OpenPodScreenPayload.Destination d : view.destinations()) far = Math.max(far, d.distance());
        int elsewhere = 0;
        for (OpenPodScreenPayload.Destination destination : view.destinations()) {
            if (destination.has(OpenPodScreenPayload.FLAG_FOLDED)) continue;
            int[] at = blip(destination, far, elsewhere);
            if (destination.distance() < 0) elsewhere++;
            boolean chosen = destination.id().equals(selected);
            boolean hovered = Math.abs(mouseX - at[0]) <= 3 && Math.abs(mouseY - at[1]) <= 3;
            int colour = destination.has(OpenPodScreenPayload.FLAG_TAKEN) ? VIOLET
                    : destination.has(OpenPodScreenPayload.FLAG_DROPPED) ? WARN
                    : destination.has(OpenPodScreenPayload.FLAG_FIELD) ? FIELD
                    : destination.distance() < 0 ? VIOLET : destination.has(OpenPodScreenPayload.FLAG_ANCHOR) ? GOOD : BRIGHT;
            // A blip brightens as the sweep passes over it.
            float bearing = (float) Math.atan2(at[1] - cy, at[0] - cx);
            float since = Mth.positiveModulo(sweep - bearing, Mth.TWO_PI);
            int glowAlpha = (int) (200 * Math.max(0.0f, 1.0f - since / 1.2f));
            graphics.fill(at[0] - 3, at[1] - 3, at[0] + 4, at[1] + 4, (glowAlpha << 24) | (colour & 0xFFFFFF));
            if (destination.has(OpenPodScreenPayload.FLAG_RELAY)) {
                // An empty pod the Relay reaches: hollow, as nobody waits there yet.
                outline(graphics, at[0] - 2, at[1] - 2, 5, 5, colour);
            } else {
                graphics.fill(at[0] - 1, at[1] - 1, at[0] + 2, at[1] + 2, colour);
            }
            if (chosen || hovered) {
                circle(graphics, at[0], at[1], 6, chosen ? WHITE : BRIGHT);
                String name = nameOf(destination);
                int labelX = Mth.clamp(at[0] - font.width(name) / 2, left + 6, radarX() + RADAR_RADIUS - font.width(name));
                graphics.text(font, name, labelX, at[1] + 8, chosen ? WHITE : TEXT, true);
            }
        }
    }

    /** Where a double's blip goes: bearing from this pod, log distance; other dimensions on the rim. */
    private int[] blip(OpenPodScreenPayload.Destination destination, double far, int elsewhereIndex) {
        int cx = radarX();
        int cy = radarY();
        if (destination.distance() < 0) {
            float angle = -Mth.HALF_PI + 0.7f + elsewhereIndex * 0.55f;
            return new int[]{cx + Math.round(Mth.cos(angle) * (RADAR_RADIUS - 2)), cy + Math.round(Mth.sin(angle) * (RADAR_RADIUS - 2))};
        }
        double dx = destination.pos().getX() - view.pos().getX();
        double dz = destination.pos().getZ() - view.pos().getZ();
        double reach = Math.log1p(destination.distance()) / Math.log1p(far);
        double radius = 8 + (RADAR_RADIUS - 14) * reach;
        double length = Math.max(1.0e-6, Math.sqrt(dx * dx + dz * dz));
        return new int[]{cx + (int) Math.round(dx / length * radius), cy + (int) Math.round(dz / length * radius)};
    }

    private static void circle(GuiGraphicsExtractor graphics, int cx, int cy, int radius, int colour) {
        int steps = Math.max(16, radius * 4);
        for (int i = 0; i < steps; i++) {
            float angle = i / (float) steps * Mth.TWO_PI;
            int x = cx + Math.round(Mth.cos(angle) * radius);
            int y = cy + Math.round(Mth.sin(angle) * radius);
            graphics.fill(x, y, x + 1, y + 1, colour);
        }
    }

    private List<OpenPodScreenPayload.Destination> rows() {
        return new ArrayList<>(view.destinations());
    }

    private void list(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<OpenPodScreenPayload.Destination> rows = rows();
        int x = listX();
        int y = listY();
        if (rows.isEmpty()) {
            graphics.textWithWordWrap(font, Component.translatable("gui.quantimium.pod.no_doubles"), x + 4, y + 6,
                    listWidth() - 8, DIM);
            return;
        }
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - VISIBLE_ROWS));
        for (int i = 0; i < Math.min(VISIBLE_ROWS, rows.size() - scroll); i++) {
            OpenPodScreenPayload.Destination destination = rows.get(scroll + i);
            int rowY = y + i * ROW_HEIGHT;
            boolean folded = destination.has(OpenPodScreenPayload.FLAG_FOLDED);
            boolean chosen = destination.id().equals(selected);
            boolean hovered = !folded && mouseX >= x && mouseX < x + listWidth() && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;
            if (chosen) {
                graphics.fill(x, rowY, x + listWidth(), rowY + ROW_HEIGHT - 2, 0x553485FF);
                graphics.fill(x, rowY, x + 2, rowY + ROW_HEIGHT - 2, BRIGHT);
            } else if (hovered) {
                graphics.fill(x, rowY, x + listWidth(), rowY + ROW_HEIGHT - 2, 0x2A3485FF);
            }
            if (folded) {
                graphics.text(font, Component.translatable("gui.quantimium.pod.folded"), x + 6, rowY + 3, FAINT, false);
                graphics.text(font, Component.translatable("gui.quantimium.pod.folded.tip"), x + 6, rowY + 13, FAINT, false);
                continue;
            }
            boolean affordable = destination.cost() <= view.energy();
            String name = font.plainSubstrByWidth(nameOf(destination), listWidth() - 16 - badgeWidth(destination));
            int nameColour = destination.has(OpenPodScreenPayload.FLAG_TAKEN) ? VIOLET
                    : destination.has(OpenPodScreenPayload.FLAG_DROPPED) ? WARN
                    : destination.has(OpenPodScreenPayload.FLAG_FIELD) ? FIELD : chosen ? WHITE : TEXT;
            graphics.text(font, name, x + 6, rowY + 3, nameColour, false);
            badges(graphics, destination, x + 6 + font.width(name) + 4, rowY + 3);
            Component where = destination.distance() < 0
                    ? Component.translatable("gui.quantimium.pod.elsewhere", dimensionName(destination.dimension()))
                    : Component.translatable("gui.quantimium.pod.distance", String.format("%,d", destination.distance()));
            if (destination.has(OpenPodScreenPayload.FLAG_RELAY)) {
                where = Component.translatable(destination.has(OpenPodScreenPayload.FLAG_NO_SPARE)
                        ? "gui.quantimium.pod.relay.no_spare" : "gui.quantimium.pod.relay", where);
            }
            String cost = shortFe(destination.cost());
            String whereText = font.plainSubstrByWidth(where.getString(), listWidth() - 16 - font.width(cost));
            graphics.text(font, whereText, x + 6, rowY + 13,
                    destination.has(OpenPodScreenPayload.FLAG_RELAY) || destination.distance() < 0 ? VIOLET : DIM, false);
            graphics.text(font, cost, x + listWidth() - 4 - font.width(cost), rowY + 13, affordable ? BRIGHT : WARN, false);
        }
        if (rows.size() > VISIBLE_ROWS) {
            int trackHeight = VISIBLE_ROWS * ROW_HEIGHT - 2;
            int thumb = Math.max(8, trackHeight * VISIBLE_ROWS / rows.size());
            int thumbY = y + (trackHeight - thumb) * scroll / Math.max(1, rows.size() - VISIBLE_ROWS);
            graphics.fill(x + listWidth() + 2, y, x + listWidth() + 3, y + trackHeight, LINE);
            graphics.fill(x + listWidth() + 1, thumbY, x + listWidth() + 4, thumbY + thumb, BRIGHT);
        }
    }

    private int badgeWidth(OpenPodScreenPayload.Destination destination) {
        int width = 0;
        if (destination.has(OpenPodScreenPayload.FLAG_ANCHOR)) width += 10;
        if (destination.has(OpenPodScreenPayload.FLAG_RESCUE)) width += 10;
        return width;
    }

    private void badges(GuiGraphicsExtractor graphics, OpenPodScreenPayload.Destination destination, int x, int y) {
        if (destination.has(OpenPodScreenPayload.FLAG_ANCHOR)) {
            badge(graphics, x, y, "A", GOOD);
            x += 10;
        }
        if (destination.has(OpenPodScreenPayload.FLAG_RESCUE)) badge(graphics, x, y, "R", VIOLET);
    }

    private void badge(GuiGraphicsExtractor graphics, int x, int y, String letter, int colour) {
        outline(graphics, x, y - 1, 9, 9, colour);
        graphics.text(font, letter, x + 2, y, colour, false);
    }

    private static String dimensionName(String id) {
        Identifier location = Identifier.tryParse(id);
        if (location == null) return id;
        String path = location.getPath().replace('_', ' ');
        return Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    private static String shortFe(int fe) {
        if (fe >= 1_000_000) return String.format("%.2fM FE", fe / 1_000_000.0);
        if (fe >= 1_000) return String.format("%.1fk FE", fe / 1_000.0);
        return fe + " FE";
    }

    /** The buffer, with what the chosen swap would take out of it marked at its end. */
    private void power(GuiGraphicsExtractor graphics) {
        int x = left + 10;
        int y = barY();
        int w = WIDTH - 20;
        int h = 6;
        graphics.fill(x, y, x + w, y + h, 0xFF0A1128);
        outline(graphics, x - 1, y - 1, w + 2, h + 2, LINE);
        float stored = view.capacity() <= 0 ? 0 : view.energy() / (float) view.capacity();
        int filled = Math.round(w * stored);
        graphics.fill(x, y, x + filled, y + h, ACCENT);
        graphics.fill(x, y, x + filled, y + 1, BRIGHT);
        OpenPodScreenPayload.Destination chosen = selected == null ? null : find(selected);
        if (chosen != null && !chosen.has(OpenPodScreenPayload.FLAG_FOLDED)) {
            int costWidth = Math.round(w * Math.min(1.0f, chosen.cost() / (float) Math.max(1, view.capacity())));
            boolean affordable = chosen.cost() <= view.energy();
            int from = Math.max(x, x + filled - costWidth);
            graphics.fill(from, y, affordable ? x + filled : from + costWidth, y + h, affordable ? 0xCCD6E6FF : 0xCCFF5A4A);
        }
        String label = String.format("%,d / %,d FE", view.energy(), view.capacity());
        graphics.text(font, label, x + w - font.width(label), y - 10, DIM, false);
        graphics.text(font, Component.translatable("gui.quantimium.pod.power"), x, y - 10, DIM, false);
    }

    private boolean canSwap() {
        if (recovering()) return canRecover();
        OpenPodScreenPayload.Destination chosen = selected == null ? null : find(selected);
        return chosen != null && !chosen.has(OpenPodScreenPayload.FLAG_FOLDED) && !chosen.has(OpenPodScreenPayload.FLAG_NO_SPARE)
                && !chosen.has(OpenPodScreenPayload.FLAG_DROPPED) && !chosen.has(OpenPodScreenPayload.FLAG_TAKEN)
                && !(view.tether() && chosen.has(OpenPodScreenPayload.FLAG_FIELD))
                && view.inside() && view.formed() && !view.occupied() && chosen.cost() <= view.energy();
    }

    /**
     * Whether the main button recovers rather than swaps: a pod with a Recovery module, opened from outside,
     * with a field double or a knocked-out Sophon picked.
     */
    private boolean recovering() {
        OpenPodScreenPayload.Destination chosen = selected == null ? null : find(selected);
        return !view.tether() && (view.modules() & PodModule.RECOVERY.bit()) != 0 && !view.inside() && chosen != null
                && (chosen.has(OpenPodScreenPayload.FLAG_FIELD) || chosen.has(OpenPodScreenPayload.FLAG_DROPPED));
    }

    private boolean canRecover() {
        OpenPodScreenPayload.Destination chosen = selected == null ? null : find(selected);
        return chosen != null && view.formed() && !view.occupied() && chosen.cost() <= view.energy();
    }

    /**
     * What the main button does: recover, swap from the Tether, send a double through the Relay first,
     * or a plain swap.
     */
    private int swapAction() {
        if (recovering()) return PodActionPayload.RECOVER;
        if (view.tether()) return PodActionPayload.TETHER_SWAP;
        OpenPodScreenPayload.Destination chosen = selected == null ? null : find(selected);
        return chosen != null && chosen.has(OpenPodScreenPayload.FLAG_RELAY) ? PodActionPayload.RELAY : PodActionPayload.SWAP;
    }

    private boolean hasRescue() {
        return (view.modules() & PodModule.RESCUE.bit()) != 0;
    }

    private void buttons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        button(graphics, swapButtonX(), buttonY(), 70, Component.translatable(recovering()
                ? "gui.quantimium.pod.recover" : "gui.quantimium.pod.swap"), canSwap(), mouseX, mouseY, true);
        if (hasRescue()) {
            button(graphics, anchorButtonX(), buttonY(), 84, Component.translatable(view.anchor()
                    ? "gui.quantimium.pod.anchor.on" : "gui.quantimium.pod.anchor.off"), true, mouseX, mouseY, false);
        }
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int w, Component label, boolean enabled, int mouseX, int mouseY,
                        boolean primary) {
        button(graphics, x, y, w, 16, label, enabled, mouseX, mouseY, primary);
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int w, int h, Component label, boolean enabled, int mouseX,
                        int mouseY, boolean primary) {
        boolean hovered = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int fill = !enabled ? 0x40202840 : hovered ? (primary ? 0xFF3485FF : 0x663485FF) : (primary ? 0xAA1B4FB8 : 0x401B4FB8);
        graphics.fill(x, y, x + w, y + h, fill);
        outline(graphics, x, y, w, h, enabled ? BRIGHT : FAINT);
        graphics.centeredText(font, label, x + w / 2, y + (h - 8) / 2, enabled ? WHITE : FAINT);
    }

    /** One line saying what to do next, or why you cannot. */
    private void status(GuiGraphicsExtractor graphics) {
        Component message;
        int colour = DIM;
        OpenPodScreenPayload.Destination chosen = selected == null ? null : find(selected);
        if (!view.formed()) {
            message = Component.translatable("gui.quantimium.pod.status.unformed");
            colour = WARN;
        } else if (recovering()) {
            message = view.occupied() ? Component.translatable("gui.quantimium.pod.status.recover_occupied")
                    : Component.translatable("gui.quantimium.pod.status.recover");
            colour = view.occupied() ? WARN : GOOD;
        } else if (chosen != null && chosen.has(OpenPodScreenPayload.FLAG_TAKEN)) {
            message = Component.translatable("gui.quantimium.pod.status.taken");
            colour = VIOLET;
        } else if (view.tether() && chosen != null && chosen.has(OpenPodScreenPayload.FLAG_FIELD)) {
            message = Component.translatable("gui.quantimium.pod.status.field_from_tether");
            colour = WARN;
        } else if (chosen != null && chosen.has(OpenPodScreenPayload.FLAG_DROPPED)) {
            message = Component.translatable("gui.quantimium.pod.status.dropped");
            colour = WARN;
        } else if (view.occupied()) {
            message = Component.translatable("gui.quantimium.pod.status.occupied");
        } else if (!view.inside()) {
            message = Component.translatable("gui.quantimium.pod.status.outside");
        } else if (chosen == null) {
            message = Component.translatable("gui.quantimium.pod.status.choose");
        } else if (chosen.cost() > view.energy()) {
            message = Component.translatable("gui.quantimium.pod.status.power",
                    shortFe(chosen.cost() - view.energy()), (chosen.cost() - view.energy()) / 1000 / 20 + 1);
            colour = WARN;
        } else if (chosen.has(OpenPodScreenPayload.FLAG_NO_SPARE)) {
            message = Component.translatable("gui.quantimium.pod.status.no_spare");
            colour = WARN;
        } else if (chosen.has(OpenPodScreenPayload.FLAG_RELAY)) {
            message = Component.translatable("gui.quantimium.pod.status.relay", chosen.name());
            colour = GOOD;
        } else {
            message = Component.translatable("gui.quantimium.pod.status.ready", nameOf(chosen));
            colour = GOOD;
        }
        int maxWidth = (hasRescue() ? anchorButtonX() : swapButtonX()) - left - 18;
        graphics.text(font, font.plainSubstrByWidth(message.getString(), maxWidth), left + 10, buttonY() + 4, colour, false);
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == 0) {
            if (within(mouseX, mouseY, swapButtonX(), buttonY(), 70, 16) && canSwap()) {
                click();
                ClientPacketDistributor.sendToServer(new PodActionPayload(view.pos(), swapAction(), selected));
                onClose();
                return true;
            }
            if (nameBox != null && !nameBox.isMouseOver(mouseX, mouseY)) finishRenaming(true);
            if (nameBox == null && !view.tether() && within(mouseX, mouseY, left + 8, top + 5, font.width(title()) + 4, 13)) {
                click();
                startRenaming();
                return true;
            }
            List<SideTab> sideTabs = tabs();
            for (int i = 0; i < sideTabs.size(); i++) {
                if (within(mouseX, mouseY, tabX(), tabY(i), TAB_WIDTH, TAB_HEIGHT)) {
                    click();
                    sideTabs.get(i).action().run();
                    return true;
                }
            }
            if (hasRescue() && within(mouseX, mouseY, anchorButtonX(), buttonY(), 84, 16)) {
                click();
                ClientPacketDistributor.sendToServer(new PodActionPayload(view.pos(), PodActionPayload.TOGGLE_ANCHOR, Util.NIL_UUID));
                return true;
            }
            List<OpenPodScreenPayload.Destination> rows = rows();
            for (int i = 0; i < Math.min(VISIBLE_ROWS, rows.size() - scroll); i++) {
                OpenPodScreenPayload.Destination destination = rows.get(scroll + i);
                if (destination.has(OpenPodScreenPayload.FLAG_FOLDED)) continue;
                if (within(mouseX, mouseY, listX(), listY() + i * ROW_HEIGHT, listWidth(), ROW_HEIGHT - 2)) {
                    select(destination.id());
                    return true;
                }
            }
            double far = 64;
            for (OpenPodScreenPayload.Destination d : view.destinations()) far = Math.max(far, d.distance());
            int elsewhere = 0;
            for (OpenPodScreenPayload.Destination destination : view.destinations()) {
                if (destination.has(OpenPodScreenPayload.FLAG_FOLDED)) continue;
                int[] at = blip(destination, far, elsewhere);
                if (destination.distance() < 0) elsewhere++;
                if (Math.abs(mouseX - at[0]) <= 4 && Math.abs(mouseY - at[1]) <= 4) {
                    select(destination.id());
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void select(UUID id) {
        if (!id.equals(selected)) click();
        selected = id;
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.4f));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (int) Math.signum(scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (nameBox != null) {
            // Enter keeps the new name, Escape drops it; neither closes the screen.
            if (keyCode == 257 || keyCode == 335) {
                finishRenaming(true);
                return true;
            }
            if (keyCode == 256) {
                finishRenaming(false);
                return true;
            }
            return nameBox.keyPressed(event) || true;
        }
        // Enter swaps, as the button would.
        if ((keyCode == 257 || keyCode == 335) && canSwap()) {
            ClientPacketDistributor.sendToServer(new PodActionPayload(view.pos(), swapAction(), selected));
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private static boolean within(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}

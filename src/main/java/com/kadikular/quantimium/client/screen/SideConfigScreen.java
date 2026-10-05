package com.kadikular.quantimium.client.screen;

import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.input.MouseButtonEvent;
import com.kadikular.quantimium.block.entity.simulation.SideAutomationProfile;
import com.kadikular.quantimium.block.entity.simulation.SideConfig;
import com.kadikular.quantimium.block.entity.simulation.SideMode;
import com.kadikular.quantimium.block.entity.simulation.SimulatorFluidTanks;
import com.kadikular.quantimium.client.screen.ui.QuantumOverlay;
import com.kadikular.quantimium.client.screen.ui.QuantumUiColours;
import com.kadikular.quantimium.client.screen.ui.QuantumWidgets;
import com.kadikular.quantimium.network.UpdateSideConfigPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Sided automation editor. The six faces are laid out as an unfolded cube around the front, and are
 * named from the machine's own facing, because "south" means nothing to somebody looking at a block
 * that is the same on every side. The world direction is still there on hover for anyone piping by
 * compass. One face is edited at a time in the pane on the right.
 */
public final class SideConfigScreen implements QuantumOverlay {

    private static final int WIDTH = 248;
    private static final int HEIGHT = 196;

    private static final int TILE = 24;
    private static final int TILE_STEP = 26;

    private static final int NET_X = 12;
    private static final int NET_Y = 48;
    private static final int PANE_X = 124;
    private static final int PANE_Y = 44;
    private static final int PANE_WIDTH = 116;
    private static final int PANE_HEIGHT = 120;

    private static final int CHIP_HEIGHT = 14;
    private static final int MODE_CHIP_WIDTH = 28;
    private static final int AUTO_CHIP_WIDTH = 52;
    private static final int MASK_GAP = 14;

    private enum Tab { ITEMS, FLUIDS }

    /** A face of the machine as the player sees it, and where it sits in the unfolded net. */
    private enum Face {
        TOP("Top", "T", 1, 0),
        LEFT("Left", "L", 0, 1),
        FRONT("Front", "F", 1, 1),
        RIGHT("Right", "R", 2, 1),
        BACK("Back", "B", 3, 1),
        BOTTOM("Bottom", "D", 1, 2);

        private final String label;
        private final String letter;
        private final int column;
        private final int row;

        Face(String label, String letter, int column, int row) {
            this.label = label;
            this.letter = letter;
            this.column = column;
            this.row = row;
        }

        Direction world(Direction facing) {
            return switch (this) {
                case FRONT -> facing;
                case BACK -> facing.getOpposite();
                // Left and right as seen by someone stood in front of the machine looking at it.
                case LEFT -> facing.getClockWise();
                case RIGHT -> facing.getCounterClockWise();
                case TOP -> Direction.UP;
                case BOTTOM -> Direction.DOWN;
            };
        }
    }

    private final BlockPos pos;
    private final Direction facing;
    private final SideAutomationProfile profile;
    private final boolean showSlotMasks;
    private final SideMode[] itemModes = new SideMode[6];
    private final int[] itemInputMasks = new int[6];
    private final int[] itemOutputMasks = new int[6];
    private final boolean[] autoItemInputs = new boolean[6];
    private final boolean[] autoItemOutputs = new boolean[6];
    private final SideMode[] fluidModes = new SideMode[6];
    private final int[] fluidInputMasks = new int[6];
    private final int[] fluidOutputMasks = new int[6];
    private final boolean[] autoFluidInputs = new boolean[6];
    private final boolean[] autoFluidOutputs = new boolean[6];
    private final List<Button> buttons = new ArrayList<>();

    private Tab tab = Tab.ITEMS;
    private Face selected = Face.FRONT;
    private int left;
    private int top;
    private boolean closed;

    public SideConfigScreen(BlockPos pos, List<SideConfig> configs, SideAutomationProfile profile) {
        this(pos, configs, profile, true);
    }

    public SideConfigScreen(BlockPos pos, List<SideConfig> configs, SideAutomationProfile profile,
                            boolean showSlotMasks) {
        this.pos = pos;
        this.facing = readFacing(pos);
        this.profile = profile;
        this.showSlotMasks = showSlotMasks;
        if (!profile.hasItems() && profile.hasFluids()) tab = Tab.FLUIDS;
        for (SideConfig config : SideConfig.normalize(configs)) {
            int id = config.side().get3DDataValue();
            itemModes[id] = config.itemMode();
            itemInputMasks[id] = config.itemInputMask();
            itemOutputMasks[id] = config.itemOutputMask();
            autoItemInputs[id] = config.autoItemInput();
            autoItemOutputs[id] = config.autoItemOutput();
            fluidModes[id] = config.fluidMode();
            fluidInputMasks[id] = config.fluidInputMask();
            fluidOutputMasks[id] = config.fluidOutputMask();
            autoFluidInputs[id] = config.autoFluidInput();
            autoFluidOutputs[id] = config.autoFluidOutput();
        }
        selectFirstSupportedFace();
    }

    private static Direction readFacing(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return Direction.NORTH;
        BlockState state = level.getBlockState(pos);
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                : Direction.NORTH;
    }

    @Override
    public net.minecraft.client.renderer.Rect2i bounds() {
        return new net.minecraft.client.renderer.Rect2i(left, top, WIDTH, HEIGHT);
    }

    @Override
    public void layout(int screenWidth, int screenHeight) {
        left = (screenWidth - WIDTH) / 2;
        top = (screenHeight - HEIGHT) / 2;
        buttons.clear();
        int footer = top + HEIGHT - 24;
        buttons.add(Button.builder(Component.literal("Apply"), b -> apply())
                .bounds(left + 108, footer, 62, 18).build());
        buttons.add(Button.builder(Component.literal("Cancel"), b -> closed = true)
                .bounds(left + 176, footer, 64, 18).build());
    }

    private void apply() {
        List<SideConfig> configs = new ArrayList<>(6);
        for (Direction side : Direction.values()) {
            int id = side.get3DDataValue();
            configs.add(new SideConfig(side, itemModes[id], itemInputMasks[id], itemOutputMasks[id],
                    autoItemInputs[id], autoItemOutputs[id],
                    fluidModes[id], fluidInputMasks[id], fluidOutputMasks[id],
                    autoFluidInputs[id], autoFluidOutputs[id]));
        }
        ClientPacketDistributor.sendToServer(new UpdateSideConfigPayload(pos, configs));
        closed = true;
    }

    @Override
    public boolean isClosed() { return closed; }

    private boolean items() {
        return tab == Tab.ITEMS;
    }

    private int maskBits() {
        return items() ? 9 : SimulatorFluidTanks.INPUT_TANKS;
    }

    private int id(Face face) {
        return face.world(facing).get3DDataValue();
    }

    private SideMode mode(Face face) {
        return items() ? itemModes[id(face)] : fluidModes[id(face)];
    }

    private boolean autoInput(Face face) {
        return items() ? autoItemInputs[id(face)] : autoFluidInputs[id(face)];
    }

    private boolean autoOutput(Face face) {
        return items() ? autoItemOutputs[id(face)] : autoFluidOutputs[id(face)];
    }

    private boolean supports(Face face) {
        Direction world = face.world(facing);
        return items() ? profile.supportsItems(world) : profile.supportsFluids(world);
    }

    private void selectFirstSupportedFace() {
        if (supports(selected)) return;
        for (Face face : Face.values()) {
            if (supports(face)) {
                selected = face;
                return;
            }
        }
    }

    private int inputMask(Face face) {
        return items() ? itemInputMasks[id(face)] : fluidInputMasks[id(face)];
    }

    private int outputMask(Face face) {
        return items() ? itemOutputMasks[id(face)] : fluidOutputMasks[id(face)];
    }

    private int tileX(Face face) {
        return left + NET_X + face.column * TILE_STEP;
    }

    private int tileY(Face face) {
        return top + NET_Y + face.row * TILE_STEP;
    }

    private int paneX() {
        return left + PANE_X;
    }

    private int paneY() {
        return top + PANE_Y;
    }

    private int modeChipX(SideMode mode) {
        return paneX() + 4 + mode.ordinal() * MODE_CHIP_WIDTH;
    }

    private int modeChipY() {
        return paneY() + 26;
    }

    private int autoChipY() {
        return paneY() + 44;
    }

    private int maskGridY() {
        return paneY() + 76;
    }

    private int inputGridX() {
        return paneX() + 4;
    }

    private int outputGridX() {
        return inputGridX() + QuantumWidgets.MASK_COLUMNS * QuantumWidgets.MASK_CELL + MASK_GAP;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float partialTick) {
        List<Component> tooltip = null;

        graphics.nextStratum();

        QuantumWidgets.backdrop(graphics);
        QuantumWidgets.framedPanel(graphics, left, top, WIDTH, HEIGHT);
        QuantumWidgets.title(graphics, font, Component.literal("Sided Automation"), left + 8, top + 8);

        if (profile.hasItems()) {
            QuantumWidgets.tab(graphics, font, "Items", left + 8, top + 24, items());
        }
        if (profile.hasFluids()) {
            QuantumWidgets.tab(graphics, font, "Fluids", left + 64, top + 24, !items());
        }

        graphics.text(font, "Facing " + facing.getName(), left + 124, top + 27,
                QuantumUiColours.TEXT_MUTED, false);

        for (Face face : Face.values()) {
            if (!supports(face)) continue;
            drawFaceTile(graphics, font, face);
            if (QuantumWidgets.hit(mouseX, mouseY, tileX(face), tileY(face), TILE, TILE)) {
                tooltip = faceTooltip(face);
            }
        }

        drawLegend(graphics, font);

        List<Component> paneTooltip = drawDetailPane(graphics, font, mouseX, mouseY);
        if (paneTooltip != null) tooltip = paneTooltip;

        for (Button button : buttons) button.extractRenderState(graphics, mouseX, mouseY, partialTick);

        if (tooltip != null) graphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
    }

    private void drawFaceTile(GuiGraphicsExtractor graphics, Font font, Face face) {
        int x = tileX(face);
        int y = tileY(face);
        SideMode mode = mode(face);

        graphics.fill(x, y, x + TILE, y + TILE, QuantumUiColours.modeFill(mode));
        QuantumWidgets.outline(graphics, x, y, TILE, TILE,
                face == selected ? QuantumUiColours.SELECTION : QuantumUiColours.BORDER);

        int letterColour = face == Face.FRONT ? QuantumUiColours.ACCENT : QuantumUiColours.TEXT;
        graphics.text(font, face.letter, x + (TILE - font.width(face.letter)) / 2, y + 3,
                letterColour, false);

        String badge = modeBadge(mode);
        graphics.text(font, badge, x + (TILE - font.width(badge)) / 2, y + 13,
                QuantumUiColours.TEXT_DIM, false);

        if (mode != SideMode.DISABLED) {
            if (autoInput(face)) {
                graphics.fill(x + 1, y + 1, x + 4, y + 4, QuantumUiColours.CHIP_IN);
            }
            if (autoOutput(face)) {
                graphics.fill(x + TILE - 4, y + 1, x + TILE - 1, y + 4,
                        QuantumUiColours.CHIP_OUT);
            }
        }
    }

    /** Key to the tile colours, in the space the net leaves below itself. */
    private void drawLegend(GuiGraphicsExtractor graphics, Font font) {
        int x = left + NET_X;
        int y = top + NET_Y + 3 * TILE_STEP + 4;
        for (SideMode mode : SideMode.values()) {
            int swatchX = x + (mode.ordinal() % 2) * 52;
            int swatchY = y + (mode.ordinal() / 2) * 12;
            graphics.fill(swatchX, swatchY, swatchX + 8, swatchY + 8, QuantumUiColours.modeFill(mode));
            graphics.text(font, modeLabel(mode), swatchX + 12, swatchY, QuantumUiColours.TEXT_DIM, false);
        }
        graphics.fill(x, y + 26, x + 3, y + 29, QuantumUiColours.CHIP_IN);
        graphics.fill(x + 5, y + 26, x + 8, y + 29, QuantumUiColours.CHIP_OUT);
        graphics.text(font, "auto in / out", x + 12, y + 25, QuantumUiColours.TEXT_DIM, false);
    }

    @Nullable
    private List<Component> drawDetailPane(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        int x = paneX();
        int y = paneY();
        graphics.fill(x, y, x + PANE_WIDTH, y + PANE_HEIGHT, 0x30FFFFFF);
        QuantumWidgets.outline(graphics, x, y, PANE_WIDTH, PANE_HEIGHT, QuantumUiColours.BORDER);

        Direction world = selected.world(facing);
        graphics.text(font, selected.label, x + 4, y + 6, QuantumUiColours.ACCENT, false);
        graphics.text(font, world.getName(), x + PANE_WIDTH - 4 - font.width(world.getName()), y + 6,
                QuantumUiColours.TEXT_MUTED, false);
        String neighbor = neighborName(world);
        String neighborLine = font.plainSubstrByWidth("Beside: " + neighbor, PANE_WIDTH - 8);
        graphics.text(font, neighborLine, x + 4, y + 16, QuantumUiColours.TEXT_DIM, false);

        List<Component> tooltip = null;
        SideMode current = mode(selected);
        for (SideMode mode : SideMode.values()) {
            int chipX = modeChipX(mode);
            QuantumWidgets.chip(graphics, font, modeLabel(mode), chipX, modeChipY(),
                    MODE_CHIP_WIDTH - 2, CHIP_HEIGHT, mode == current, QuantumUiColours.modeFill(mode));
            if (QuantumWidgets.hit(mouseX, mouseY, chipX, modeChipY(), MODE_CHIP_WIDTH - 2, CHIP_HEIGHT)) {
                tooltip = List.of(Component.literal(modeTooltip(mode)));
            }
        }

        boolean autoIn = autoInput(selected);
        boolean autoOut = autoOutput(selected);
        QuantumWidgets.chip(graphics, font, "AUTO IN", x + 4, autoChipY(),
                AUTO_CHIP_WIDTH, CHIP_HEIGHT, autoIn, QuantumUiColours.CHIP_IN);
        QuantumWidgets.chip(graphics, font, "AUTO OUT", x + 60, autoChipY(),
                AUTO_CHIP_WIDTH, CHIP_HEIGHT, autoOut, QuantumUiColours.CHIP_OUT);
        if (QuantumWidgets.hit(mouseX, mouseY, x + 4, autoChipY(), AUTO_CHIP_WIDTH, CHIP_HEIGHT)) {
            tooltip = List.of(Component.literal("§6Auto input"),
                    Component.literal("§7Pull from the neighbouring block without a pipe."));
        }
        if (QuantumWidgets.hit(mouseX, mouseY, x + 60, autoChipY(), AUTO_CHIP_WIDTH, CHIP_HEIGHT)) {
            tooltip = List.of(Component.literal("§6Auto output"),
                    Component.literal("§7Push into the neighbouring block without a pipe."));
        }

        if (showSlotMasks) {
            int bits = maskBits();
            String insertLabel = items() ? "Insert" : "Fill";
            String extractLabel = items() ? "Extract" : "Drain";
            graphics.text(font, insertLabel, inputGridX(), maskGridY() - 10, QuantumUiColours.TEXT_DIM, false);
            graphics.text(font, extractLabel, outputGridX(), maskGridY() - 10, QuantumUiColours.TEXT_DIM, false);

            QuantumWidgets.maskGrid(graphics, inputGridX(), maskGridY(), inputMask(selected), bits,
                    QuantumUiColours.CHIP_IN);
            QuantumWidgets.maskGrid(graphics, outputGridX(), maskGridY(), outputMask(selected), bits,
                    QuantumUiColours.CHIP_OUT);

            int inputBit = QuantumWidgets.maskBitAt(inputGridX(), maskGridY(), bits, mouseX, mouseY);
            if (inputBit >= 0) tooltip = maskTooltip(insertLabel, inputBit);
            int outputBit = QuantumWidgets.maskBitAt(outputGridX(), maskGridY(), bits, mouseX, mouseY);
            if (outputBit >= 0) tooltip = maskTooltip(extractLabel, outputBit);
        } else {
            graphics.text(font, "All remote slots", x + 4, maskGridY(), QuantumUiColours.TEXT_DIM, false);
        }

        return tooltip;
    }

    private List<Component> maskTooltip(String action, int bit) {
        String target = items() ? "input slot " + bit : "tank " + bit;
        return List.of(Component.literal("§6" + action + " " + target),
                Component.literal("§7Click to allow or block this face for it."));
    }

    private List<Component> faceTooltip(Face face) {
        SideMode mode = mode(face);
        List<Component> lines = new ArrayList<>(5);
        lines.add(Component.literal("§b" + face.label + " face"));
        lines.add(Component.literal("§7World: §f" + face.world(facing).getName()));
        lines.add(Component.literal("§7Neighbour: §f" + neighborName(face.world(facing))));
        String automatic = (autoInput(face) ? " §aauto in" : "")
                + (autoOutput(face) ? " §6auto out" : "");
        lines.add(Component.literal("§7Mode: §f" + modeLabel(mode) + automatic));
        lines.add(Component.literal("§8Click to edit, right-click to cycle mode"));
        return lines;
    }

    private String neighborName(Direction side) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return "Unknown";
        BlockState neighbor = level.getBlockState(pos.relative(side));
        if (neighbor.is(Blocks.AIR)) return "nothing";
        return neighbor.getBlock().getName().getString();
    }

    private static String modeLabel(SideMode mode) {
        return switch (mode) {
            case DISABLED -> "Off";
            case INPUT -> "In";
            case OUTPUT -> "Out";
            case BOTH -> "Both";
        };
    }

    private static String modeBadge(SideMode mode) {
        return switch (mode) {
            case DISABLED -> "--";
            case INPUT -> "IN";
            case OUTPUT -> "OUT";
            case BOTH -> "I/O";
        };
    }

    private static String modeTooltip(SideMode mode) {
        return switch (mode) {
            case DISABLED -> "§7Nothing may move through this face.";
            case INPUT -> "§7Accepts insertions only.";
            case OUTPUT -> "§7Allows extraction only.";
            case BOTH -> "§7Accepts insertions and allows extraction.";
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Button widget : buttons) if (widget.mouseClicked(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, 0)), false)) return true;

        if (mouseY >= top + 24 && mouseY < top + 38) {
            if (profile.hasItems() && mouseX >= left + 8 && mouseX < left + 58) {
                tab = Tab.ITEMS;
                selectFirstSupportedFace();
                return true;
            }
            if (profile.hasFluids() && mouseX >= left + 64 && mouseX < left + 114) {
                tab = Tab.FLUIDS;
                selectFirstSupportedFace();
                return true;
            }
        }

        for (Face face : Face.values()) {
            if (!supports(face)) continue;
            if (!QuantumWidgets.hit(mouseX, mouseY, tileX(face), tileY(face), TILE, TILE)) continue;
            if (button == 1) {
                setMode(face, mode(face).next());
            } else {
                selected = face;
            }
            return true;
        }

        for (SideMode mode : SideMode.values()) {
            if (QuantumWidgets.hit(mouseX, mouseY, modeChipX(mode), modeChipY(),
                    MODE_CHIP_WIDTH - 2, CHIP_HEIGHT)) {
                setMode(selected, mode);
                return true;
            }
        }

        if (QuantumWidgets.hit(mouseX, mouseY, paneX() + 4, autoChipY(), AUTO_CHIP_WIDTH, CHIP_HEIGHT)) {
            int id = id(selected);
            if (items()) autoItemInputs[id] = !autoItemInputs[id];
            else autoFluidInputs[id] = !autoFluidInputs[id];
            return true;
        }

        if (QuantumWidgets.hit(mouseX, mouseY, paneX() + 60, autoChipY(), AUTO_CHIP_WIDTH, CHIP_HEIGHT)) {
            int id = id(selected);
            if (items()) autoItemOutputs[id] = !autoItemOutputs[id];
            else autoFluidOutputs[id] = !autoFluidOutputs[id];
            return true;
        }

        if (showSlotMasks) {
            int bits = maskBits();
            int inputBit = QuantumWidgets.maskBitAt(inputGridX(), maskGridY(), bits, mouseX, mouseY);
            if (inputBit >= 0) {
                int id = id(selected);
                if (items()) itemInputMasks[id] ^= 1 << inputBit;
                else fluidInputMasks[id] ^= 1 << inputBit;
                return true;
            }
            int outputBit = QuantumWidgets.maskBitAt(outputGridX(), maskGridY(), bits, mouseX, mouseY);
            if (outputBit >= 0) {
                int id = id(selected);
                if (items()) itemOutputMasks[id] ^= 1 << outputBit;
                else fluidOutputMasks[id] ^= 1 << outputBit;
                return true;
            }
        }

        return true;
    }

    private void setMode(Face face, SideMode mode) {
        int id = id(face);
        if (items()) itemModes[id] = mode;
        else fluidModes[id] = mode;
    }

    @Override
    public boolean keyPressed(int keyCode) {
        if (keyCode == 256) closed = true;
        return true;
    }
}

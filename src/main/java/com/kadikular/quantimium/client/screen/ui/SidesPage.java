package com.kadikular.quantimium.client.screen.ui;

import com.kadikular.quantimium.block.entity.simulation.SideAutomationProfile;
import com.kadikular.quantimium.block.entity.simulation.SideConfig;
import com.kadikular.quantimium.block.entity.simulation.SideMode;
import com.kadikular.quantimium.block.entity.simulation.SimulatorFluidTanks;
import com.kadikular.quantimium.network.UpdateSideConfigPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The Sides page. The six faces are an unfolded cube around the front, named from the block's own facing,
 * coloured by what each lets through. Click one to pick it; right-click to step it on (Off, In, Out, Both),
 * shift-right-click to step back. The picked face shows below, with what it touches and whether it pulls from
 * or pushes into it. Machines
 * with input slots also choose, per face, which slots it may fill or empty. Each change is sent as it's made.
 */
public final class SidesPage extends Page {

    private static final int KIND_X = 6;
    private static final int KIND_Y = 20;
    private static final int KIND_SIZE = 18;

    private static final int NET_X = 30;
    private static final int NET_Y = 20;
    private static final int TILE = 18;
    private static final int TILE_STEP = 19;

    private static final int FACE_Y = 84;
    private static final int AUTO_Y = 96;
    private static final int AUTO_WIDTH = 48;
    private static final int AUTO_HEIGHT = 16;

    private static final int MASK_X = 118;
    private static final int INSERT_Y = 30;
    private static final int EXTRACT_Y = 86;

    private static final int LEGEND_Y = 150;

    private enum Kind { ITEMS, FLUIDS }

    /** A face as the player sees it, and where it sits in the unfolded net. */
    private enum Face {
        TOP("top", 1, 0),
        LEFT("left", 0, 1),
        FRONT("front", 1, 1),
        RIGHT("right", 2, 1),
        BACK("back", 3, 1),
        BOTTOM("bottom", 1, 2);

        private final String key;
        private final int column;
        private final int row;

        Face(String key, int column, int row) {
            this.key = key;
            this.column = column;
            this.row = row;
        }

        Component label() {
            return Component.translatable("gui.quantimium.sides.face." + key);
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
    private final boolean slotMasks;
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

    private Kind kind = Kind.ITEMS;
    private Face selected = Face.FRONT;

    public SidesPage(Supplier<Rect2i> panel, Rail rail, BlockPos pos, List<SideConfig> configs, SideAutomationProfile profile, boolean slotMasks) {
        super(panel, rail, Component.translatable("gui.quantimium.sides"));
        this.pos = pos;
        this.facing = readFacing(pos);
        this.profile = profile;
        this.slotMasks = slotMasks;
        if (!profile.hasItems() && profile.hasFluids()) kind = Kind.FLUIDS;
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
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        // Blocks that face any way (laser blocks): named from their facing when it's level.
        if (state.hasProperty(BlockStateProperties.FACING) && state.getValue(BlockStateProperties.FACING).getAxis().isHorizontal()) {
            return state.getValue(BlockStateProperties.FACING);
        }
        return Direction.NORTH;
    }

    private void send() {
        List<SideConfig> configs = new ArrayList<>(6);
        for (Direction side : Direction.values()) {
            int id = side.get3DDataValue();
            configs.add(new SideConfig(side, itemModes[id], itemInputMasks[id], itemOutputMasks[id],
                    autoItemInputs[id], autoItemOutputs[id],
                    fluidModes[id], fluidInputMasks[id], fluidOutputMasks[id],
                    autoFluidInputs[id], autoFluidOutputs[id]));
        }
        ClientPacketDistributor.sendToServer(new UpdateSideConfigPayload(pos, configs));
    }

    private boolean items() {
        return kind == Kind.ITEMS;
    }

    private boolean bothKinds() {
        return profile.hasItems() && profile.hasFluids();
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

    private void setMode(Face face, SideMode mode) {
        if (items()) itemModes[id(face)] = mode;
        else fluidModes[id(face)] = mode;
    }

    private boolean pulls(Face face) {
        return items() ? autoItemInputs[id(face)] : autoFluidInputs[id(face)];
    }

    private boolean pushes(Face face) {
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

    private int kindY(Kind kind) {
        return top + KIND_Y + kind.ordinal() * (KIND_SIZE + 2);
    }

    private int pullX() {
        return left + 6;
    }

    private int pushX() {
        return pullX() + AUTO_WIDTH + 4;
    }

    @Override
    protected void renderPage(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        List<Component> tooltip = null;

        if (bothKinds()) {
            for (Kind each : Kind.values()) {
                int x = left + KIND_X;
                int y = kindY(each);
                boolean hovered = QuantumWidgets.hit(mouseX, mouseY, x, y, KIND_SIZE, KIND_SIZE);
                graphics.fill(x, y, x + KIND_SIZE, y + KIND_SIZE, each == kind ? rail.litFill() : rail.fill());
                QuantumWidgets.outline(graphics, x, y, KIND_SIZE, KIND_SIZE, hovered ? rail.hoverEdge() : each == kind ? rail.litEdge() : rail.edge());
                graphics.item(new ItemStack(each == Kind.ITEMS ? Items.CHEST : Items.WATER_BUCKET), x + 1, y + 1);
                if (hovered) tooltip = List.of(Component.translatable(each == Kind.ITEMS ? "gui.quantimium.sides.items" : "gui.quantimium.sides.fluids"));
            }
        }

        for (Face face : Face.values()) {
            if (!supports(face)) continue;
            drawTile(graphics, font, face);
            if (QuantumWidgets.hit(mouseX, mouseY, tileX(face), tileY(face), TILE, TILE)) tooltip = faceTooltip(face);
        }

        // The face last clicked: what it touches, and its two toggles.
        Component name = selected.label().copy().withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" · " + neighbourName(selected.world(facing))).withStyle(ChatFormatting.GRAY));
        int room = (slotMasks ? MASK_X - 6 : width - 6) - 6;
        graphics.text(font, font.substrByWidth(name, room).getString(), left + 6, top + FACE_Y, rail.text(), false);
        SideMode mode = mode(selected);
        tooltip = autoChip(graphics, font, "gui.quantimium.sides.pull", pullX(), pulls(selected), mode.allowsInput(),
                QuantumUiColours.CHIP_IN, mouseX, mouseY, tooltip);
        tooltip = autoChip(graphics, font, "gui.quantimium.sides.push", pushX(), pushes(selected), mode.allowsOutput(),
                QuantumUiColours.CHIP_OUT, mouseX, mouseY, tooltip);

        if (slotMasks) {
            int bits = maskBits();
            int x = left + MASK_X;
            graphics.text(font, Component.translatable("gui.quantimium.sides.mode.input"), x, top + INSERT_Y - 10, rail.textDim(), false);
            graphics.text(font, Component.translatable("gui.quantimium.sides.mode.output"), x, top + EXTRACT_Y - 10, rail.textDim(), false);
            QuantumWidgets.maskGrid(graphics, x, top + INSERT_Y, inputMask(selected), bits, QuantumUiColours.CHIP_IN);
            QuantumWidgets.maskGrid(graphics, x, top + EXTRACT_Y, outputMask(selected), bits, QuantumUiColours.CHIP_OUT);
            int bit = QuantumWidgets.maskBitAt(x, top + INSERT_Y, bits, mouseX, mouseY);
            if (bit < 0) bit = QuantumWidgets.maskBitAt(x, top + EXTRACT_Y, bits, mouseX, mouseY);
            if (bit >= 0) {
                tooltip = List.of(Component.translatable(items() ? "gui.quantimium.sides.slot" : "gui.quantimium.sides.tank", bit + 1));
            }
        }

        // Key to the colours.
        int x = left + 6;
        for (SideMode each : SideMode.values()) {
            graphics.fill(x, top + LEGEND_Y, x + 6, top + LEGEND_Y + 6, QuantumUiColours.modeFill(each));
            Component word = modeLabel(each);
            graphics.text(font, word, x + 9, top + LEGEND_Y - 1, rail.textDim(), false);
            x += 9 + font.width(word) + 10;
        }

        if (tooltip != null) graphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
    }

    private void drawTile(GuiGraphicsExtractor graphics, Font font, Face face) {
        int x = tileX(face);
        int y = tileY(face);
        SideMode mode = mode(face);
        graphics.fill(x, y, x + TILE, y + TILE, QuantumUiColours.modeFill(mode));
        QuantumWidgets.outline(graphics, x, y, TILE, TILE, face == selected ? QuantumUiColours.SELECTION : rail.edge());
        String letter = Component.translatable("gui.quantimium.sides.face." + face.key + ".short").getString();
        graphics.text(font, letter, x + (TILE - font.width(letter)) / 2 + 1, y + 5,
                face == Face.FRONT ? QuantumUiColours.ACCENT : rail.text(), false);
        if (mode.allowsInput() && pulls(face)) graphics.fill(x + 2, y + 2, x + 5, y + 5, QuantumUiColours.CHIP_IN);
        if (mode.allowsOutput() && pushes(face)) graphics.fill(x + TILE - 5, y + 2, x + TILE - 2, y + 5, QuantumUiColours.CHIP_OUT);
    }

    @Nullable
    private List<Component> autoChip(GuiGraphicsExtractor graphics, Font font, String key, int x, boolean on, boolean usable,
                                     int colour, int mouseX, int mouseY, @Nullable List<Component> tooltip) {
        int y = top + AUTO_Y;
        boolean lit = on && usable;
        graphics.fill(x, y, x + AUTO_WIDTH, y + AUTO_HEIGHT, lit ? colour : rail.fill());
        QuantumWidgets.outline(graphics, x, y, AUTO_WIDTH, AUTO_HEIGHT, lit ? QuantumUiColours.SELECTION : rail.edge());
        Component label = Component.translatable(key);
        graphics.text(font, label, x + (AUTO_WIDTH - font.width(label)) / 2, y + 4, usable ? rail.text() : rail.textDim(), false);
        if (!QuantumWidgets.hit(mouseX, mouseY, x, y, AUTO_WIDTH, AUTO_HEIGHT)) return tooltip;
        return List.of(Component.translatable(key + "." + (on ? "on" : "off")),
                Component.translatable(usable ? key + ".tip" : key + ".unused").withStyle(ChatFormatting.GRAY));
    }

    private List<Component> faceTooltip(Face face) {
        return List.of(face.label().copy().append(": ").append(modeLabel(mode(face))),
                Component.translatable("gui.quantimium.sides.face.tip").withStyle(ChatFormatting.GRAY));
    }

    private String neighbourName(Direction side) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return "";
        BlockState neighbour = level.getBlockState(pos.relative(side));
        if (neighbour.isAir()) return Component.translatable("gui.quantimium.sides.nothing").getString();
        return neighbour.getBlock().getName().getString();
    }

    private static Component modeLabel(SideMode mode) {
        return Component.translatable("gui.quantimium.sides.mode." + switch (mode) {
            case DISABLED -> "off";
            case INPUT -> "input";
            case OUTPUT -> "output";
            case BOTH -> "both";
        });
    }

    @Override
    protected void pageClicked(double mouseX, double mouseY, int button) {
        if (bothKinds()) {
            for (Kind each : Kind.values()) {
                if (QuantumWidgets.hit(mouseX, mouseY, left + KIND_X, kindY(each), KIND_SIZE, KIND_SIZE)) {
                    click();
                    kind = each;
                    selectFirstSupportedFace();
                    return;
                }
            }
        }

        for (Face face : Face.values()) {
            if (!supports(face) || !QuantumWidgets.hit(mouseX, mouseY, tileX(face), tileY(face), TILE, TILE)) continue;
            click();
            selected = face;
            if (button == 1) {
                SideMode[] modes = SideMode.values();
                setMode(face, shift() ? modes[(mode(face).ordinal() + modes.length - 1) % modes.length] : mode(face).next());
                send();
            }
            return;
        }

        if (button != 0) return;
        int id = id(selected);
        if (QuantumWidgets.hit(mouseX, mouseY, pullX(), top + AUTO_Y, AUTO_WIDTH, AUTO_HEIGHT) && mode(selected).allowsInput()) {
            click();
            if (items()) autoItemInputs[id] = !autoItemInputs[id];
            else autoFluidInputs[id] = !autoFluidInputs[id];
            send();
            return;
        }
        if (QuantumWidgets.hit(mouseX, mouseY, pushX(), top + AUTO_Y, AUTO_WIDTH, AUTO_HEIGHT) && mode(selected).allowsOutput()) {
            click();
            if (items()) autoItemOutputs[id] = !autoItemOutputs[id];
            else autoFluidOutputs[id] = !autoFluidOutputs[id];
            send();
            return;
        }

        if (slotMasks) {
            int bits = maskBits();
            int inputBit = QuantumWidgets.maskBitAt(left + MASK_X, top + INSERT_Y, bits, mouseX, mouseY);
            if (inputBit >= 0) {
                click();
                if (items()) itemInputMasks[id] ^= 1 << inputBit;
                else fluidInputMasks[id] ^= 1 << inputBit;
                send();
                return;
            }
            int outputBit = QuantumWidgets.maskBitAt(left + MASK_X, top + EXTRACT_Y, bits, mouseX, mouseY);
            if (outputBit >= 0) {
                click();
                if (items()) itemOutputMasks[id] ^= 1 << outputBit;
                else fluidOutputMasks[id] ^= 1 << outputBit;
                send();
            }
        }
    }
}

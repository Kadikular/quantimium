package com.kadikular.quantimium.client.screen;

import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.input.MouseButtonEvent;
import com.kadikular.quantimium.block.entity.simulation.FluidMapping;
import com.kadikular.quantimium.block.entity.simulation.MachineSlotInfo;
import com.kadikular.quantimium.block.entity.simulation.MachineTankInfo;
import com.kadikular.quantimium.block.entity.simulation.SimulatorFluidTanks;
import com.kadikular.quantimium.block.entity.simulation.SlotMapping;
import com.kadikular.quantimium.client.screen.ui.QuantumOverlay;
import com.kadikular.quantimium.client.screen.ui.QuantumUiColours;
import com.kadikular.quantimium.client.screen.ui.QuantumWidgets;
import com.kadikular.quantimium.network.RequestSlotConfigPayload;
import com.kadikular.quantimium.network.UpdateSlotMappingPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Modal for binding simulator item slots and fluid tanks onto the contained machine.
 */
public class SlotConfigScreen implements QuantumOverlay {

    private static final int PANEL_WIDTH = 238;
    private static final int PANEL_HEIGHT = 237;
    /** Tall enough for a whole item icon, which the machine slot's contents are shown as. */
    private static final int ROW_HEIGHT = 18;
    private static final int ROWS_TOP = 42;

    private static final int OPTION_AUTO = 0;
    private static final int OPTION_LOCKED = 1;
    private static final int OPTION_MACHINE_BASE = 2;

    private enum Tab { ITEMS, FLUIDS }

    private final BlockPos pos;
    private final List<MachineSlotInfo> machineSlots;
    private final List<MachineTankInfo> machineTanks;
    private final int[] itemOptions;
    private final int[] fluidOptions;
    private final List<Button> buttons = new ArrayList<>();
    private Tab tab = Tab.ITEMS;

    private int left;
    private int top;
    private boolean closed;

    public SlotConfigScreen(BlockPos pos, List<MachineSlotInfo> machineSlots, List<SlotMapping> mappings,
                            List<MachineTankInfo> machineTanks, List<FluidMapping> fluidMappings) {
        this.pos = pos;
        this.machineSlots = machineSlots;
        this.machineTanks = machineTanks;
        this.itemOptions = new int[9];
        this.fluidOptions = new int[SimulatorFluidTanks.INPUT_TANKS];
        for (int gridSlot = 0; gridSlot < itemOptions.length; gridSlot++) {
            itemOptions[gridSlot] = gridSlot < mappings.size() ? optionForItem(mappings.get(gridSlot)) : OPTION_AUTO;
        }
        for (int tank = 0; tank < fluidOptions.length; tank++) {
            fluidOptions[tank] = tank < fluidMappings.size() ? optionForFluid(fluidMappings.get(tank)) : OPTION_AUTO;
        }
    }

    private int optionForItem(SlotMapping mapping) {
        if (mapping.locked()) return OPTION_LOCKED;
        if (!mapping.isBound()) return OPTION_AUTO;
        int machineSlot = mapping.targetSlotIndex();
        for (int i = 0; i < machineSlots.size(); i++) {
            if (machineSlots.get(i).slotIndex() == machineSlot) return OPTION_MACHINE_BASE + i;
        }
        return OPTION_AUTO;
    }

    private int optionForFluid(FluidMapping mapping) {
        if (mapping.locked()) return OPTION_LOCKED;
        if (!mapping.isBound()) return OPTION_AUTO;
        int machineTank = mapping.targetTankIndex();
        for (int i = 0; i < machineTanks.size(); i++) {
            if (machineTanks.get(i).tankIndex() == machineTank) return OPTION_MACHINE_BASE + i;
        }
        return OPTION_AUTO;
    }

    @Override
    public net.minecraft.client.renderer.Rect2i bounds() {
        return new net.minecraft.client.renderer.Rect2i(left, top, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void layout(int screenWidth, int screenHeight) {
        this.left = (screenWidth - PANEL_WIDTH) / 2;
        this.top = (screenHeight - PANEL_HEIGHT) / 2;

        buttons.clear();
        int footerY = top + PANEL_HEIGHT - 24;
        buttons.add(Button.builder(Component.translatable("gui.quantimium.slot_config.auto_detect"),
                        b -> ClientPacketDistributor.sendToServer(new RequestSlotConfigPayload(pos, true)))
                .bounds(left + 8, footerY, 88, 18).build());
        buttons.add(Button.builder(Component.translatable("gui.quantimium.slot_config.apply"), b -> apply())
                .bounds(left + 102, footerY, 60, 18).build());
        buttons.add(Button.builder(Component.translatable("gui.quantimium.slot_config.cancel"), b -> closed = true)
                .bounds(left + 168, footerY, 62, 18).build());
    }

    private void apply() {
        List<SlotMapping> mappings = new ArrayList<>(itemOptions.length);
        for (int gridSlot = 0; gridSlot < itemOptions.length; gridSlot++) {
            mappings.add(toItemMapping(gridSlot, itemOptions[gridSlot]));
        }
        List<FluidMapping> fluids = new ArrayList<>(fluidOptions.length);
        for (int tank = 0; tank < fluidOptions.length; tank++) {
            fluids.add(toFluidMapping(tank, fluidOptions[tank]));
        }
        ClientPacketDistributor.sendToServer(new UpdateSlotMappingPayload(pos, mappings, fluids));
        closed = true;
    }

    private SlotMapping toItemMapping(int gridSlot, int option) {
        if (option == OPTION_LOCKED) return SlotMapping.locked(gridSlot);
        if (option == OPTION_AUTO) return SlotMapping.auto(gridSlot);
        MachineSlotInfo info = machineSlots.get(option - OPTION_MACHINE_BASE);
        return SlotMapping.bound(gridSlot, info.slotIndex(), info.face());
    }

    private FluidMapping toFluidMapping(int simTank, int option) {
        if (option == OPTION_LOCKED) return FluidMapping.locked(simTank);
        if (option == OPTION_AUTO) return FluidMapping.auto(simTank);
        MachineTankInfo info = machineTanks.get(option - OPTION_MACHINE_BASE);
        return FluidMapping.bound(simTank, info.tankIndex(), info.face());
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float partialTick) {
        List<Component> tooltip = null;

        graphics.nextStratum();

        QuantumWidgets.backdrop(graphics);
        QuantumWidgets.framedPanel(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT);

        QuantumWidgets.title(graphics, font, Component.translatable("gui.quantimium.slot_config.title"),
                left + 8, top + 8);
        QuantumWidgets.tab(graphics, font, "Items", left + 8, top + 22, tab == Tab.ITEMS);
        QuantumWidgets.tab(graphics, font, "Fluids", left + 64, top + 22, tab == Tab.FLUIDS);

        boolean items = tab == Tab.ITEMS;
        int[] options = items ? itemOptions : fluidOptions;
        int optionCount = OPTION_MACHINE_BASE + (items ? machineSlots.size() : machineTanks.size());

        if (items && machineSlots.isEmpty()) {
            graphics.text(font, Component.translatable("gui.quantimium.slot_config.no_machine"),
                    left + 8, top + ROWS_TOP, QuantumUiColours.TEXT_WARN, false);
        }
        if (!items && machineTanks.isEmpty()) {
            graphics.text(font, "No fluid tanks on this machine", left + 8, top + ROWS_TOP,
                    QuantumUiColours.TEXT_WARN, false);
        }

        for (int row = 0; row < options.length; row++) {
            int rowY = top + ROWS_TOP + row * ROW_HEIGHT;
            boolean hovered = isOverRow(mouseX, mouseY, row, options.length);
            int background = options[row] == OPTION_LOCKED
                    ? QuantumUiColours.ROW_LOCKED
                    : (hovered ? QuantumUiColours.ROW_HOVER : QuantumUiColours.ROW);
            graphics.fill(left + 6, rowY, left + PANEL_WIDTH - 6, rowY + ROW_HEIGHT - 2, background);

            // "Grid 3 → [what the machine slot holds] Slot 1 [IN]": the icon belongs to the target.
            graphics.text(font, (items ? "Grid " : "Tank ") + (row + 1), left + 10, rowY + 4,
                    QuantumUiColours.TEXT_DIM, false);
            graphics.text(font, "→", left + 48, rowY + 4, QuantumUiColours.TEXT_MUTED, false);
            if (items) {
                ItemStack preview = previewStack(options[row]);
                if (!preview.isEmpty()) {
                    graphics.item(preview, left + 58, rowY);
                    graphics.itemDecorations(font, preview, left + 58, rowY);
                }
            }
            graphics.text(font, describeOption(items, options[row]), left + 78, rowY + 4,
                    QuantumUiColours.TEXT, false);

            if (hovered) tooltip = rowTooltip(items, row, options[row]);
        }

        for (Button button : buttons) {
            button.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }

        if (tooltip != null) {
            graphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
        }
    }

    private List<Component> rowTooltip(boolean items, int row, int option) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(items ? "Grid slot " + (row + 1) : "Input tank " + (row + 1)));
        if (option >= OPTION_MACHINE_BASE) {
            if (items) {
                MachineSlotInfo info = machineSlots.get(option - OPTION_MACHINE_BASE);
                lines.add(Component.literal("§7" + info.faceLabel() + " · " + info.roleLabel() + " · max " + info.limit()));
            } else {
                MachineTankInfo info = machineTanks.get(option - OPTION_MACHINE_BASE);
                lines.add(Component.literal("§7Tank " + (info.tankIndex() + 1) + " · cap " + info.capacity() + " mB"));
            }
        }
        lines.add(Component.translatable("gui.quantimium.slot_config.cycle_hint").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private ItemStack previewStack(int option) {
        if (option < OPTION_MACHINE_BASE) return ItemStack.EMPTY;
        return machineSlots.get(option - OPTION_MACHINE_BASE).contents();
    }

    private String describeOption(boolean items, int option) {
        if (option == OPTION_LOCKED) return "§cLocked";
        if (option == OPTION_AUTO) return "§eAuto route";
        if (items) {
            MachineSlotInfo info = machineSlots.get(option - OPTION_MACHINE_BASE);
            String colour = info.producesOutput() ? "§6" : "§a";
            return colour + "Slot " + (info.slotIndex() + 1) + " §7[" + info.roleLabel() + "]";
        }
        MachineTankInfo info = machineTanks.get(option - OPTION_MACHINE_BASE);
        FluidStack contents = info.contents();
        String colour = info.producesOutput() ? "§6" : "§a";
        String name = contents.isEmpty() ? "empty" : contents.getHoverName().getString();
        return colour + "Tank " + (info.tankIndex() + 1) + " §7[" + name + "]";
    }

    private boolean isOverRow(int mouseX, int mouseY, int row, int rowCount) {
        if (row < 0 || row >= rowCount) return false;
        int rowY = top + ROWS_TOP + row * ROW_HEIGHT;
        return mouseX >= left + 6 && mouseX < left + PANEL_WIDTH - 6 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Button widget : buttons) {
            if (widget.mouseClicked(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, 0)), false)) return true;
        }

        if (mouseY >= top + 22 && mouseY < top + 36) {
            if (mouseX >= left + 8 && mouseX < left + 58) {
                tab = Tab.ITEMS;
                return true;
            }
            if (mouseX >= left + 64 && mouseX < left + 114) {
                tab = Tab.FLUIDS;
                return true;
            }
        }

        boolean items = tab == Tab.ITEMS;
        int[] options = items ? itemOptions : fluidOptions;
        int optionCount = OPTION_MACHINE_BASE + (items ? machineSlots.size() : machineTanks.size());
        if (optionCount < 1) optionCount = OPTION_MACHINE_BASE;
        for (int row = 0; row < options.length; row++) {
            if (!isOverRow((int) mouseX, (int) mouseY, row, options.length)) continue;
            int step = button == 1 ? -1 : 1;
            options[row] = Math.floorMod(options[row] + step, optionCount);
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode) {
        if (keyCode == 256) {
            closed = true;
            return true;
        }
        return true;
    }
}

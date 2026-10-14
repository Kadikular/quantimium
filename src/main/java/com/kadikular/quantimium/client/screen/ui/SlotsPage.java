package com.kadikular.quantimium.client.screen.ui;

import com.kadikular.quantimium.block.entity.simulation.FluidMapping;
import com.kadikular.quantimium.block.entity.simulation.MachineSlotInfo;
import com.kadikular.quantimium.block.entity.simulation.MachineTankInfo;
import com.kadikular.quantimium.block.entity.simulation.SimulatorFluidTanks;
import com.kadikular.quantimium.block.entity.simulation.SlotMapping;
import com.kadikular.quantimium.network.UpdateSlotMappingPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The Simulator's slot binding page: a row for each of its input slots (or tanks), saying which of the
 * contained machine's slots it feeds. Click a row to step it on, right-click to step back, through Auto,
 * Locked and each of the machine's slots. Each change is sent as it's made; auto-detect is on the left rail.
 */
public final class SlotsPage extends Page {

    private static final int KIND_X = 6;
    private static final int KIND_Y = 20;
    private static final int KIND_SIZE = 18;
    private static final int ROWS_X = 28;
    private static final int ROWS_Y = 20;
    private static final int ROW_HEIGHT = 16;
    private static final int ROW_STEP = 17;

    private static final int OPTION_AUTO = 0;
    private static final int OPTION_LOCKED = 1;
    private static final int OPTION_MACHINE_BASE = 2;

    private static final int GREEN = 0xFF5BE38A;
    private static final int ORANGE = 0xFFF0A040;
    private static final int RED = 0xFFE05A50;
    private static final int LOCKED_ROW = 0x40B03A33;

    private final BlockPos pos;
    private final List<MachineSlotInfo> machineSlots;
    private final List<MachineTankInfo> machineTanks;
    private final int[] itemOptions = new int[9];
    private final int[] fluidOptions = new int[SimulatorFluidTanks.INPUT_TANKS];
    private boolean fluids;

    public SlotsPage(Supplier<Rect2i> panel, Rail rail, BlockPos pos, List<MachineSlotInfo> machineSlots, List<SlotMapping> mappings,
                     List<MachineTankInfo> machineTanks, List<FluidMapping> fluidMappings) {
        super(panel, rail, Component.translatable("gui.quantimium.slot_config.title"));
        this.pos = pos;
        this.machineSlots = machineSlots;
        this.machineTanks = machineTanks;
        for (int slot = 0; slot < itemOptions.length; slot++) {
            itemOptions[slot] = slot < mappings.size() ? optionForItem(mappings.get(slot)) : OPTION_AUTO;
        }
        for (int tank = 0; tank < fluidOptions.length; tank++) {
            fluidOptions[tank] = tank < fluidMappings.size() ? optionForFluid(fluidMappings.get(tank)) : OPTION_AUTO;
        }
    }

    private int optionForItem(SlotMapping mapping) {
        if (mapping.locked()) return OPTION_LOCKED;
        if (!mapping.isBound()) return OPTION_AUTO;
        for (int i = 0; i < machineSlots.size(); i++) {
            if (machineSlots.get(i).slotIndex() == mapping.targetSlotIndex()) return OPTION_MACHINE_BASE + i;
        }
        return OPTION_AUTO;
    }

    private int optionForFluid(FluidMapping mapping) {
        if (mapping.locked()) return OPTION_LOCKED;
        if (!mapping.isBound()) return OPTION_AUTO;
        for (int i = 0; i < machineTanks.size(); i++) {
            if (machineTanks.get(i).tankIndex() == mapping.targetTankIndex()) return OPTION_MACHINE_BASE + i;
        }
        return OPTION_AUTO;
    }

    private void send() {
        List<SlotMapping> items = new ArrayList<>(itemOptions.length);
        for (int slot = 0; slot < itemOptions.length; slot++) {
            int option = itemOptions[slot];
            if (option == OPTION_LOCKED) items.add(SlotMapping.locked(slot));
            else if (option == OPTION_AUTO) items.add(SlotMapping.auto(slot));
            else {
                MachineSlotInfo info = machineSlots.get(option - OPTION_MACHINE_BASE);
                items.add(SlotMapping.bound(slot, info.slotIndex(), info.face()));
            }
        }
        List<FluidMapping> tanks = new ArrayList<>(fluidOptions.length);
        for (int tank = 0; tank < fluidOptions.length; tank++) {
            int option = fluidOptions[tank];
            if (option == OPTION_LOCKED) tanks.add(FluidMapping.locked(tank));
            else if (option == OPTION_AUTO) tanks.add(FluidMapping.auto(tank));
            else {
                MachineTankInfo info = machineTanks.get(option - OPTION_MACHINE_BASE);
                tanks.add(FluidMapping.bound(tank, info.tankIndex(), info.face()));
            }
        }
        ClientPacketDistributor.sendToServer(new UpdateSlotMappingPayload(pos, items, tanks));
    }

    private int[] options() {
        return fluids ? fluidOptions : itemOptions;
    }

    private int optionCount() {
        return OPTION_MACHINE_BASE + (fluids ? machineTanks.size() : machineSlots.size());
    }

    private int kindY(boolean fluid) {
        return top + KIND_Y + (fluid ? KIND_SIZE + 2 : 0);
    }

    private int rowY(int row) {
        return top + ROWS_Y + row * ROW_STEP;
    }

    private boolean overRow(double mouseX, double mouseY, int row) {
        return QuantumWidgets.hit(mouseX, mouseY, left + ROWS_X, rowY(row), width - ROWS_X - 6, ROW_HEIGHT);
    }

    @Override
    protected void renderPage(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        List<Component> tooltip = null;

        for (boolean fluid : new boolean[] {false, true}) {
            int x = left + KIND_X;
            int y = kindY(fluid);
            boolean hovered = QuantumWidgets.hit(mouseX, mouseY, x, y, KIND_SIZE, KIND_SIZE);
            rail.button(graphics, x, y, KIND_SIZE, KIND_SIZE, fluid == fluids, hovered);
            graphics.item(new ItemStack(fluid ? Items.WATER_BUCKET : Items.CHEST), x + 1, y + 1);
            if (hovered) tooltip = List.of(Component.translatable(fluid ? "gui.quantimium.sides.fluids" : "gui.quantimium.sides.items"));
        }

        boolean none = fluids ? machineTanks.isEmpty() : machineSlots.isEmpty();
        if (none) {
            graphics.textWithWordWrap(font, Component.translatable(fluids ? "gui.quantimium.slot_config.no_tanks" : "gui.quantimium.slot_config.no_machine"),
                    left + ROWS_X, top + ROWS_Y + 4, width - ROWS_X - 6, rail.textDim());
        } else {
            int[] options = options();
            for (int row = 0; row < options.length; row++) {
                int y = rowY(row);
                boolean hovered = overRow(mouseX, mouseY, row);
                rail.button(graphics, left + ROWS_X, y, width - ROWS_X - 6, ROW_HEIGHT, false, hovered);
                if (options[row] == OPTION_LOCKED) graphics.fill(left + ROWS_X + 1, y + 1, left + width - 7, y + ROW_HEIGHT - 1, LOCKED_ROW);
                graphics.text(font, Integer.toString(row + 1), left + ROWS_X + 4, y + 4, rail.textDim(), false);
                graphics.text(font, "→", left + ROWS_X + 14, y + 4, rail.textDim(), false);
                ItemStack preview = !fluids && options[row] >= OPTION_MACHINE_BASE
                        ? machineSlots.get(options[row] - OPTION_MACHINE_BASE).contents() : ItemStack.EMPTY;
                int textX = left + ROWS_X + 26;
                if (!preview.isEmpty()) {
                    graphics.item(preview, textX, y);
                    textX += 18;
                }
                graphics.text(font, describe(options[row]), textX, y + 4, colour(options[row]), false);
                if (hovered) tooltip = rowTooltip(row, options[row]);
            }
        }

        if (tooltip != null) graphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
    }

    /** "Auto", "Locked", "Slot 2 · In", "Tank 1 · Water". */
    private Component describe(int option) {
        if (option == OPTION_LOCKED) return Component.translatable("gui.quantimium.slot_config.locked");
        if (option == OPTION_AUTO) return Component.translatable("gui.quantimium.slot_config.auto");
        if (!fluids) {
            MachineSlotInfo info = machineSlots.get(option - OPTION_MACHINE_BASE);
            return Component.translatable("gui.quantimium.slot_config.slot", info.slotIndex() + 1, info.roleLabel());
        }
        MachineTankInfo info = machineTanks.get(option - OPTION_MACHINE_BASE);
        FluidStack contents = info.contents();
        return Component.translatable("gui.quantimium.slot_config.tank", info.tankIndex() + 1,
                contents.isEmpty() ? Component.translatable("gui.quantimium.slot_config.empty") : contents.getHoverName());
    }

    private int colour(int option) {
        if (option == OPTION_LOCKED) return RED;
        if (option == OPTION_AUTO) return rail.text();
        boolean output = fluids ? machineTanks.get(option - OPTION_MACHINE_BASE).producesOutput()
                : machineSlots.get(option - OPTION_MACHINE_BASE).producesOutput();
        return output ? ORANGE : GREEN;
    }

    private List<Component> rowTooltip(int row, int option) {
        MutableComponent name = Component.translatable(fluids ? "gui.quantimium.slot_config.row.tank" : "gui.quantimium.slot_config.row", row + 1);
        List<Component> lines = new ArrayList<>();
        lines.add(name.append(": ").append(describe(option)));
        if (option >= OPTION_MACHINE_BASE && !fluids) {
            MachineSlotInfo info = machineSlots.get(option - OPTION_MACHINE_BASE);
            lines.add(Component.translatable("gui.quantimium.slot_config.detail", info.faceLabel(), info.limit()).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("gui.quantimium.tab.cycle").withStyle(ChatFormatting.GRAY));
        return lines;
    }

    @Override
    protected void pageClicked(double mouseX, double mouseY, int button) {
        for (boolean fluid : new boolean[] {false, true}) {
            if (button == 0 && QuantumWidgets.hit(mouseX, mouseY, left + KIND_X, kindY(fluid), KIND_SIZE, KIND_SIZE)) {
                click();
                fluids = fluid;
                return;
            }
        }
        if (fluids ? machineTanks.isEmpty() : machineSlots.isEmpty()) return;
        int[] options = options();
        for (int row = 0; row < options.length; row++) {
            if (!overRow(mouseX, mouseY, row) || (button != 0 && button != 1)) continue;
            click();
            options[row] = Math.floorMod(options[row] + (button == 1 ? -1 : 1), optionCount());
            send();
            return;
        }
    }
}

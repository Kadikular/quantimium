// Path: src/main/java/com/kadikular/quantimium/client/screen/QuantumSimulatorScreen.java
package com.kadikular.quantimium.client.screen;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.client.screen.ui.QuantumWidgets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.flux.BandGate;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.block.entity.simulation.SimulatorFluidTanks;
import com.kadikular.quantimium.client.screen.ui.QuantumUiColours;
import com.kadikular.quantimium.menu.QuantumSimulatorMenu;
import com.kadikular.quantimium.network.OpenSlotConfigPayload;
import com.kadikular.quantimium.network.OpenSideConfigPayload;
import com.kadikular.quantimium.network.RequestSlotConfigPayload;
import com.kadikular.quantimium.network.RequestSideConfigPayload;
import com.kadikular.quantimium.network.SetBatchSizePayload;
import com.kadikular.quantimium.network.ToggleEngagePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class QuantumSimulatorScreen extends QuantumMachineScreen<QuantumSimulatorMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/quantum_simulator.png");

    private static final int LOCKED_SLOT_OVERLAY = 0xC0201820;
    private static final int ARROW_IDLE = 0xFF444444;
    /** Fluid tanks sit under the grids, one under each column (tools/gui_panels.py). */
    private static final int TANK_Y = 76;
    private static final int TANK_WIDTH = 16;
    private static final int TANK_HEIGHT = 12;
    private static final int INPUT_TANK_X = 12;
    private static final int OUTPUT_TANK_X = 116;
    public static final int PANEL_HEIGHT = 186;

    private Button engageButton;

    public QuantumSimulatorScreen(QuantumSimulatorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, DEFAULT_IMAGE_WIDTH, PANEL_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        this.engageButton = Button.builder(Component.literal("FIELD"), button ->
                        ClientPacketDistributor.sendToServer(new ToggleEngagePayload(menu.getBlockEntity().getBlockPos())))
                .bounds(leftPos + 72, topPos + 56, 32, 14).build();
        addRenderableWidget(engageButton);
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(
                Tab.of("\u2699", () -> ClientPacketDistributor.sendToServer(
                                new RequestSlotConfigPayload(menu.getBlockEntity().getBlockPos(), false)),
                        List.of(Component.literal("§6Configure Slots"),
                                Component.literal("§7Bind each input slot to a machine slot."),
                                Component.literal("§8Requires an engaged containment field.")))
                        .enabled(menu::isEngaged)
                        .icon(new ItemStack(Items.COMPARATOR)),
                Tab.of("S", () -> ClientPacketDistributor.sendToServer(
                                new RequestSideConfigPayload(menu.getBlockEntity().getBlockPos())),
                        List.of(Component.literal("§6Configure Sides"),
                                Component.literal("§7Set input/output faces and auto transfer.")))
                        .icon(new ItemStack(Items.HOPPER)),
                Tab.of(() -> Component.literal(menu.getBatchSize() + "x"),
                        () -> {
                            int current = menu.getBatchSize();
                            int next = current >= 32 ? 1 : current * 2;
                            ClientPacketDistributor.sendToServer(
                                    new SetBatchSizePayload(menu.getBlockEntity().getBlockPos(), next));
                        },
                        this::batchTooltip)
                        .width(28));
    }

    /** The batch chosen, and when the band here holds it back, what it runs at and what it needs. */
    private List<Component> batchTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.quantimium.quantum_simulator.batch_chosen", menu.getBatchSize()).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (menu.getRunningBatch() < menu.getBatchSize()) {
            lines.add(Component.translatable("gui.quantimium.quantum_simulator.batch_running", menu.getRunningBatch()).withStyle(ChatFormatting.GOLD));
            lines.add(BandGate.refusal(bandFor(menu.getBatchSize()), menu.getBand(), menu.getHeading()).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("gui.quantimium.quantum_simulator.batch_cycle").withStyle(ChatFormatting.GRAY));
        return lines;
    }

    /** The lowest band whose largest batch is at least {@code batch}. */
    private static FluxBand bandFor(int batch) {
        for (FluxBand band : FluxBand.values()) {
            if (Config.simulatorMaxBatch(band) >= batch) return band;
        }
        return FluxBand.SINGULARITY;
    }

    /** Called from the server's reply to a slot configuration request. */
    public void openSlotConfig(OpenSlotConfigPayload payload) {
        openOverlay(new SlotConfigScreen(payload.pos(), payload.machineSlots(), payload.mappings(),
                payload.machineTanks(), payload.fluidMappings()));
    }

    public void openSideConfig(OpenSideConfigPayload payload) {
        openOverlay(new SideConfigScreen(payload.pos(), payload.configs(), payload.profile()));
    }

    @Override
    protected Identifier panelTexture() {
        return TEXTURE;
    }

    @Override
    protected int energyStored() {
        return menu.getEnergyStored();
    }

    @Override
    protected int maxEnergyStored() {
        return menu.getMaxEnergyStored();
    }

    @Override
    protected List<Component> energyTooltip() {
        return EnergyBarTooltip.lines(menu.getEnergyStored(), menu.getMaxEnergyStored(),
                menu.getCurrentPowerUse(), menu.getAveragePowerUse(), true,
                menu.getAnomalySurchargePercent(), menu.getAnomalyBand(),
                menu.getPassiveDrainFePerTick());
    }

    @Override
    protected Component statusLabel() {
        return Component.translatable("gui.quantimium.quantum_simulator.status." + statusKey());
    }

    @Override
    protected PanelTheme theme() {
        return PanelTheme.MID;
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        if (!menu.isEngaged()) return PanelTheme.Tone.IDLE;
        return switch (menu.getStatusCode()) {
            case 1, 11 -> PanelTheme.Tone.OK;
            case 2 -> PanelTheme.Tone.IDLE;
            case 6, 9, 13 -> PanelTheme.Tone.WARN;
            default -> PanelTheme.Tone.BAD;
        };
    }

    private String statusKey() {
        if (!menu.isEngaged()) return "offline";
        return switch (menu.getStatusCode()) {
            case 1 -> "working";
            case 2 -> "idle";
            case 4 -> "recipe_format";
            case 5 -> "power";
            case 6 -> "output";
            case 7 -> "unsupported";
            case 8 -> "no_route";
            case 9 -> "low_input";
            case 10 -> "target_unpowered";
            case 11 -> "initialising";
            case 12 -> "no_tick";
            case 13 -> "stalled";
            default -> "recipe";
        };
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        boolean engaged = menu.isEngaged();
        engageButton.setMessage(Component.literal(engaged ? "ON" : "OFF"));

        renderFluidTanks(graphics, leftPos, topPos);
        renderProgressArrow(graphics, leftPos, topPos, engaged);
    }

    private void renderProgressArrow(GuiGraphicsExtractor graphics, int x, int y, boolean engaged) {
        int arrowX = x + 99;
        int arrowY = y + 37;

        graphics.fill(arrowX, arrowY + 5, arrowX + 11, arrowY + 9, ARROW_IDLE);
        graphics.fill(arrowX + 11, arrowY + 2, arrowX + 15, arrowY + 12, ARROW_IDLE);

        int progress = menu.getProgress();
        int maxProgress = menu.getMaxProgress();
        if (maxProgress <= 0 || progress <= 0 || !engaged) return;

        int progressWidth = (int) (15.0 * progress / maxProgress);
        if (progressWidth <= 0) return;
        int stemWidth = Math.min(progressWidth, 11);
        graphics.fill(arrowX, arrowY + 5, arrowX + stemWidth, arrowY + 9, QuantumUiColours.ACCENT);
        if (progressWidth > 11) {
            int headWidth = progressWidth - 11;
            graphics.fill(arrowX + 11, arrowY + 2, arrowX + 11 + headWidth, arrowY + 12, QuantumUiColours.ACCENT);
        }
    }

    private void renderFluidTanks(GuiGraphicsExtractor graphics, int x, int y) {
        SimulatorFluidTanks tanks = menu.getBlockEntity().getFluidTanks();
        for (int i = 0; i < SimulatorFluidTanks.INPUT_TANKS; i++) {
            drawTank(graphics, tanks.getFluid(i), x + INPUT_TANK_X + i * 18, y + TANK_Y);
        }
        for (int i = 0; i < SimulatorFluidTanks.OUTPUT_TANKS; i++) {
            drawTank(graphics, tanks.getFluid(SimulatorFluidTanks.outputIndex(i)),
                    x + OUTPUT_TANK_X + i * 18, y + TANK_Y);
        }
    }

    /** The well is painted on the panel; this only fills it, from the bottom up. */
    private static void drawTank(GuiGraphicsExtractor graphics, FluidStack fluid, int x, int y) {
        if (fluid.isEmpty()) return;
        int filled = Math.max(1, (int) ((long) TANK_HEIGHT * fluid.getAmount() / SimulatorFluidTanks.CAPACITY));
        FluidTintSource tint = Minecraft.getInstance().getModelManager().getFluidStateModelSet()
                .get(fluid.getFluid().defaultFluidState()).fluidTintSource();
        int color = tint == null ? 0xFFFFFFFF : tint.colorAsStack(fluid);
        if ((color & 0xFF000000) == 0) color |= 0xFF000000;
        graphics.fill(x, y + TANK_HEIGHT - filled, x + TANK_WIDTH, y + TANK_HEIGHT, color);
    }

    /** Darkens grid slots that parallel mode has locked, so invalid inserts are obvious. */
    @Override
    protected void renderMachineForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int lockedMask = menu.getLockedMask();
        if (lockedMask == 0) return;

        graphics.nextStratum();
        for (int slot = 0; slot < 9; slot++) {
            if ((lockedMask & (1 << slot)) == 0) continue;
            int slotX = leftPos + 12 + (slot % 3) * 18;
            int slotY = topPos + 18 + (slot / 3) * 18;
            graphics.fill(slotX, slotY, slotX + 16, slotY + 16, LOCKED_SLOT_OVERLAY);
        }
    }

    @Override
    protected void renderMachineTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        SimulatorFluidTanks tanks = menu.getBlockEntity().getFluidTanks();
        for (int i = 0; i < SimulatorFluidTanks.INPUT_TANKS; i++) {
            if (QuantumWidgets.hit(mouseX, mouseY, leftPos + INPUT_TANK_X + i * 18 - 1, topPos + TANK_Y - 1,
                    TANK_WIDTH + 2, TANK_HEIGHT + 2)) {
                graphics.setComponentTooltipForNextFrame(font, fluidTooltip("Input", i, tanks.getFluid(i)), mouseX, mouseY);
                return;
            }
        }
        for (int i = 0; i < SimulatorFluidTanks.OUTPUT_TANKS; i++) {
            if (QuantumWidgets.hit(mouseX, mouseY, leftPos + OUTPUT_TANK_X + i * 18 - 1, topPos + TANK_Y - 1,
                    TANK_WIDTH + 2, TANK_HEIGHT + 2)) {
                graphics.setComponentTooltipForNextFrame(font,
                        fluidTooltip("Output", i, tanks.getFluid(SimulatorFluidTanks.outputIndex(i))), mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    protected List<Component> statusTooltip() {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("gui.quantimium.quantum_simulator.batch", menu.getRunningBatch())
                .withStyle(ChatFormatting.GOLD));
        String key = "gui.quantimium.quantum_simulator.status." + statusKey();
        tooltip.add(Component.translatable(key + ".tip").withStyle(ChatFormatting.WHITE));
        if (I18n.exists(key + ".hint")) {
            tooltip.add(Component.translatable(key + ".hint").withStyle(ChatFormatting.GRAY));
        }
        return tooltip;
    }

    private static List<Component> fluidTooltip(String role, int index, FluidStack fluid) {
        List<Component> tip = new ArrayList<>(2);
        tip.add(Component.literal("§b" + role + " tank " + (index + 1)));
        if (fluid.isEmpty()) {
            tip.add(Component.literal("§7Empty"));
        } else {
            tip.add(Component.literal("§f").append(fluid.getHoverName())
                    .append(Component.literal("§7: §f" + String.format("%,d", fluid.getAmount())
                            + " / " + String.format("%,d", SimulatorFluidTanks.CAPACITY) + " mB")));
        }
        return tip;
    }
}

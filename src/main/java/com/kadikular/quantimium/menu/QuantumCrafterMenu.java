package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.init.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.kadikular.quantimium.util.SlotItemHandler;

public class QuantumCrafterMenu extends AbstractContainerMenu {

    private static final int DATA_COUNT = 18;
    private static final int MENU_CATALYST_SLOT = 9;
    private static final int MACHINE_MENU_SLOTS = 10;
    /** Where the player's inventory starts on the panel (tools/gui_panels.py quantum_crafter). */
    public static final int PLAYER_Y = 152;

    private final QuantumCrafterBlockEntity blockEntity;
    private final ContainerData data;

    public QuantumCrafterMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv,
                (QuantumCrafterBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(DATA_COUNT));
    }

    public QuantumCrafterMenu(int containerId, Inventory playerInv, QuantumCrafterBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.QUANTUM_CRAFTER_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        checkContainerDataCount(data, DATA_COUNT);

        // 3x3 Inputs (0-8)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new SlotItemHandler(blockEntity.getInventory(), col + row * 3, 12 + col * 18, 18 + row * 18));
            }
        }

        // The outputs aren't slots: the screen shows them all in a grid, and takes from them by button.
        // Catalyst (menu slot 9; backing slot follows all 54 outputs)
        this.addSlot(new SlotItemHandler(blockEntity.getInventory(), QuantumCrafterBlockEntity.CATALYST_SLOT, 80, 36));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, PLAYER_Y + 58));
        }

        // Its screen open on the server: the Crafter keeps up with what it draws on, for this player.
        if (!playerInv.player.level().isClientSide()) blockEntity.viewed(true);
        addDataSlots(data);
    }

    public QuantumCrafterBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public boolean isBasic() {
        return blockEntity.isBasic();
    }

    public int getEnergyStored() {
        return (data.get(1) << 16) | (data.get(0) & 0xFFFF);
    }

    public int getMaxEnergyStored() {
        return (data.get(3) << 16) | (data.get(2) & 0xFFFF);
    }

    public int getStatusCode() {
        return data.get(4);
    }

    public boolean isRecipeLocked() {
        return data.get(5) != 0;
    }

    public int getGhostCount() {
        return data.get(6);
    }

    public int getCurrentPowerUse() {
        return (data.get(8) << 16) | (data.get(7) & 0xFFFF);
    }

    public int getAveragePowerUse() {
        return (data.get(10) << 16) | (data.get(9) & 0xFFFF);
    }

    public int getAnomalySurchargePercent() {
        return data.get(11);
    }

    public FluxBand getAnomalyBand() {
        int ordinal = data.get(12);
        FluxBand[] bands = FluxBand.values();
        if (ordinal < 0 || ordinal >= bands.length) return FluxBand.LOW;
        return bands[ordinal];
    }

    public int getCoherenceReserve() {
        return data.get(13);
    }

    public int getCoherenceCapacity() {
        return data.get(14);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stackInSlot = slot.getItem();
        itemstack = stackInSlot.copy();

        if (index < MACHINE_MENU_SLOTS) {
            if (!this.moveItemStackTo(stackInSlot, MACHINE_MENU_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Prefer catalyst if empty, else inputs.
            ItemStack catalyst = blockEntity.getInventory().getStackInSlot(QuantumCrafterBlockEntity.CATALYST_SLOT);
            if (catalyst.isEmpty()) {
                if (!this.moveItemStackTo(stackInSlot, MENU_CATALYST_SLOT,
                        MENU_CATALYST_SLOT + 1, false)) {
                    if (!this.moveItemStackTo(stackInSlot, 0, QuantumCrafterBlockEntity.INPUT_SLOTS, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            } else if (!this.moveItemStackTo(stackInSlot, 0, QuantumCrafterBlockEntity.INPUT_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stackInSlot.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();

        if (stackInSlot.getCount() == itemstack.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stackInSlot);
        return itemstack;
    }

    /** Moves {@code stack} into the player's inventory; what didn't fit. */
    private ItemStack toInventory(ItemStack stack) {
        ItemStack moving = stack.copy();
        moveItemStackTo(moving, MACHINE_MENU_SLOTS, slots.size(), true);
        return moving;
    }

    /**
     * Menu buttons taking what {@code slot} shows: {@code mode * TAKE + slot}, an output slot (0 to 53) or a preview
     * ({@link QuantumCrafterBlockEntity#GHOST_BASE} on). A stack onto the
     * cursor, half a stack, one, or a stack into the inventory: what's in its outputs first, from every output holding
     * it, and the rest made, by whichever preview of it can make the most.
     */
    public static final int TAKE = 100_000, BATCH = 1, HALF = 2, ONE = 3, TO_INVENTORY = 4;

    /** A bucket on the cursor clicked on an input tank (this + its index). */
    public static final int BUTTON_TANK = 50_000;
    /**
     * A fluid in the grid clicked holding a container: this plus the preview making it ({@link QuantumCrafterBlockEntity#GHOST_BASE}
     * on), or plus the output tank holding it.
     */
    public static final int BUTTON_FLUID = 60_000;
    /** Pins or unpins what's in output tank this + i. */
    public static final int BUTTON_PIN_TANK = 70_000;
    /** Unpins pin this + i. */
    public static final int BUTTON_UNPIN = 80_000;
    /** {@code PIN * TAKE + slot}: pins or unpins what an output slot or preview shows. */
    public static final int PIN = 5;

    @org.jetbrains.annotations.Nullable
    private net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> cursorFluids(Player player) {
        if (getCarried().isEmpty()) return null;
        return net.neoforged.neoforge.transfer.access.ItemAccess.forPlayerCursor(player, this).oneByOne()
                .getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.ITEM);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= BUTTON_PIN_TANK && id < BUTTON_PIN_TANK + QuantumCrafterBlockEntity.OUTPUT_TANKS) {
            var fluid = blockEntity.outputTanks().get(id - BUTTON_PIN_TANK);
            if (fluid.isEmpty()) return false;
            blockEntity.togglePin(com.kadikular.quantimium.recipe.FluidEntry.key(net.neoforged.neoforge.transfer.fluid.FluidResource.of(fluid)));
            return true;
        }
        if (id >= BUTTON_UNPIN && id < BUTTON_UNPIN + QuantumCrafterBlockEntity.MAX_PINS) {
            int at = id - BUTTON_UNPIN;
            if (at >= blockEntity.pins().size()) return false;
            blockEntity.togglePin(blockEntity.pins().get(at));
            return true;
        }
        if (id / TAKE == PIN) {
            int local = id % TAKE;
            ItemStack shown = local < QuantumCrafterBlockEntity.OUTPUT_SLOTS
                    ? blockEntity.getInventory().getStackInSlot(QuantumCrafterBlockEntity.OUTPUT_START + local)
                    : local >= QuantumCrafterBlockEntity.GHOST_BASE ? blockEntity.getGhost(local) : ItemStack.EMPTY;
            if (shown.isEmpty()) return false;
            blockEntity.togglePin(net.neoforged.neoforge.transfer.item.ItemResource.of(shown));
            return true;
        }
        if (id >= BUTTON_TANK && id < BUTTON_TANK + QuantumCrafterBlockEntity.INPUT_TANKS) {
            var held = cursorFluids(player);
            if (held == null || !blockEntity.pour(held, id - BUTTON_TANK)) return false;
            broadcastChanges();
            return true;
        }
        if (id >= BUTTON_FLUID && id < TAKE) {
            int at = id - BUTTON_FLUID;
            net.neoforged.neoforge.fluids.FluidStack fluid = at >= QuantumCrafterBlockEntity.GHOST_BASE
                    ? com.kadikular.quantimium.recipe.FluidEntry.fluidOf(blockEntity.getGhost(at))
                    : blockEntity.outputTanks().get(at);
            var held = cursorFluids(player);
            if (fluid.isEmpty() || held == null || !blockEntity.fillFrom(held, fluid, at)) return false;
            broadcastChanges();
            return true;
        }
        int mode = id / TAKE;
        int local = id % TAKE;
        if (mode < BATCH || mode > TO_INVENTORY) return false;
        ItemStack shown;
        if (local < QuantumCrafterBlockEntity.OUTPUT_SLOTS) {
            shown = blockEntity.getInventory().getStackInSlot(QuantumCrafterBlockEntity.OUTPUT_START + local);
        } else if (local >= QuantumCrafterBlockEntity.GHOST_BASE) {
            shown = blockEntity.getGhost(local);
        } else {
            return false;
        }
        // A fluid is taken in a bucket (BUTTON_FLUID), never as an item.
        if (shown.isEmpty() || com.kadikular.quantimium.recipe.FluidEntry.is(shown)) return false;
        boolean toInventory = mode == TO_INVENTORY;
        ItemStack carried = getCarried();
        if (!toInventory && !carried.isEmpty() && !ItemStack.isSameItemSameComponents(carried, shown)) return false;
        int stack = shown.getMaxStackSize();
        int room = toInventory ? stack : stack - carried.getCount();
        int wanted = Math.min(room, switch (mode) {
            case HALF -> Math.max(1, stack / 2);
            case ONE -> 1;
            default -> stack;
        });
        if (wanted <= 0) return false;
        ItemStack got = ItemStack.EMPTY;
        // What's there first.
        for (int slot = QuantumCrafterBlockEntity.OUTPUT_START; slot <= QuantumCrafterBlockEntity.OUTPUT_END && got.getCount() < wanted; slot++) {
            if (!ItemStack.isSameItemSameComponents(blockEntity.getInventory().getStackInSlot(slot), shown)) continue;
            ItemStack more = blockEntity.getInventory().extractItem(slot, wanted - got.getCount(), false);
            got = got.isEmpty() ? more : got.copyWithCount(got.getCount() + more.getCount());
        }
        // Then made, a batch at a time.
        for (int tries = 0; got.getCount() < wanted && tries < 8; tries++) {
            int ghost = blockEntity.bestGhostFor(shown);
            if (ghost < 0) break;
            ItemStack made = blockEntity.takeGhost(ghost, wanted - got.getCount());
            if (made.isEmpty()) break;
            got = got.isEmpty() ? made : got.copyWithCount(got.getCount() + made.getCount());
        }
        if (got.isEmpty()) return false;
        // What a run made past what was asked for (eight more nuggets) stays in its outputs.
        int keep = Math.min(wanted, got.getCount());
        if (got.getCount() > keep) blockEntity.storeOutput(got.copyWithCount(got.getCount() - keep));
        got = got.copyWithCount(keep);
        if (toInventory) {
            ItemStack left = toInventory(got);
            if (!left.isEmpty()) blockEntity.storeOutput(left);
        } else {
            setCarried(carried.isEmpty() ? got : carried.copyWithCount(carried.getCount() + got.getCount()));
        }
        // The take already resized what it shows; anything newly possible (or not) is worked out in the background.
        blockEntity.refreshSoon();
        broadcastChanges();
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) blockEntity.viewed(false);
    }

    @Override
    public boolean stillValid(Player player) {
        Level level = this.blockEntity.getLevel() != null ? this.blockEntity.getLevel() : player.level();
        return level.getBlockEntity(blockEntity.getBlockPos()) == blockEntity
                && blockEntity.isUsableBy(player);
    }

    /** The flux band here, where it's heading, and the band the catalyst in the slot needs. */
    public FluxBand getBand() {
        return band(15);
    }

    public FluxBand getHeading() {
        return band(16);
    }

    public FluxBand getRequiredBand() {
        return band(17);
    }

    private FluxBand band(int index) {
        return FluxBand.values()[Math.clamp(data.get(index), 0, FluxBand.values().length - 1)];
    }
}

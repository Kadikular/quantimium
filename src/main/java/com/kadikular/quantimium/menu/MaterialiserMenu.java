package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.MaterialiserBlockEntity;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.init.ModMenuTypes;
import com.kadikular.quantimium.network.MaterialiserOptionsPayload;
import com.kadikular.quantimium.recipe.EntangledLinks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.kadikular.quantimium.util.SlotItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

/**
 * Layout matches {@code tools/gui_panels.py} materialiser: Matter at (26, 17), Trace in its socket at
 * (26, 53), the search box at (48, 17) over the choices at (48, 32), results in a column at (145, 17). On the server it sends the player
 * what the Materialiser could make of its Matter once a second while open.
 */
public class MaterialiserMenu extends AbstractContainerMenu {

    public static final int MATTER_X = 26;
    public static final int MATTER_Y = 17;
    public static final int TRACE_X = 26;
    public static final int TRACE_Y = 53;
    public static final int OUTPUT_X = 145;
    public static final int OUTPUT_Y = 17;

    /** Machine slots in the menu: Matter, Trace, three results. */
    private static final int MACHINE_SLOTS = 5;

    private final MaterialiserBlockEntity blockEntity;
    private final ContainerData data;
    private final Player player;
    private int sinceOptions;

    public MaterialiserMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv,
                (MaterialiserBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(MaterialiserBlockEntity.DATA_COUNT));
    }

    public MaterialiserMenu(int containerId, Inventory playerInv, MaterialiserBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.MATERIALISER_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        this.player = playerInv.player;
        checkContainerDataCount(data, MaterialiserBlockEntity.DATA_COUNT);

        addSlot(new SlotItemHandler(blockEntity.getInventory(), MaterialiserBlockEntity.MATTER_SLOT, MATTER_X, MATTER_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.UNREALISED_MATTER.get()) || EntangledLinks.isBound(stack);
            }
        });
        addSlot(new SlotItemHandler(blockEntity.getInventory(), MaterialiserBlockEntity.TRACE_SLOT, TRACE_X, TRACE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.QUANTIMIUM_TRACE.get()) || EntangledLinks.isBound(stack);
            }
        });
        for (int i = 0; i < 3; i++) {
            addSlot(new SlotItemHandler(blockEntity.getInventory(), MaterialiserBlockEntity.OUTPUT_START + i,
                    OUTPUT_X, OUTPUT_Y + i * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
        addDataSlots(data);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        // What it could make, once a second, and at once when opened.
        if (player instanceof ServerPlayer server && sinceOptions-- <= 0 && blockEntity.getLevel() instanceof ServerLevel level) {
            sinceOptions = 20;
            PacketDistributor.sendToPlayer(server, new MaterialiserOptionsPayload(blockEntity.options(level),
                    Optional.ofNullable(blockEntity.getTarget()), blockEntity.matterSeen().copy()));
        }
    }

    public MaterialiserBlockEntity getBlockEntity() {
        return blockEntity;
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

    public int getProgress() {
        return data.get(5);
    }

    public int getAverageFePerTick() {
        return data.get(6);
    }

    public int getSurchargePercent() {
        return data.get(7);
    }

    public FluxBand getBand() {
        return FluxBand.values()[Math.clamp(data.get(8), 0, FluxBand.values().length - 1)];
    }

    public FluxBand getHeading() {
        return FluxBand.values()[Math.clamp(data.get(9), 0, FluxBand.values().length - 1)];
    }

    /** Trace paid for but not yet spent. */
    public double getTraceCredit() {
        return data.get(10) / 1000.0;
    }

    /** The chosen ore's rarity (an ordinal), or -1. */
    public int getTargetRarity() {
        return data.get(11);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.is(ModItems.UNREALISED_MATTER.get())) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (stack.is(ModItems.QUANTIMIUM_TRACE.get())) {
            if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
        } else if (EntangledLinks.isBound(stack)) {
            // A bound Tesseract feeds Matter first, then Trace.
            if (!moveItemStackTo(stack, 0, 2, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, ModBlocks.MATERIALISER.get());
    }
}

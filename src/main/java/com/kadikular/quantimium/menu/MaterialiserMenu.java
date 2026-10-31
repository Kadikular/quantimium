package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.MaterialiserBlockEntity;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModMenuTypes;
import com.kadikular.quantimium.network.MaterialiserOptionsPayload;
import com.kadikular.quantimium.recipe.EntangledLinks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import com.kadikular.quantimium.util.SlotItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

/**
 * Layout matches {@code tools/gui_panels.py} materialiser: Matter at (26, 17), Anomalite Dust in its socket at
 * (26, 53), the search box over a grid of the forms it could make, taken from as at a Logistics Terminal. On the
 * server it sends the player what the Materialiser could make of its Matter, and how many, twice a second while open
 * and at once after a take.
 */
public class MaterialiserMenu extends QuantumMenu {

    public static final int MATTER_X = 26;
    public static final int MATTER_Y = 17;
    public static final int TRACE_X = 26;
    public static final int TRACE_Y = 53;
    public static final int PLAYER_Y = 110;

    /** Machine slots in the menu: Matter, Anomalite Dust. */
    private static final int MACHINE_SLOTS = 2;

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
                return com.kadikular.quantimium.unrealised.Matter.is(stack) || EntangledLinks.isBound(stack);
            }
        });
        addSlot(new SlotItemHandler(blockEntity.getInventory(), MaterialiserBlockEntity.TRACE_SLOT, TRACE_X, TRACE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return com.kadikular.quantimium.unrealised.Materialising.pays(stack) || EntangledLinks.isBound(stack);
            }
        });
        addPlayerInventory(playerInv, PLAYER_Y);
        addDataSlots(data);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        // What it could make, once a second, and at once when opened.
        if (player instanceof ServerPlayer server && sinceOptions-- <= 0 && blockEntity.getLevel() instanceof ServerLevel level) {
            sinceOptions = 10;
            var options = blockEntity.options(level);
            List<Long> buffered = new java.util.ArrayList<>(options.size());
            List<Long> makeable = new java.util.ArrayList<>(options.size());
            for (var option : options) {
                long held = blockEntity.buffered(option.display());
                buffered.add(held);
                makeable.add(option.allowed() ? Math.max(0, blockEntity.available(option) - held) : 0L);
            }
            PacketDistributor.sendToPlayer(server, new MaterialiserOptionsPayload(options,
                    Optional.ofNullable(blockEntity.getTarget()), blockEntity.matterSeen().copy(), buffered, makeable));
        }
    }

    /**
     * Gives {@code count} of {@code form} to {@code player}, from the buffer first, then made: onto the cursor (as much as
     * fits beside what's there, which must be the same), or into the inventory. What it made over stays in its buffer.
     */
    public void take(Player player, com.kadikular.quantimium.unrealised.Materialising.Target form, int count, boolean toInventory) {
        ItemStack carried = getCarried();
        int room = count;
        if (!toInventory) {
            if (!carried.isEmpty() && !net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(carried.getItem()).equals(form.form())) return;
            int max = carried.isEmpty() ? 64 : carried.getMaxStackSize();
            room = Math.min(count, max - carried.getCount());
        }
        if (room <= 0) return;
        ItemStack made = getBlockEntity().makeFor(form, room);
        if (made.isEmpty()) return;
        if (toInventory) {
            player.getInventory().add(made);
            if (!made.isEmpty()) getBlockEntity().addBuffered(made);
        } else if (carried.isEmpty()) {
            setCarried(made);
        } else {
            carried.grow(made.getCount());
            setCarried(carried);
        }
        sinceOptions = 0;
    }

    /** Puts what's on the cursor (or one of it) back into its buffer, if it's something it makes. */
    public void putBack(boolean one) {
        ItemStack carried = getCarried();
        if (carried.isEmpty() || !getBlockEntity().takesBack(carried)) return;
        getBlockEntity().addBuffered(carried.split(one ? 1 : carried.getCount()));
        setCarried(carried);
        sinceOptions = 0;
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

    /** Anomalite Dust paid for but not yet spent. */
    public double getTraceCredit() {
        return data.get(10) / 1000.0;
    }

    /** The chosen ore's rarity (an ordinal), or -1. */
    public int getTargetRarity() {
        return data.get(11);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return shiftClick(player, index, stack -> {
            if (index < MACHINE_SLOTS) {
                if (!moveItemStackTo(stack, MACHINE_SLOTS, slots.size(), true)) return false;
            } else if (com.kadikular.quantimium.unrealised.Matter.is(stack)) {
                if (!moveItemStackTo(stack, 0, 1, false)) return false;
            } else if (com.kadikular.quantimium.unrealised.Materialising.pays(stack)) {
                if (!moveItemStackTo(stack, 1, 2, false)) return false;
            } else if (EntangledLinks.isBound(stack)) {
                // A bound Tesseract feeds Matter first, then Anomalite Dust.
                if (!moveItemStackTo(stack, 0, 2, false)) return false;
            } else {
                return false;
            }
            return true;
        });
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, ModBlocks.MATERIALISER.get());
    }
}

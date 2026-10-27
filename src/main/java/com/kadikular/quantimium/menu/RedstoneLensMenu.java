package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.init.ModMenuTypes;
import com.kadikular.quantimium.item.RedstoneLensItem;
import com.kadikular.quantimium.laser.RedstoneSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A Redstone Lens in hand, in a mount or in an emitter: its mode, its signal's strength, and for a count the item (a
 * ghost entry) and how many. Changes go straight onto the lens. Layout: tools/gui_panels.py redstone_lens.
 */
public class RedstoneLensMenu extends AbstractContainerMenu implements GhostFilterMenu {

    public static final int BUTTON_MODE = 0;
    public static final int BUTTON_STRONGER = 1;
    public static final int BUTTON_WEAKER = 2;
    /** Sets the count outright: the count rides in the id, above this. */
    public static final int BUTTON_COUNT = 1_000_000;
    public static final int ITEM_X = 80;
    public static final int ITEM_Y = 35;

    private final Supplier<ItemStack> lens;
    private final Runnable changed;
    private final Predicate<Player> valid;
    private final SimpleContainer shown = new SimpleContainer(1);

    public static RedstoneLensMenu fromNetwork(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        return new RedstoneLensMenu(containerId, playerInv, LensHolder.read(playerInv, extraData));
    }

    public RedstoneLensMenu(int containerId, Inventory playerInv, LensHolder holder) {
        super(ModMenuTypes.REDSTONE_LENS_MENU.get(), containerId);
        this.lens = holder.lens();
        this.changed = holder.changed();
        this.valid = holder.valid();
        int heldSlot = holder.heldSlot();
        addSlot(new Slot(shown, 0, ITEM_X, ITEM_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player who) {
                return false;
            }

            @Override
            public boolean isActive() {
                return settings().mode().counts();
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142) {
                // The lens being set, held, stays where it is.
                @Override
                public boolean mayPickup(Player who) {
                    return getContainerSlot() != heldSlot;
                }
            });
        }
        refresh();
    }

    public RedstoneSettings settings() {
        return RedstoneLensItem.settingsOf(lens.get());
    }

    private void set(RedstoneSettings settings) {
        if (lens.get().getItem() instanceof RedstoneLensItem) {
            lens.get().set(ModDataComponents.REDSTONE.get(), settings);
            changed.run();
        }
        refresh();
    }

    private void refresh() {
        shown.setItem(0, settings().item().copy());
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player who) {
        if (slotId == 0) {
            if (clickType == ContainerInput.PICKUP) setGhost(0, getCarried());
            return;
        }
        super.clicked(slotId, button, clickType, who);
    }

    @Override
    public void setGhost(int index, ItemStack stack) {
        if (index == 0) set(settings().withItem(stack));
    }

    @Override
    public boolean clickMenuButton(Player who, int id) {
        RedstoneSettings settings = settings();
        if (id >= BUTTON_COUNT) {
            set(settings.withCount(id - BUTTON_COUNT));
            return true;
        }
        switch (id) {
            case BUTTON_MODE -> set(settings.withMode(settings.mode().next()));
            case BUTTON_STRONGER -> set(settings.withLevel(settings.level() % 15 + 1));
            case BUTTON_WEAKER -> set(settings.withLevel((settings.level() + 13) % 15 + 1));
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player who) {
        return valid.test(who);
    }
}

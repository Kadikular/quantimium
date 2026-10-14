package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.init.ModMenuTypes;
import com.kadikular.quantimium.item.IsolatorLensItem;
import com.kadikular.quantimium.item.LensItem;
import com.kadikular.quantimium.laser.IsolatorSettings;
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
 * An Isolator Lens in hand, in a mount or in a Relay emitter: whether a network, actions and modifiers pass it, and
 * nine ghost entries of lenses let through only, or stopped. Changes go straight onto the lens. Layout:
 * tools/gui_panels.py laser_port_filter, as a filter lens's.
 */
public class IsolatorLensMenu extends AbstractContainerMenu implements GhostFilterMenu {

    public static final int BUTTON_ALLOW = 0;
    public static final int BUTTON_LOGISTICS = 1;
    public static final int BUTTON_ACTIONS = 2;
    public static final int BUTTON_MODIFIERS = 3;
    public static final int FILTER_X = FilterLensMenu.FILTER_X;
    public static final int FILTER_Y = FilterLensMenu.FILTER_Y;

    private final Supplier<ItemStack> lens;
    private final Runnable changed;
    private final Predicate<Player> valid;
    private final SimpleContainer shown = new SimpleContainer(IsolatorSettings.SLOTS);

    public static IsolatorLensMenu fromNetwork(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        return new IsolatorLensMenu(containerId, playerInv, LensHolder.read(playerInv, extraData));
    }

    public IsolatorLensMenu(int containerId, Inventory playerInv, LensHolder holder) {
        super(ModMenuTypes.ISOLATOR_LENS_MENU.get(), containerId);
        this.lens = holder.lens();
        this.changed = holder.changed();
        this.valid = holder.valid();
        int heldSlot = holder.heldSlot();
        for (int i = 0; i < IsolatorSettings.SLOTS; i++) {
            addSlot(new Slot(shown, i, FILTER_X + i * 18, FILTER_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player who) {
                    return false;
                }
            });
        }
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

    public IsolatorSettings settings() {
        return IsolatorLensItem.settingsOf(lens.get());
    }

    private void set(IsolatorSettings settings) {
        if (lens.get().getItem() instanceof IsolatorLensItem) {
            lens.get().set(ModDataComponents.ISOLATOR.get(), settings);
            changed.run();
        }
        refresh();
    }

    private void refresh() {
        IsolatorSettings settings = settings();
        for (int i = 0; i < IsolatorSettings.SLOTS; i++) shown.setItem(i, settings.entry(i).copy());
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player who) {
        if (slotId >= 0 && slotId < IsolatorSettings.SLOTS) {
            if (clickType == ContainerInput.PICKUP) setGhost(slotId, getCarried());
            return;
        }
        super.clicked(slotId, button, clickType, who);
    }

    /** Only lenses go in its entries. */
    @Override
    public void setGhost(int index, ItemStack stack) {
        if (!stack.isEmpty() && !(stack.getItem() instanceof LensItem)) return;
        set(settings().withEntry(index, stack));
    }

    @Override
    public boolean clickMenuButton(Player who, int id) {
        IsolatorSettings settings = settings();
        switch (id) {
            case BUTTON_ALLOW -> set(settings.withAllow(!settings.allow()));
            case BUTTON_LOGISTICS -> set(settings.withLogistics(!settings.logistics()));
            case BUTTON_ACTIONS -> set(settings.withActions(!settings.actions()));
            case BUTTON_MODIFIERS -> set(settings.withModifiers(!settings.modifiers()));
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

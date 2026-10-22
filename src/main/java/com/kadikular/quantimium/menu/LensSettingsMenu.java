package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.init.ModMenuTypes;
import com.kadikular.quantimium.item.InteractLensItem;
import com.kadikular.quantimium.item.VoidLensItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A lens's few switches, in hand, in a mount or in a Relay emitter: an Interact lens's use or hit and what it works on,
 * a Void lens's mode. Changes go straight onto the lens. Layout: tools/gui_panels.py lens_settings.
 */
public class LensSettingsMenu extends AbstractContainerMenu {

    public static final int BUTTON_HITS = 0;
    public static final int BUTTON_TARGETS = 1;
    public static final int BUTTON_VOID_ALL = 2;

    private final Supplier<ItemStack> lens;
    private final Runnable changed;
    private final Predicate<Player> valid;

    public static LensSettingsMenu fromNetwork(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        return new LensSettingsMenu(containerId, playerInv, LensHolder.read(playerInv, extraData));
    }

    public LensSettingsMenu(int containerId, Inventory playerInv, LensHolder holder) {
        super(ModMenuTypes.LENS_SETTINGS_MENU.get(), containerId);
        this.lens = holder.lens();
        this.changed = holder.changed();
        this.valid = holder.valid();
    }

    /** The lens being set. */
    public ItemStack lens() {
        return lens.get();
    }

    @Override
    public boolean clickMenuButton(Player who, int id) {
        ItemStack stack = lens.get();
        boolean done = switch (id) {
            case BUTTON_HITS -> stack.getItem() instanceof InteractLensItem && InteractLensItem.toggle(stack) == InteractLensItem.hits(stack);
            case BUTTON_TARGETS -> stack.getItem() instanceof InteractLensItem && InteractLensItem.cycleTargets(stack) >= 0;
            case BUTTON_VOID_ALL -> stack.getItem() instanceof VoidLensItem && VoidLensItem.toggle(stack) == VoidLensItem.voidsAll(stack);
            default -> false;
        };
        if (done) changed.run();
        return done;
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

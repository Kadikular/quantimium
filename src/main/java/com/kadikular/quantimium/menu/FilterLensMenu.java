package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.init.ModMenuTypes;
import com.kadikular.quantimium.item.FilterLensItem;
import com.kadikular.quantimium.laser.LensFilter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A Filter lens in hand: its nine ghost entries in a row, whitelist or blacklist, and which living things
 * it lets through. Changes go straight onto the lens. Layout: tools/gui_panels.py collector_filter.
 */
public class FilterLensMenu extends AbstractContainerMenu implements GhostFilterMenu {

    public static final int BUTTON_MODE = 0;
    public static final int BUTTON_LIVING = 1;
    public static final int BUTTON_GROWN = 2;
    public static final int FILTER_X = 8;
    public static final int FILTER_Y = 35;
    public static final int FILTER_END = LensFilter.SLOTS;

    private final Player player;
    private final InteractionHand hand;
    /** What the slots show; the lens itself holds the truth. */
    private final SimpleContainer shown = new SimpleContainer(LensFilter.SLOTS);

    public FilterLensMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, extraData.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
    }

    public FilterLensMenu(int containerId, Inventory playerInv, InteractionHand hand) {
        super(ModMenuTypes.FILTER_LENS_MENU.get(), containerId);
        this.player = playerInv.player;
        this.hand = hand;
        for (int i = 0; i < LensFilter.SLOTS; i++) {
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
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142) {
                // The lens being set stays where it is.
                @Override
                public boolean mayPickup(Player who) {
                    return !(hand == InteractionHand.MAIN_HAND && getContainerSlot() == playerInv.getSelectedSlot());
                }
            });
        }
        refresh();
    }

    private ItemStack lens() {
        return player.getItemInHand(hand);
    }

    public LensFilter filter() {
        return FilterLensItem.filterOf(lens());
    }

    private void set(LensFilter filter) {
        if (lens().getItem() instanceof FilterLensItem) lens().set(ModDataComponents.LENS_FILTER.get(), filter);
        refresh();
    }

    private void refresh() {
        LensFilter filter = filter();
        for (int i = 0; i < LensFilter.SLOTS; i++) shown.setItem(i, filter.entry(i).copy());
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player who) {
        if (slotId >= 0 && slotId < FILTER_END) {
            if (clickType == ContainerInput.QUICK_MOVE) set(filter().cycledTag(slotId));
            else if (clickType == ContainerInput.PICKUP) setGhost(slotId, getCarried());
            return;
        }
        super.clicked(slotId, button, clickType, who);
    }

    @Override
    public void setGhost(int index, ItemStack stack) {
        set(filter().withEntry(index, stack));
    }

    @Override
    public boolean clickMenuButton(Player who, int id) {
        LensFilter filter = filter();
        if (id == BUTTON_MODE) set(new LensFilter(filter.entries(), !filter.allow(), filter.living(), filter.grown()));
        else if (id == BUTTON_LIVING) set(new LensFilter(filter.entries(), filter.allow(), filter.living().next(), filter.grown()));
        else if (id == BUTTON_GROWN) set(new LensFilter(filter.entries(), filter.allow(), filter.living(), !filter.grown()));
        else return false;
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player who) {
        return lens().getItem() instanceof FilterLensItem;
    }
}

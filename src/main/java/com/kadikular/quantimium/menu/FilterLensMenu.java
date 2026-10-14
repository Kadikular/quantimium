package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.init.ModMenuTypes;
import com.kadikular.quantimium.item.FilterLensItem;
import com.kadikular.quantimium.laser.LensFilter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A filter lens in hand or in a mount: nine ghost entries in a row and allow or deny. A Block Filter Lens also has
 * ripe-only; an Entity Filter Lens's entries are spawn eggs, and it also has which kinds of living thing, whether to
 * spare the young, and how many to spare. Changes go straight onto the lens. Layout: tools/gui_panels.py
 * laser_port_filter.
 */
public class FilterLensMenu extends AbstractContainerMenu implements GhostFilterMenu {

    public static final int BUTTON_ALLOW = 0;
    public static final int BUTTON_RIPE = 1;
    public static final int BUTTON_YOUNG = 2;
    public static final int BUTTON_KEEP_MORE = 3;
    public static final int BUTTON_KEEP_FEWER = 4;
    public static final int BUTTON_KEEP_MORE_TEN = 5;
    public static final int BUTTON_KEEP_FEWER_TEN = 6;
    /** Toggling a kind of living thing: this plus {@link LensFilter#HOSTILE}, {@link LensFilter#ANIMALS} or {@link LensFilter#PLAYERS}. */
    public static final int BUTTON_KIND = 8;
    public static final int FILTER_X = 8;
    public static final int FILTER_Y = 35;
    public static final int FILTER_END = LensFilter.SLOTS;

    private final java.util.function.Supplier<ItemStack> lens;
    /** After a change: a mount's light changes with its lens. */
    private final Runnable changed;
    private final java.util.function.Predicate<Player> valid;
    /** What the slots show; the lens itself holds the truth. */
    private final SimpleContainer shown = new SimpleContainer(LensFilter.SLOTS);

    public static FilterLensMenu fromNetwork(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        return new FilterLensMenu(containerId, playerInv, LensHolder.read(playerInv, extraData));
    }

    public FilterLensMenu(int containerId, Inventory playerInv, LensHolder holder) {
        super(ModMenuTypes.FILTER_LENS_MENU.get(), containerId);
        this.lens = holder.lens();
        this.changed = holder.changed();
        this.valid = holder.valid();
        int heldSlot = holder.heldSlot();
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
                // The lens being set, held, stays where it is.
                @Override
                public boolean mayPickup(Player who) {
                    return getContainerSlot() != heldSlot;
                }
            });
        }
        refresh();
    }

    private ItemStack lens() {
        return lens.get();
    }

    public LensFilter filter() {
        return FilterLensItem.filterOf(lens());
    }

    /** Which part of the filter this lens sets. */
    public int part() {
        return lens().getItem() instanceof FilterLensItem item ? item.part() : LensFilter.ITEMS;
    }

    private void set(LensFilter filter) {
        if (lens().getItem() instanceof FilterLensItem) {
            lens().set(ModDataComponents.LENS_FILTER.get(), filter);
            changed.run();
        }
        refresh();
    }

    private void refresh() {
        LensFilter filter = filter();
        for (int i = 0; i < LensFilter.SLOTS; i++) shown.setItem(i, filter.entry(part(), i).copy());
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player who) {
        if (slotId >= 0 && slotId < FILTER_END) {
            // Spawn eggs have no tags to step through.
            if (clickType == ContainerInput.QUICK_MOVE && part() != LensFilter.ENTITIES) set(filter().cycledTag(part(), slotId));
            else if (clickType == ContainerInput.PICKUP) setGhost(slotId, getCarried());
            return;
        }
        super.clicked(slotId, button, clickType, who);
    }

    @Override
    public void setGhost(int index, ItemStack stack) {
        // An Entity lens lists mobs by their spawn eggs.
        if (part() == LensFilter.ENTITIES && !stack.isEmpty() && !(stack.getItem() instanceof net.minecraft.world.item.SpawnEggItem)) return;
        set(filter().withEntry(part(), index, stack));
    }

    @Override
    public boolean clickMenuButton(Player who, int id) {
        LensFilter filter = filter();
        int part = part();
        if (id == BUTTON_ALLOW) set(filter.withAllow(part, !filter.allows(part)));
        else if (id == BUTTON_RIPE && part == LensFilter.BLOCKS) set(filter.withRipe(!filter.ripe()));
        else if (id == BUTTON_YOUNG && part == LensFilter.ENTITIES) set(filter.withSpareYoung(!filter.spareYoung()));
        else if (id == BUTTON_KEEP_MORE && part == LensFilter.ENTITIES) set(filter.withKeep(filter.keep() + 1));
        else if (id == BUTTON_KEEP_FEWER && part == LensFilter.ENTITIES) set(filter.withKeep(filter.keep() - 1));
        else if (id == BUTTON_KEEP_MORE_TEN && part == LensFilter.ENTITIES) set(filter.withKeep(filter.keep() + 10));
        else if (id == BUTTON_KEEP_FEWER_TEN && part == LensFilter.ENTITIES) set(filter.withKeep(filter.keep() - 10));
        else if (id > BUTTON_KIND && id <= BUTTON_KIND + LensFilter.PLAYERS && part == LensFilter.ENTITIES) set(filter.withKinds(filter.kinds() ^ (id - BUTTON_KIND)));
        else return false;
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

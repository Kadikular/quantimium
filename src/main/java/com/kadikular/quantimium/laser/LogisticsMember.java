package com.kadikular.quantimium.laser;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Something on a logistics network that reaches an inventory: a Logistics Receptacle on a chest or a machine,
 * or a Laser Port with what it's caught. {@link Logistics} moves items between them.
 */
public interface LogisticsMember {

    /** What it does with the inventory it reaches. */
    enum Role implements net.minecraft.util.StringRepresentable {
        /** Where the network keeps things: what it takes in goes here, highest priority first, and can be taken out again. */
        STORAGE,
        /** Things the network may take (passive), or pushes into the network itself (active). */
        PROVIDER,
        /** Takes things active providers push (passive), or pulls what it lists from the network itself (active). */
        REQUESTER;

        public Role next() {
            return values()[(ordinal() + 1) % values().length];
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    Role role();

    /** Whether it moves items itself, once a second: an active provider pushes, an active requester pulls. */
    boolean active();

    /** Higher goes first: storage is filled from the highest and emptied from the lowest. */
    int priority();

    /** Whether its filter lets {@code stack} through. */
    boolean lets(ItemStack stack);

    /** The inventory it reaches, or null when there's none. */
    @Nullable
    IItemHandler inventory();

    /** Where that inventory is, so nothing is moved from an inventory into itself. */
    BlockPos inventoryPos();

    /** The network has found it, joined to a controller's light, or not: it shows it. */
    default void connected(boolean connected) {}
}

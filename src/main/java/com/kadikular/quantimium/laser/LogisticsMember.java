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

    /** The channel it's on ({@link Channels}): it joins only a Logistics Controller on it (either on All). */
    default int channel() {
        return 0;
    }

    /** Whether its filter lets {@code stack} through. */
    boolean lets(ItemStack stack);

    /** The inventory it reaches, or null when there's none. */
    @Nullable
    IItemHandler inventory();

    /** The fluid tanks it reaches, or null when there are none. */
    @Nullable
    default net.neoforged.neoforge.fluids.capability.IFluidHandler fluids() {
        return null;
    }

    /**
     * The fluids its machine makes to order (a Crafter's recipes making only fluids), made as they're drained; null for
     * none. Its {@link #fluids} hold only what's really there.
     */
    @Nullable
    default net.neoforged.neoforge.fluids.capability.IFluidHandler madeFluids() {
        return null;
    }

    /** Whether its filter lets {@code fluid} through: only fluid entries count; with none listed, as with an empty filter. */
    default boolean lets(net.neoforged.neoforge.fluids.FluidStack fluid) {
        return role() != Role.REQUESTER;
    }

    /** How many more mB of {@code fluid} it will take in (see {@link #wants(ItemStack)}). */
    default int wants(net.neoforged.neoforge.fluids.FluidStack fluid) {
        return Integer.MAX_VALUE;
    }

    /** Where that inventory is, so nothing is moved from an inventory into itself. */
    BlockPos inventoryPos();

    /**
     * For a terminal, if it knows better than its slots say: each thing, with how many are there and how many more
     * could be made (a Reactor). Null to read its slots.
     */
    @Nullable
    default java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, long[]> listing() {
        return null;
    }

    /**
     * For a member whose stock is kept by item ({@link #listing()} not null, a Reactor's port): takes up to {@code max}
     * of {@code item}, what's there first, as a pull from a slot showing it would. Looked up by item, not by slot:
     * such a member may show thousands.
     */
    default net.minecraft.world.item.ItemStack takeListed(net.neoforged.neoforge.transfer.item.ItemResource item, int max, boolean simulate) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    /**
     * How many more of {@code stack} it will take in: a requester keeping counts stocked takes only what it's short
     * of; anything else, as much as fits.
     */
    default int wants(ItemStack stack) {
        return Integer.MAX_VALUE;
    }

    /** The network has found it, joined to a controller's light, or not: it shows it. */
    default void connected(boolean connected) {}

    /** Whether its network's controller has the power to move anything: it shows that too. */
    default void networkPowered(boolean powered) {}

    /** The machine behind it that makes things to order (a Quantum Crafter), whose made things are craftable, not stock. */
    @Nullable
    default OfferMaker maker() {
        return null;
    }

    /** Whether what it offers may include what a machine would make (a provider's mode), or only what's really there. */
    default boolean offersCraftable() {
        return true;
    }

    /**
     * Whether what the machine behind it makes may come from what other machines would make (a chain), or only
     * from what's really stored.
     */
    default boolean craftsFromCraftable() {
        return false;
    }

    /** An active requester: whether it may have machines make what it pulls, or takes only what's stored. */
    default boolean requestsCraftable() {
        return true;
    }

    /** Whether a pull from {@code slot} would make something (see {@link #maker}) rather than hand over what's there. */
    default boolean onDemand(int slot) {
        OfferMaker maker = maker();
        return maker != null && maker.makesOnDemand(slot);
    }

    /** Another network's offers, when it sits on that network's Logistics Materialiser; null otherwise. */
    @Nullable
    default OfferTable imports() {
        return null;
    }

    /**
     * An active member's pace, kept by the logistics pass: {@code [seconds before it looks again, its last wait]}.
     * Null, it looks every second.
     */
    @org.jetbrains.annotations.Nullable
    default int[] pace() {
        return null;
    }
}

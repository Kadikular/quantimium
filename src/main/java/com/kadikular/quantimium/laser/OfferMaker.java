package com.kadikular.quantimium.laser;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A machine that makes things to order behind a provider Receptacle: a Quantum Crafter, the Unrealised
 * Materialiser. Its network's {@link OfferTable} lists what it makes as craftable, not as stock, and works it
 * out from a snapshot of the rest of the network rather than by asking the network live.
 */
public interface OfferMaker {
    /**
     * One craft it could make: up to {@code count} of {@code item}, out of the items {@code from}; {@code craft}, the
     * machine's own note of the craft, handed back to it in {@link #offered} if the network keeps it.
     */
    record Made(ItemResource item, long count, Set<Item> from, @org.jetbrains.annotations.Nullable Object craft) {
        public Made(ItemResource item, long count, Set<Item> from) {
            this(item, count, from, null);
        }
    }

    /**
     * What it could make, craft by craft, if this network offered {@code stock} (the network's real items and what
     * the other machines could make, its own left out) through any Tesseract it has bound to that network. Without
     * {@code fromCraftable}, from only what's really stored, on any network it draws on.
     */
    List<Made> makes(LogisticsController network, Map<ItemResource, Long> stock, boolean fromCraftable);

    /** Working out {@link #makes} a step at a time. */
    interface Making {
        /** Does a step; whether it's done. */
        boolean step();

        /** What it could make, once done. */
        List<Made> result();
    }

    /** As {@link #makes}, a step at a time: a machine with a thousand recipes to look through needn't do it in one. */
    default Making making(LogisticsController network, Map<ItemResource, Long> stock, boolean fromCraftable) {
        List<Made> made = makes(network, stock, fromCraftable);
        return new Making() {
            @Override
            public boolean step() {
                return true;
            }

            @Override
            public List<Made> result() {
                return made;
            }
        };
    }

    /**
     * The network has worked its offers out: of what it last asked this machine, these are the crafts ({@link Made#craft})
     * it kept, loops left out; what a pull from it should be able to make now. A machine that shows what it makes (a
     * Crafter's previews) shows these, so what it's asked for is what the network says it makes.
     */
    default void offered(LogisticsController network, List<Object> crafts) {}

    /** Whether a pull from {@code slot} of its automation view would make something rather than hand over what's there. */
    boolean makesOnDemand(int slot);

    /**
     * Whether what's in {@code slot} of its automation view is on offer as it is: what it has made, not what it
     * makes things from (a Crafter's grid shows to pipes, but isn't the network's to take).
     */
    default boolean stocks(int slot) {
        return true;
    }

    /**
     * What a pull from {@code slot} (one that makes something) would make, without working out whether it could right
     * now: for finding where things are, which then asks only the few slots it found.
     */
    net.minecraft.world.item.ItemStack peekOnDemand(int slot);
}

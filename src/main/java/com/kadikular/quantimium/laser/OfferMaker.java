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
    /** One craft it could make: up to {@code count} of {@code item}, out of the items {@code from}. */
    record Made(ItemResource item, long count, Set<Item> from) {}

    /**
     * What it could make, craft by craft, if this network offered {@code stock} (the network's real items and what
     * the other machines could make, its own left out) through any Tesseract it has bound to that network. Without
     * {@code fromCraftable}, from only what's really stored, on any network it draws on.
     */
    List<Made> makes(LogisticsController network, Map<ItemResource, Long> stock, boolean fromCraftable);

    /** Whether a pull from {@code slot} of its automation view would make something rather than hand over what's there. */
    boolean makesOnDemand(int slot);
}

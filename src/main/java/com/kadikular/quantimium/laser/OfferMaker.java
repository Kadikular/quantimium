package com.kadikular.quantimium.laser;

import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.Map;

/**
 * A machine that makes things to order behind a provider Receptacle: a Quantum Crafter, the Unrealised
 * Materialiser. Its network's {@link OfferTable} lists what it makes as craftable, not as stock, and works it
 * out from a snapshot of the rest of the network rather than by asking the network live.
 */
public interface OfferMaker {
    /**
     * What it could make, by item and how many, if this network offered {@code stock} (the network's real items and
     * what the other machines could make, its own left out) through any Tesseract it has bound to that network.
     */
    Map<ItemResource, Long> makes(LogisticsController network, Map<ItemResource, Long> stock);

    /** Whether a pull from {@code slot} of its automation view would make something rather than hand over what's there. */
    boolean makesOnDemand(int slot);
}

package com.kadikular.quantimium.laser;

/** A Logistics Controller: everything its light reaches is one logistics network, which it pays to run. */
public interface LogisticsController {

    /** Whether it has the power to move a stack. */
    boolean powered();

    /** Takes {@code fe} to move a stack; whether it had it. */
    boolean pay(long fe);

    /** What the network found, once a second: its members, and whether another controller's light joins its own. */
    void report(java.util.List<LogisticsMember> members, boolean clash);

    /** Its network's members, as last found; none while another controller's light stops it. */
    java.util.List<LogisticsMember> members();

    /** What its network offers, real and craftable, as last worked out (see {@link OfferTable}). */
    default OfferTable offers() {
        return OfferTable.EMPTY;
    }

    /** Something moved on its network that a player is watching for (a terminal take): the offers are worked out again soon. */
    default void stockMoved() {}

    /** A player is looking at what it offers (a terminal open): what it works out comes first. */
    default void watched() {}

    /** Something moved on its network by itself (a pipe, a lens, a receptacle): the offers catch up at their usual pace. */
    default void movedQuietly() {}

    /**
     * Where each thing is, looked over again now: for a take that came up short of what the network lists, in case
     * it's been put somewhere new since {@link #spots()} looked. Null if that's been done this tick already.
     */
    @org.jetbrains.annotations.Nullable
    default java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, java.util.List<Logistics.Spot>> spotsAgain() {
        return null;
    }

    /** A machine making things for it has changed what it could make (its grid, say): its offers are worked out again. */
    default void makerChanged() {}

    /** Where each thing on its network is, looked over at most once a tick and again after a move (see {@link Logistics#locate}). */
    default java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, java.util.List<Logistics.Spot>> spots() {
        return Logistics.locate(members());
    }
}

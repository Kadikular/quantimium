package com.kadikular.quantimium.superposition;

import net.minecraft.util.StringRepresentable;

/**
 * What a block in one of a Superposition Pod's four cradle slots adds to it. Plating fills a slot and
 * adds nothing; the rest are upgrades, and a cradle has room for four, so a pod is a choice. New modules
 * go on the end: a module's bit is its place here, and the registry keeps those bits.
 */
public enum PodModule implements StringRepresentable {
    PLATING("plating"),
    /** Lets the pod be its owner's Anchor: dying wakes them in its double. */
    RESCUE("rescue"),
    /** Its double keeps its owner on Regeneration I, anywhere in the dimension. */
    REGENERATION("regeneration"),
    /** Its double keeps its owner on Resistance I, anywhere in the dimension. */
    HARDENING("hardening"),
    /** Keeps what prowls round the pod off its double. */
    WARD("ward"),
    /** Opens its owner's stash, from anywhere, while it holds one of their doubles. */
    STASH("stash"),
    /** Its double charges whatever holds FE in its owner's inventory, anywhere in the dimension. */
    CHARGE("charge"),
    /** Makes the pod a hub: it reaches the pods its Tesseracts are bound to, holding a double or not. */
    RELAY("relay"),
    /** Brings a lost double home: a field double, or a Sophon lying where one was knocked out. */
    RECOVERY("recovery");

    private final String name;

    PodModule(String name) {
        this.name = name;
    }

    /** This module's bit in a pod's module mask; plating has none. */
    public int bit() {
        return this == PLATING ? 0 : 1 << (ordinal() - 1);
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}

package com.kadikular.quantimium.laser;

import com.kadikular.quantimium.item.LensItem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * What an Isolator Lens lets through of the light before it, at the light's reach. Set off, {@code power} leaves the
 * machines past it unpowered (what the light does past it is still paid for), {@code logistics} stops a controller's
 * network there, {@code actions} what the light does, {@code modifiers} what changes it, and {@code redstone} a
 * Redstone Lens's signal; listed lenses are let through only (allow) or stopped (deny), among what the switches pass.
 * Unset, it lets power through and nothing else, as the Isolator block does.
 */
public record IsolatorSettings(boolean logistics, boolean actions, boolean modifiers, List<ItemStack> lenses, boolean allow, boolean power,
                               boolean redstone) {

    public static final int SLOTS = 9;
    public static final IsolatorSettings POWER_ONLY = new IsolatorSettings(false, false, false, List.of(), false, true, false);

    public IsolatorSettings {
        lenses = List.copyOf(lenses);
    }

    public static final Codec<IsolatorSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("logistics", false).forGetter(IsolatorSettings::logistics),
            Codec.BOOL.optionalFieldOf("actions", false).forGetter(IsolatorSettings::actions),
            Codec.BOOL.optionalFieldOf("modifiers", false).forGetter(IsolatorSettings::modifiers),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("lenses", List.of()).forGetter(IsolatorSettings::lenses),
            Codec.BOOL.optionalFieldOf("allow", false).forGetter(IsolatorSettings::allow),
            Codec.BOOL.optionalFieldOf("power", true).forGetter(IsolatorSettings::power),
            Codec.BOOL.optionalFieldOf("redstone", false).forGetter(IsolatorSettings::redstone)
    ).apply(instance, IsolatorSettings::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, IsolatorSettings> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    /** Whether {@code lens}, on the light before it, gets through. */
    public boolean passes(Lens lens) {
        if (!(lens == Lens.REDSTONE ? redstone : lens.action ? actions : modifiers)) return false;
        boolean any = false;
        boolean listed = false;
        for (ItemStack entry : lenses) {
            if (!(entry.getItem() instanceof LensItem item)) continue;
            any = true;
            listed |= item.lens() == lens;
        }
        return !any || listed == allow;
    }

    public ItemStack entry(int slot) {
        return slot < lenses.size() ? lenses.get(slot) : ItemStack.EMPTY;
    }

    public IsolatorSettings withEntry(int slot, ItemStack stack) {
        List<ItemStack> list = new ArrayList<>(lenses);
        while (list.size() < SLOTS) list.add(ItemStack.EMPTY);
        list.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        return new IsolatorSettings(logistics, actions, modifiers, list, allow, power, redstone);
    }

    public IsolatorSettings withLogistics(boolean on) {
        return new IsolatorSettings(on, actions, modifiers, lenses, allow, power, redstone);
    }

    public IsolatorSettings withActions(boolean on) {
        return new IsolatorSettings(logistics, on, modifiers, lenses, allow, power, redstone);
    }

    public IsolatorSettings withModifiers(boolean on) {
        return new IsolatorSettings(logistics, actions, on, lenses, allow, power, redstone);
    }

    public IsolatorSettings withAllow(boolean on) {
        return new IsolatorSettings(logistics, actions, modifiers, lenses, on, power, redstone);
    }

    public IsolatorSettings withPower(boolean on) {
        return new IsolatorSettings(logistics, actions, modifiers, lenses, allow, on, redstone);
    }

    public IsolatorSettings withRedstone(boolean on) {
        return new IsolatorSettings(logistics, actions, modifiers, lenses, allow, power, on);
    }

    /** By value: an {@link ItemStack} only equals itself (see {@link LensFilter#equals}). */
    @Override
    public boolean equals(Object other) {
        return other instanceof IsolatorSettings that && logistics == that.logistics && actions == that.actions
                && modifiers == that.modifiers && allow == that.allow && power == that.power && redstone == that.redstone && ItemStack.listMatches(lenses, that.lenses);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(logistics, actions, modifiers, allow, power, redstone, ItemStack.hashStackList(lenses));
    }
}

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
 * What an Isolator Lens lets through of the light before it. Power always passes, at the light's reach. Set off,
 * {@code logistics} stops a controller's network there, {@code actions} what the light does, and {@code modifiers}
 * what changes it; listed lenses are let through only (allow) or stopped (deny), among what the switches pass.
 * Unset, it lets power through and nothing else, as the Isolator block does.
 */
public record IsolatorSettings(boolean logistics, boolean actions, boolean modifiers, List<ItemStack> lenses, boolean allow) {

    public static final int SLOTS = 9;
    public static final IsolatorSettings POWER_ONLY = new IsolatorSettings(false, false, false, List.of(), false);

    public IsolatorSettings {
        lenses = List.copyOf(lenses);
    }

    public static final Codec<IsolatorSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("logistics", false).forGetter(IsolatorSettings::logistics),
            Codec.BOOL.optionalFieldOf("actions", false).forGetter(IsolatorSettings::actions),
            Codec.BOOL.optionalFieldOf("modifiers", false).forGetter(IsolatorSettings::modifiers),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("lenses", List.of()).forGetter(IsolatorSettings::lenses),
            Codec.BOOL.optionalFieldOf("allow", false).forGetter(IsolatorSettings::allow)
    ).apply(instance, IsolatorSettings::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, IsolatorSettings> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    /** Whether {@code lens}, on the light before it, gets through. */
    public boolean passes(Lens lens) {
        if (!(lens.action ? actions : modifiers)) return false;
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
        return new IsolatorSettings(logistics, actions, modifiers, list, allow);
    }

    public IsolatorSettings withLogistics(boolean on) {
        return new IsolatorSettings(on, actions, modifiers, lenses, allow);
    }

    public IsolatorSettings withActions(boolean on) {
        return new IsolatorSettings(logistics, on, modifiers, lenses, allow);
    }

    public IsolatorSettings withModifiers(boolean on) {
        return new IsolatorSettings(logistics, actions, on, lenses, allow);
    }

    public IsolatorSettings withAllow(boolean on) {
        return new IsolatorSettings(logistics, actions, modifiers, lenses, on);
    }
}

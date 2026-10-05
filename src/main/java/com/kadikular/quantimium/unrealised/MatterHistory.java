package com.kadikular.quantimium.unrealised;

import com.kadikular.quantimium.init.ModDataComponents;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What Unrealised Matter has been through without being observed (game plan E3): the recipe types of
 * the catalysts it went through in a Quantum Crafter, in order, at most {@link #MAX_STEPS}. Recorded,
 * not applied: the Matter is still unrealised, and the steps are worked out with the pack's own
 * recipes for whatever ore it becomes. Matter only stacks with Matter of the same history.
 */
public record MatterHistory(List<Identifier> steps) {

    public static final int MAX_STEPS = 5;
    public static final MatterHistory NONE = new MatterHistory(List.of());

    public static final Codec<MatterHistory> CODEC =
            Identifier.CODEC.listOf(0, MAX_STEPS).xmap(MatterHistory::new, MatterHistory::steps);
    public static final StreamCodec<ByteBuf, MatterHistory> STREAM_CODEC =
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_STEPS)).map(MatterHistory::new, MatterHistory::steps);

    public MatterHistory {
        steps = List.copyOf(steps);
    }

    public static MatterHistory of(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.MATTER_HISTORY.get(), NONE);
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    public boolean full() {
        return steps.size() >= MAX_STEPS;
    }

    /** This history with {@code step} after it. */
    public MatterHistory then(Identifier step) {
        List<Identifier> next = new ArrayList<>(steps);
        next.add(step);
        return new MatterHistory(next);
    }

    /** {@code stack} (Matter) with this history; no history clears it, so fresh Matter stacks with fresh. */
    public ItemStack applyTo(ItemStack stack) {
        ItemStack copy = stack.copy();
        if (isEmpty()) copy.remove(ModDataComponents.MATTER_HISTORY.get());
        else copy.set(ModDataComponents.MATTER_HISTORY.get(), this);
        return copy;
    }

    /** "ground → smelted": each step's name, or its recipe type's path where there is none. */
    public MutableComponent describe() {
        MutableComponent line = Component.empty();
        for (int i = 0; i < steps.size(); i++) {
            if (i > 0) line.append(" → ");
            line.append(stepName(steps.get(i)));
        }
        return line;
    }

    /** A step's name: {@code unrealised.quantimium.step.<namespace>.<path>}, falling back to its path. */
    public static Component stepName(Identifier step) {
        String key = "unrealised.quantimium.step." + step.getNamespace() + "." + step.getPath().replace('/', '.');
        return Component.translatableWithFallback(key, step.getPath().replace('_', ' ').toLowerCase(Locale.ROOT));
    }
}

package com.kadikular.quantimium.compat.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.kadikular.quantimium.reactor.ReactorPlanner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A whole tree the Reactor makes in one go, offered to an ME network as one pattern: the raw things a
 * plan for one of an item uses up, in; the item and everything the plan has left over, out. AE2 sends
 * it once per run, and the Reactor makes the whole tree inside. Its definition is the ME Superposition
 * Port's item tagged with the item and the shape of the tree, so a tree planned again the same way is
 * the same pattern.
 */
public final class ReactorTreePattern implements IPatternDetails {

    private final ItemResource target;
    private final long fe;
    private final AEItemKey definition;
    private final IInput[] inputs;
    private final List<GenericStack> outputs;

    private ReactorTreePattern(ItemResource target, long fe, IInput[] inputs, List<GenericStack> outputs, int shape) {
        this.target = target;
        this.fe = fe;
        this.inputs = inputs;
        this.outputs = outputs;
        ItemStack marker = new ItemStack(Ae2Content.REACTOR_ME_PORT_ITEM.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("tree", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(target.getItem()).toString());
        tag.putInt("shape", shape);
        marker.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        this.definition = AEItemKey.of(marker);
    }

    /**
     * The pattern for {@code plan}, a plan for one or more of its target: what it uses up, in; the
     * target and its leftovers, out. Null for a plan that uses nothing up (nothing to ask the network for).
     */
    @Nullable
    public static ReactorTreePattern of(ReactorPlanner.Plan plan, long fe) {
        List<IInput> in = new ArrayList<>();
        List<GenericStack> used = new ArrayList<>();
        for (Map.Entry<ItemResource, Long> entry : plan.consumed().entrySet()) {
            if (entry.getValue() <= 0) continue;
            in.add(new Exact(AEItemKey.of(entry.getKey()), entry.getValue()));
            used.add(new GenericStack(AEItemKey.of(entry.getKey()), entry.getValue()));
        }
        if (in.isEmpty()) return null;
        // In a fixed order, so the same tree planned again is the same pattern.
        used.sort(java.util.Comparator.comparing(stack -> stack.what().toString()));
        List<GenericStack> out = new ArrayList<>();
        long made = plan.count() + plan.leftovers().getOrDefault(plan.target(), 0L);
        out.add(new GenericStack(AEItemKey.of(plan.target()), made));
        plan.leftovers().forEach((item, amount) -> {
            if (!item.equals(plan.target()) && amount > 0) out.add(new GenericStack(AEItemKey.of(item), amount));
        });
        return new ReactorTreePattern(plan.target(), fe, in.toArray(IInput[]::new), List.copyOf(out),
                Objects.hash(used, out));
    }

    public ItemResource target() {
        return target;
    }

    /** What one run costs the Reactor. */
    public long fe() {
        return fe;
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public IInput[] getInputs() {
        return inputs;
    }

    @Override
    public List<GenericStack> getOutputs() {
        return outputs;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ReactorTreePattern pattern && pattern.definition.equals(definition);
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }

    /** Exactly this item, data and all, {@code count} of it per run. */
    private record Exact(AEItemKey key, long count) implements IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[] {new GenericStack(key, 1)};
        }

        @Override
        public long getMultiplier() {
            return count;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return key.equals(input);
        }

        @Nullable
        @Override
        public AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }
}

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

    /** A tool a tree wears down, such as a cutting knife: any that fits, worn by {@code uses}. */
    public record Worn(net.minecraft.world.item.crafting.Ingredient tool, int uses) {}

    /**
     * The pattern for {@code plan}, a plan for one of its target: what it uses up, and the tools it
     * keeps, in; the target and its leftovers, out. A tool it {@code kept} (a press) is that very item,
     * handed back. A tool it {@code worn} (a knife) is any that fits, handed back worn: the plan's own
     * choice of knife, and the worn knife it leaves, are left out, so the pattern doesn't ask for one
     * knife in particular. Null for a plan that uses nothing up (nothing to ask the network for).
     */
    @Nullable
    public static ReactorTreePattern of(ReactorPlanner.Plan plan, long fe, List<ItemResource> kept, List<Worn> worn) {
        java.util.function.Predicate<ItemResource> wornTool =
                item -> worn.stream().anyMatch(tool -> tool.tool().test(item.toStack(1)));
        List<IInput> in = new ArrayList<>();
        List<String> shape = new ArrayList<>();
        for (Map.Entry<ItemResource, Long> entry : plan.consumed().entrySet()) {
            if (entry.getValue() <= 0 || wornTool.test(entry.getKey())) continue;
            in.add(new Exact(AEItemKey.of(entry.getKey()), entry.getValue(), false));
            shape.add(AEItemKey.of(entry.getKey()) + "x" + entry.getValue());
        }
        if (in.isEmpty()) return null;
        for (ItemResource tool : kept) {
            in.add(new Exact(AEItemKey.of(tool), 1, true));
            shape.add("keep " + AEItemKey.of(tool));
        }
        for (Worn tool : worn) {
            in.add(new ToolInput(tool.tool(), tool.uses()));
            shape.add("wear " + com.kadikular.quantimium.recipe.RecipeCompat.stacks(tool.tool()) + "x" + tool.uses());
        }
        List<GenericStack> out = new ArrayList<>();
        long made = plan.count() + plan.leftovers().getOrDefault(plan.target(), 0L);
        out.add(new GenericStack(AEItemKey.of(plan.target()), made));
        plan.leftovers().forEach((item, amount) -> {
            if (!item.equals(plan.target()) && amount > 0 && !wornTool.test(item)) {
                out.add(new GenericStack(AEItemKey.of(item), amount));
            }
        });
        // In a fixed order, so the same tree planned again is the same pattern.
        java.util.Collections.sort(shape);
        out.forEach(stack -> shape.add("out " + stack));
        return new ReactorTreePattern(plan.target(), fe, in.toArray(IInput[]::new), List.copyOf(out), shape.hashCode());
    }

    /** Whether all it asks for, but the tools it keeps or wears, are somewhere in {@code stock}. */
    public boolean findsItsInputsIn(Map<ItemResource, Long> stock) {
        for (IInput input : inputs) {
            if (input instanceof Exact exact && !exact.kept() && stock.getOrDefault(exact.key().toResource(), 0L) <= 0) {
                return false;
            }
        }
        return true;
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

    /** Exactly this item, data and all, {@code count} of it per run; a tool ({@code kept}) comes back. */
    private record Exact(AEItemKey key, long count, boolean kept) implements IInput {
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
            return kept ? template : null;
        }
    }
}

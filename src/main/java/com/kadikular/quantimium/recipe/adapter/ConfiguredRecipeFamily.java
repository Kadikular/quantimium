package com.kadikular.quantimium.recipe.adapter;

import com.kadikular.quantimium.recipe.RecipeCompat;
import com.kadikular.quantimium.recipe.RecipeShape;
import com.kadikular.quantimium.recipe.CraftEnergy;
import com.kadikular.quantimium.recipe.IngredientPool;
import com.kadikular.quantimium.recipe.ResolvedCraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies JSON {@link RecipeAdapter}s to foreign recipes. Prefer this over
 * {@link com.kadikular.quantimium.recipe.GenericItemFamily} when a recipe needs durable inputs
 * (presses), sized counts, or a specific energy flat fee.
 *
 * <p>Field values may be vanilla {@link Ingredient}s, NeoForge {@link SizedIngredient}s, or Mekanism
 * {@code ItemStackIngredient} wrappers around a sized ingredient.
 */
public final class ConfiguredRecipeFamily {

    private static final ConcurrentHashMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Method> INGREDIENT_ACCESSORS = new ConcurrentHashMap<>();

    private record CountedIngredient(Ingredient ingredient, int count) {}

    private ConfiguredRecipeFamily() {}

    public static Optional<ResolvedCraft> match(HolderLookup.Provider registries, RecipeHolder<?> holder,
                                                IngredientPool pool) {
        Recipe<?> recipe = holder.value();
        RecipeType<?> type = recipe.getType();
        List<RecipeAdapter> adapters = RecipeAdapterRegistry.INSTANCE.adaptersFor(type);
        if (adapters.isEmpty() || pool.isEmpty()) return Optional.empty();

        for (RecipeAdapter adapter : adapters) {
            Optional<ResolvedCraft> craft = matchWith(adapter, registries, holder, pool);
            if (craft.isPresent()) return craft;
        }
        return Optional.empty();
    }

    /** Items any configured adapter can see on this recipe, for the reverse index. */
    public static Set<Item> inputItems(Recipe<?> recipe) {
        if (recipe == null) return Set.of();
        List<RecipeAdapter> adapters = RecipeAdapterRegistry.INSTANCE.adaptersFor(recipe.getType());
        if (adapters.isEmpty()) return Set.of();
        Set<Item> items = new HashSet<>();
        for (RecipeAdapter adapter : adapters) {
            try {
                for (CountedIngredient counted : resolveInputs(adapter, recipe)) {
                    if (counted.ingredient().isEmpty()) continue;
                    for (ItemStack stack : RecipeCompat.stacks(counted.ingredient())) {
                        if (!stack.isEmpty()) items.add(stack.getItem());
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return items;
    }

    /**
     * The recipe in the abstract, through the first adapter that can read it. Recipes with an
     * ingredient that is never used up (an Inscriber's press) are left out: an ME pattern would take
     * the press from the network every run.
     */
    public static Optional<RecipeShape> describe(HolderLookup.Provider registries, RecipeHolder<?> holder) {
        Recipe<?> recipe = holder.value();
        for (RecipeAdapter adapter : RecipeAdapterRegistry.INSTANCE.adaptersFor(recipe.getType())) {
            try {
                List<CountedIngredient> counted = resolveInputs(adapter, recipe);
                List<ItemStack> outputs = resolveOutputs(adapter, recipe, registries);
                if (counted.isEmpty() || outputs.isEmpty()) continue;
                List<RecipeShape.Input> inputs = new ArrayList<>();
                List<Ingredient> tools = new ArrayList<>();
                boolean unknown = false;
                for (CountedIngredient input : counted) {
                    Ingredient ingredient = input.ingredient();
                    if (ingredient == null || ingredient.isEmpty()) {
                        if (adapter.skipEmptyIngredients()) continue;
                        unknown = true;
                        break;
                    }
                    // A press and the like: needed, never used up.
                    if (allNonConsumed(adapter, ingredient)) {
                        tools.add(ingredient);
                        continue;
                    }
                    inputs.add(new RecipeShape.Input(ingredient, Math.max(1, input.count())));
                }
                if (unknown || inputs.isEmpty()) continue;
                return Optional.of(new RecipeShape(holder.id().identifier(), inputs, outputs,
                        CraftEnergy.flat(energy(adapter, recipe)), tools));
            } catch (Throwable ignored) {
            }
        }
        return Optional.empty();
    }

    private static boolean allNonConsumed(RecipeAdapter adapter, Ingredient ingredient) {
        if (adapter.nonConsumedTags().isEmpty() && adapter.nonConsumedItems().isEmpty()) return false;
        boolean saw = false;
        for (ItemStack stack : RecipeCompat.stacks(ingredient)) {
            if (stack.isEmpty()) continue;
            saw = true;
            if (!isNonConsumed(adapter, stack)) return false;
        }
        return saw;
    }

    private static Optional<ResolvedCraft> matchWith(RecipeAdapter adapter, HolderLookup.Provider registries,
                                                     RecipeHolder<?> holder, IngredientPool pool) {
        try {
            Recipe<?> recipe = holder.value();
            List<CountedIngredient> inputs = resolveInputs(adapter, recipe);
            if (inputs.isEmpty()) return Optional.empty();

            List<ItemStack> outputs = resolveOutputs(adapter, recipe, registries);
            if (outputs.isEmpty()) return Optional.empty();

            IngredientPool.Allocator alloc = pool.allocator();
            List<IngredientPool.Withdrawal> withdrawals = new ArrayList<>();
            for (CountedIngredient counted : inputs) {
                Ingredient ingredient = counted.ingredient();
                if (ingredient == null || (ingredient.isEmpty() && adapter.skipEmptyIngredients())) continue;
                if (ingredient.isEmpty()) return Optional.empty();

                if (isDurableIngredient(adapter, ingredient, pool)) {
                    if (!poolHasMatch(ingredient, pool)) return Optional.empty();
                    continue;
                }

                List<IngredientPool.Withdrawal> taken = alloc.takeMatching(ingredient, Math.max(1, counted.count()));
                if (taken == null) return Optional.empty();
                withdrawals.addAll(taken);
            }
            if (withdrawals.isEmpty()) return Optional.empty();

            int cost = CraftEnergy.flat(energy(adapter, recipe));
            return Optional.of(new ResolvedCraft(holder.id().identifier(), withdrawals, outputs, cost));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    private static List<CountedIngredient> resolveInputs(RecipeAdapter adapter, Recipe<?> recipe) throws Exception {
        return switch (adapter.inputMode()) {
            case INGREDIENTS -> {
                List<CountedIngredient> list = new ArrayList<>();
                for (Ingredient ingredient : RecipeCompat.ingredients(recipe)) {
                    list.addAll(unwrap(ingredient));
                }
                yield list;
            }
            case FIELD -> {
                List<CountedIngredient> list = new ArrayList<>();
                for (String name : adapter.inputFields()) {
                    list.addAll(unwrap(field(recipe, name)));
                }
                yield list;
            }
        };
    }

    private static List<CountedIngredient> unwrap(Object value) {
        if (value == null) return List.of();
        // Optional inputs, such as an inscriber's top and bottom slots.
        if (value instanceof Optional<?> optional) return optional.map(ConfiguredRecipeFamily::unwrap).orElse(List.of());
        if (value instanceof Ingredient ingredient) {
            return ingredient.isEmpty() ? List.of() : List.of(new CountedIngredient(ingredient, 1));
        }
        if (value instanceof SizedIngredient sized) {
            Ingredient inner = sized.ingredient();
            if (inner == null || inner.isEmpty()) return List.of();
            return List.of(new CountedIngredient(inner, Math.max(1, sized.count())));
        }
        if (value.getClass().isArray() && !value.getClass().getComponentType().isPrimitive()) {
            List<CountedIngredient> list = new ArrayList<>();
            for (int i = 0; i < java.lang.reflect.Array.getLength(value); i++) {
                list.addAll(unwrap(java.lang.reflect.Array.get(value, i)));
            }
            return list;
        }
        if (value instanceof Iterable<?> iterable && !(value instanceof ItemStack)) {
            List<CountedIngredient> list = new ArrayList<>();
            for (Object element : iterable) {
                list.addAll(unwrap(element));
            }
            return list;
        }

        Method accessor = INGREDIENT_ACCESSORS.computeIfAbsent(value.getClass(), type -> {
            for (String name : List.of("ingredient", "getIngredient", "input", "getInput")) {
                try {
                    Method method = type.getMethod(name);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
            }
            return null;
        });
        if (accessor != null) {
            try {
                Object inner = accessor.invoke(value);
                if (inner != null && inner != value) {
                    // A wrapper with its own count, such as Energized Power's IngredientWithCount.
                    int count = count(value);
                    List<CountedIngredient> found = unwrap(inner);
                    if (count > 1 && found.size() == 1) return List.of(new CountedIngredient(found.getFirst().ingredient(), count));
                    return found;
                }
            } catch (Throwable ignored) {
            }
        }
        return List.of();
    }

    private static int count(Object wrapper) {
        for (String name : List.of("count", "getCount", "amount")) {
            try {
                Method method = wrapper.getClass().getMethod(name);
                if (method.invoke(wrapper) instanceof Number number) return number.intValue();
            } catch (Throwable ignored) {
            }
        }
        return 1;
    }

    /** What a run always gives, the main output first. Empty when it can't be read. */
    private static List<ItemStack> resolveOutputs(RecipeAdapter adapter, Recipe<?> recipe,
                                                  HolderLookup.Provider registries) throws Exception {
        return switch (adapter.outputMode()) {
            case RESULT_ITEM -> {
                ItemStack result = RecipeCompat.result(recipe, registries);
                yield result == null || result.isEmpty() ? List.of() : List.of(result.copy());
            }
            case FIELD -> {
                List<ItemStack> outputs = new ArrayList<>();
                for (String name : adapter.outputFields()) {
                    // One adapter can serve several recipe classes: a field one of them lacks gives nothing.
                    try {
                        outputs.addAll(GuaranteedOutputs.read(field(recipe, name)));
                    } catch (NoSuchFieldException absent) {
                        // not this class's
                    }
                }
                yield outputs;
            }
        };
    }

    /** The base FE for one run: a recipe's own energy field, scaled, or the adapter's flat fee. */
    private static int energy(RecipeAdapter adapter, Recipe<?> recipe) {
        RecipeAdapter.EnergySpec spec = adapter.energy();
        if (spec.field() != null) {
            try {
                if (field(recipe, spec.field()) instanceof Number number && number.doubleValue() > 0) {
                    return (int) Math.min(Integer.MAX_VALUE, Math.round(number.doubleValue() * spec.scale()));
                }
            } catch (Exception ignored) {
            }
        }
        return spec.flat();
    }

    /**
     * An ingredient is durable when every item it accepts is marked non-consumed, or when the pool
     * can satisfy it with a non-consumed stack (presses sitting next to consumable substitutes).
     */
    private static boolean isDurableIngredient(RecipeAdapter adapter, Ingredient ingredient,
                                               IngredientPool pool) {
        if (adapter.nonConsumedTags().isEmpty() && adapter.nonConsumedItems().isEmpty()) return false;

        boolean sawItem = false;
        boolean allListed = true;
        for (ItemStack stack : RecipeCompat.stacks(ingredient)) {
            if (stack.isEmpty()) continue;
            sawItem = true;
            if (!isNonConsumed(adapter, stack)) {
                allListed = false;
                break;
            }
        }
        if (sawItem && allListed) return true;

        for (IngredientPool.Offer offer : pool.offers()) {
            if (ingredient.test(offer.stack()) && isNonConsumed(adapter, offer.stack())) return true;
        }
        return false;
    }

    private static boolean isNonConsumed(RecipeAdapter adapter, ItemStack stack) {
        for (TagKey<Item> tag : adapter.nonConsumedTags()) {
            if (stack.is(tag)) return true;
        }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId != null && adapter.nonConsumedItems().contains(itemId);
    }

    private static boolean poolHasMatch(Ingredient ingredient, IngredientPool pool) {
        for (IngredientPool.Offer offer : pool.offers()) {
            if (ingredient.test(offer.stack())) return true;
        }
        return false;
    }

    private static Object field(Object target, String name) throws Exception {
        String key = target.getClass().getName() + "#" + name;
        Field field = FIELD_CACHE.computeIfAbsent(key, ignored -> {
            Class<?> type = target.getClass();
            while (type != null) {
                try {
                    Field found = type.getDeclaredField(name);
                    found.setAccessible(true);
                    return found;
                } catch (NoSuchFieldException e) {
                    type = type.getSuperclass();
                }
            }
            return null;
        });
        if (field == null) throw new NoSuchFieldException(name);
        return field.get(target);
    }
}

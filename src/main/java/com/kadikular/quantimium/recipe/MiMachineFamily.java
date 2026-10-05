package com.kadikular.quantimium.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Best-effort Modern Industrialization {@code MachineRecipe} matching via reflection so Quantimium
 * does not hard-depend on MI at compile time. Chance outputs (probability &lt; 1) are dropped —
 * sacrificed to the quantum.
 */
public final class MiMachineFamily {

    /** Charged per tick for the rare MI recipe that declares no EU draw. */
    private static final int UNPOWERED_FE_PER_TICK = 10;

    private static final String MI_RECIPE = "aztech.modern_industrialization.machines.recipe.MachineRecipe";
    private static final Map<Class<?>, Boolean> SUPPORT_CACHE = new ConcurrentHashMap<>();

    private MiMachineFamily() {}

    public static boolean supports(Recipe<?> recipe) {
        if (recipe == null) return false;
        return SUPPORT_CACHE.computeIfAbsent(recipe.getClass(), MiMachineFamily::classIsMiMachineRecipe);
    }

    private static boolean classIsMiMachineRecipe(Class<?> type) {
        Class<?> c = type;
        while (c != null) {
            if (c.getName().equals(MI_RECIPE)) return true;
            c = c.getSuperclass();
        }
        return false;
    }

    public static Optional<ResolvedCraft> match(RecipeHolder<?> holder, IngredientPool pool) {
        Recipe<?> recipe = holder.value();
        if (!supports(recipe) || pool.isEmpty()) return Optional.empty();

        try {
            List<?> itemInputs = (List<?>) field(recipe, "itemInputs");
            List<?> fluidInputs = (List<?>) field(recipe, "fluidInputs");
            List<?> itemOutputs = (List<?>) field(recipe, "itemOutputs");
            int eu = (Integer) field(recipe, "eu");
            int duration = (Integer) field(recipe, "duration");

            // Fluids unsupported in v1 — skip fluid recipes.
            if (fluidInputs != null && !fluidInputs.isEmpty()) return Optional.empty();
            if (itemInputs == null || itemInputs.isEmpty()) return Optional.empty();

            IngredientPool.Allocator alloc = pool.allocator();
            List<IngredientPool.Withdrawal> withdrawals = new ArrayList<>();

            for (Object inputObj : itemInputs) {
                float probability = ((Number) call(inputObj, "probability")).floatValue();
                // Probabilistic inputs are optional; skip them for matching (don't require).
                if (probability < 1.0f) continue;

                Ingredient ingredient = (Ingredient) call(inputObj, "ingredient");
                int amount = ((Number) call(inputObj, "amount")).intValue();
                if (amount <= 0) continue;

                List<IngredientPool.Withdrawal> taken = alloc.takeMatching(
                        stack -> matchesIngredient(ingredient, inputObj, stack), amount);
                if (taken == null) return Optional.empty();
                withdrawals.addAll(taken);
            }

            List<ItemStack> outputs = new ArrayList<>();
            if (itemOutputs != null) {
                for (Object outObj : itemOutputs) {
                    float probability = ((Number) call(outObj, "probability")).floatValue();
                    // Guaranteed outputs only — chance extras are voided.
                    if (probability < 1.0f) continue;
                    ItemStack stack = (ItemStack) call(outObj, "getStack");
                    if (stack != null && !stack.isEmpty()) outputs.add(stack.copy());
                }
            }
            if (outputs.isEmpty()) return Optional.empty();

            int ticks = Math.max(duration, 1);
            int cost = eu > 0
                    ? CraftEnergy.fromEu((long) eu * ticks)
                    : CraftEnergy.flat((long) ticks * UNPOWERED_FE_PER_TICK);

            return Optional.of(new ResolvedCraft(holder.id().identifier(), withdrawals, outputs, cost));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    /**
     * The recipe in the abstract. Recipes with fluid inputs, or with inputs that are only sometimes
     * used up (tools, catalysts), are left out: an ME pattern needs a fixed list of what goes in.
     */
    public static Optional<RecipeShape> describe(RecipeHolder<?> holder) {
        Recipe<?> recipe = holder.value();
        if (!supports(recipe)) return Optional.empty();
        try {
            List<?> itemInputs = (List<?>) field(recipe, "itemInputs");
            List<?> fluidInputs = (List<?>) field(recipe, "fluidInputs");
            List<?> itemOutputs = (List<?>) field(recipe, "itemOutputs");
            if (fluidInputs != null && !fluidInputs.isEmpty()) return Optional.empty();
            if (itemInputs == null || itemInputs.isEmpty()) return Optional.empty();

            List<RecipeShape.Input> inputs = new ArrayList<>();
            for (Object inputObj : itemInputs) {
                float probability = ((Number) call(inputObj, "probability")).floatValue();
                if (probability < 1.0f) return Optional.empty();
                Ingredient ingredient = (Ingredient) call(inputObj, "ingredient");
                int amount = ((Number) call(inputObj, "amount")).intValue();
                if (ingredient == null || ingredient.isEmpty() || amount <= 0) return Optional.empty();
                inputs.add(new RecipeShape.Input(ingredient, amount));
            }
            List<ItemStack> outputs = new ArrayList<>();
            if (itemOutputs != null) {
                for (Object outObj : itemOutputs) {
                    float probability = ((Number) call(outObj, "probability")).floatValue();
                    if (probability < 1.0f) continue;
                    ItemStack stack = (ItemStack) call(outObj, "getStack");
                    if (stack != null && !stack.isEmpty()) outputs.add(stack.copy());
                }
            }
            if (outputs.isEmpty()) return Optional.empty();
            return Optional.of(new RecipeShape(holder.id().identifier(), inputs, outputs, cost(recipe)));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    private static int cost(Recipe<?> recipe) throws Exception {
        int eu = (Integer) field(recipe, "eu");
        int ticks = Math.max((Integer) field(recipe, "duration"), 1);
        return eu > 0
                ? CraftEnergy.fromEu((long) eu * ticks)
                : CraftEnergy.flat((long) ticks * UNPOWERED_FE_PER_TICK);
    }

    private static boolean matchesIngredient(Ingredient ingredient, Object inputObj, ItemStack stack) {
        try {
            if (ingredient != null && !ingredient.isEmpty() && ingredient.test(stack)) return true;
            // Fallback: ItemInput.matches(ItemStack)
            Object result = call(inputObj, "matches", stack);
            return result instanceof Boolean b && b;
        } catch (Throwable t) {
            return ingredient != null && ingredient.test(stack);
        }
    }

    /** Items any input of this recipe accepts, for building the item → recipe index. */
    public static Set<Item> inputItems(Recipe<?> recipe) {
        if (!supports(recipe)) return Set.of();
        try {
            List<?> itemInputs = (List<?>) field(recipe, "itemInputs");
            if (itemInputs == null || itemInputs.isEmpty()) return Set.of();
            Set<Item> items = new HashSet<>();
            for (Object inputObj : itemInputs) {
                Ingredient ingredient = (Ingredient) call(inputObj, "ingredient");
                if (ingredient == null) continue;
                for (ItemStack stack : RecipeCompat.stacks(ingredient)) {
                    if (!stack.isEmpty()) items.add(stack.getItem());
                }
            }
            return items;
        } catch (Throwable t) {
            return Set.of();
        }
    }

    private record MemberKey(Class<?> owner, String name, int arity) {}

    private static final Map<MemberKey, Field> FIELDS = new ConcurrentHashMap<>();
    private static final Map<MemberKey, Method> METHODS = new ConcurrentHashMap<>();

    private static Object field(Object target, String name) throws Exception {
        // Resolving these per call was the dominant cost of matching a few thousand MI recipes.
        Field f = FIELDS.computeIfAbsent(new MemberKey(target.getClass(), name, -1), key -> {
            Field found = findField(key.owner(), key.name());
            if (found != null) found.setAccessible(true);
            return found;
        });
        if (f == null) throw new NoSuchFieldException(name);
        return f.get(target);
    }

    @Nullable
    private static Field findField(Class<?> type, String name) {
        Class<?> c = type;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        Method m = METHODS.computeIfAbsent(new MemberKey(target.getClass(), name, args.length), key -> {
            for (Method candidate : key.owner().getMethods()) {
                if (!candidate.getName().equals(key.name())) continue;
                if (candidate.getParameterCount() != key.arity()) continue;
                candidate.setAccessible(true);
                return candidate;
            }
            return null;
        });
        if (m == null) throw new NoSuchMethodException(name);
        return m.invoke(target, args);
    }
}

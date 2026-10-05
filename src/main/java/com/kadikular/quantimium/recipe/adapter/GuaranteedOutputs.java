package com.kadikular.quantimium.recipe.adapter;

import com.kadikular.quantimium.recipe.RecipeCompat;
import com.mojang.datafixers.util.Either;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a recipe field always gives, read without knowing its mod's classes. Item stacks and stack
 * templates are taken as they are. A wrapper with a {@code chance} counts only when it can't miss; one
 * with {@code percentages} (a roll per item, as Energized Power writes them) counts the rolls that are
 * certain; one with a {@code min} gives at least that many. Anything else is looked into for a stack.
 */
final class GuaranteedOutputs {

    private static final List<String> INNER = List.of("output", "stack", "item", "result", "ingredient", "value");

    private GuaranteedOutputs() {}

    static List<ItemStack> read(Object value) {
        List<ItemStack> out = new ArrayList<>();
        collect(value, out, 0);
        out.removeIf(ItemStack::isEmpty);
        return out;
    }

    private static void collect(Object value, List<ItemStack> out, int depth) {
        if (value == null || depth > 4) return;
        if (value instanceof ItemStack stack) {
            out.add(stack.copy());
            return;
        }
        if (value instanceof ItemStackTemplate template) {
            out.add(template.create());
            return;
        }
        if (value instanceof Optional<?> optional) {
            optional.ifPresent(inner -> collect(inner, out, depth + 1));
            return;
        }
        if (value instanceof Either<?, ?> either) {
            either.ifLeft(left -> collect(left, out, depth + 1)).ifRight(right -> collect(right, out, depth + 1));
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) collect(element, out, depth + 1);
            return;
        }
        if (value.getClass().isArray()) {
            if (value.getClass().getComponentType().isPrimitive()) return;
            for (int i = 0; i < Array.getLength(value); i++) collect(Array.get(value, i), out, depth + 1);
            return;
        }
        if (value instanceof Ingredient ingredient) {
            // An output given as an ingredient (Productive Bees): its first item.
            List<ItemStack> stacks = RecipeCompat.stacks(ingredient);
            if (!stacks.isEmpty()) out.add(stacks.getFirst().copyWithCount(1));
            return;
        }
        if (value.getClass().getName().startsWith("net.minecraft.") || value.getClass().getName().startsWith("java.")) return;

        // A mod's own wrapper: how many it surely gives, then what.
        Object chance = field(value, "chance");
        if (chance instanceof Number number && number.doubleValue() < 1.0) return;
        int count = -1;
        Object percentages = field(value, "percentages");
        if (percentages instanceof double[] rolls) {
            count = 0;
            for (double roll : rolls) if (roll >= 1.0) count++;
            if (count == 0) return;
        }
        Object min = field(value, "min");
        if (count < 0 && min instanceof Number number) {
            count = number.intValue();
            if (count <= 0) return;
        }
        for (String name : INNER) {
            Object inner = field(value, name);
            if (inner == null || inner == value) continue;
            List<ItemStack> found = new ArrayList<>();
            collect(inner, found, depth + 1);
            if (found.isEmpty()) continue;
            for (ItemStack stack : found) out.add(count > 0 ? stack.copyWithCount(count) : stack);
            return;
        }
    }

    private static Object field(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                if (Modifier.isStatic(field.getModifiers())) return null;
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                // look further up
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }
}

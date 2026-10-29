package com.kadikular.quantimium.recipe;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The fluids a recipe takes and gives, read off its fields: any field holding a {@link SizedFluidIngredient},
 * {@link FluidIngredient}, {@link FluidStack} or {@link FluidStackTemplate}, or a mod's own fluid ingredient (anything
 * with a {@code test(FluidStack)}), alone or in a list, an array, an {@link Optional} or an {@link Either}. So most
 * mods' machines are read without an adapter; an adapter naming its fields wins where there is one.
 *
 * <p>Which side a field is on comes from its name ({@code input}, {@code ingredient}; {@code output},
 * {@code result}), and failing that from its type: an ingredient is taken, a stack given.
 */
public final class FluidParts {

    /** One fluid a recipe takes: which fluids will do, and how many mB. */
    public record Input(List<Fluid> fluids, int amount, Predicate<FluidStack> test) {
        public Input {
            fluids = List.copyOf(fluids);
        }

        public boolean matches(FluidStack fluid) {
            return !fluid.isEmpty() && test.test(fluid.copyWithAmount(amount));
        }
    }

    /** What a recipe takes and gives in fluids. */
    public record Parts(List<Input> inputs, List<FluidStack> outputs) {
        public static final Parts NONE = new Parts(List.of(), List.of());

        public Parts {
            inputs = List.copyOf(inputs);
            outputs = outputs.stream().map(FluidStack::copy).toList();
        }

        public boolean isEmpty() {
            return inputs.isEmpty() && outputs.isEmpty();
        }
    }

    /** Which side a field is on: by its name, else by what it holds. */
    private enum Side { INPUT, OUTPUT, EITHER }

    private record Part(Field field, Side side) {}

    /** The fields of each recipe class that may hold fluids, found once. */
    private static final Map<Class<?>, List<Part>> FIELDS = new ConcurrentHashMap<>();
    /** Each class of a mod's own fluid ingredient: its {@code test(FluidStack)}, and where its amount is. */
    private static final Map<Class<?>, Optional<Method>> TESTS = new ConcurrentHashMap<>();

    private FluidParts() {}

    /** The fluids {@code recipe} takes and gives, read off whichever of its fields hold them. */
    public static Parts of(Object recipe) {
        List<Part> parts = FIELDS.computeIfAbsent(recipe.getClass(), FluidParts::find);
        if (parts.isEmpty()) return Parts.NONE;
        Collector into = new Collector();
        Side mode = modeOf(recipe);
        for (Part part : parts) {
            try {
                into.add(part.field().get(recipe), part.side() == Side.EITHER ? mode : part.side(), 0);
            } catch (Throwable ignored) {
                // A field that won't be read: as if it held nothing.
            }
        }
        return into.parts();
    }

    /**
     * A field named {@code mode} saying which way the fluid goes, as Energized Power's Fluid Transposer's does: filling,
     * its fluid is taken; emptying, given. Otherwise either, by type.
     */
    private static Side modeOf(Object recipe) {
        Field field = MODES.computeIfAbsent(recipe.getClass(), FluidParts::modeField).orElse(null);
        if (field == null) return Side.EITHER;
        Object mode;
        try {
            mode = field.get(recipe);
        } catch (Throwable t) {
            return Side.EITHER;
        }
        if (!(mode instanceof Enum<?> value)) return Side.EITHER;
        String name = value.name().toUpperCase(Locale.ROOT);
        if (name.contains("FILL")) return Side.INPUT;
        if (name.contains("EMPT") || name.contains("DRAIN")) return Side.OUTPUT;
        return Side.EITHER;
    }

    /** Each recipe class's {@code mode} field, if it has one that's an enum. */
    private static final Map<Class<?>, Optional<Field>> MODES = new ConcurrentHashMap<>();

    private static Optional<Field> modeField(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField("mode");
                if (!field.getType().isEnum()) return Optional.empty();
                field.setAccessible(true);
                return Optional.of(field);
            } catch (NoSuchFieldException e) {
                // up to the superclass
            } catch (Throwable t) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** {@code parts}, taking besides as many mB of each fluid as the number in its field says (none, if it's 0). */
    public static Parts withAmounts(Object recipe, Parts parts, Map<String, net.minecraft.resources.Identifier> amounts) {
        List<Input> inputs = new ArrayList<>(parts.inputs());
        amounts.forEach((name, id) -> {
            Fluid fluid = BuiltInRegistries.FLUID.getValue(id);
            if (fluid == null || fluid == Fluids.EMPTY || !(read(recipe, name) instanceof Number number) || number.intValue() <= 0) return;
            inputs.add(new Input(List.of(fluid), number.intValue(), each -> each.getFluid().isSame(fluid)));
        });
        return new Parts(inputs, parts.outputs());
    }

    /** As {@link #of(Object)}, from the named fields only (an adapter's). */
    public static Parts of(Object recipe, List<String> inputFields, List<String> outputFields) {
        Collector into = new Collector();
        for (String name : inputFields) into.add(read(recipe, name), Side.INPUT, 0);
        for (String name : outputFields) into.add(read(recipe, name), Side.OUTPUT, 0);
        return into.parts();
    }

    @Nullable
    private static Object read(Object recipe, String name) {
        for (Class<?> c = recipe.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(recipe);
            } catch (NoSuchFieldException e) {
                // up to the superclass
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static List<Part> find(Class<?> type) {
        List<Part> parts = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !fluidish(field.getGenericType(), 0)) continue;
                try {
                    field.setAccessible(true);
                } catch (Throwable t) {
                    continue;
                }
                parts.add(new Part(field, sideOf(field.getName())));
            }
        }
        return List.copyOf(parts);
    }

    private static Side sideOf(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("out") || lower.contains("result") || lower.contains("product")) return Side.OUTPUT;
        if (lower.startsWith("in") || lower.contains("input") || lower.contains("ingredient") || lower.contains("consum")
                || lower.contains("require")) return Side.INPUT;
        return Side.EITHER;
    }

    /** Whether a field of this type may hold a fluid. */
    private static boolean fluidish(Type type, int depth) {
        if (depth > 3) return false;
        if (type instanceof ParameterizedType parameterized) {
            if (!(parameterized.getRawType() instanceof Class<?> raw)) return false;
            if (!Collection.class.isAssignableFrom(raw) && raw != Optional.class && raw != Either.class) return false;
            for (Type argument : parameterized.getActualTypeArguments()) if (fluidish(argument, depth + 1)) return true;
            return false;
        }
        if (type instanceof GenericArrayType array) return fluidish(array.getGenericComponentType(), depth + 1);
        if (!(type instanceof Class<?> raw)) return false;
        if (raw.isArray()) return fluidish(raw.getComponentType(), depth + 1);
        return raw == FluidStack.class || raw == FluidStackTemplate.class || raw == SizedFluidIngredient.class
                || FluidIngredient.class.isAssignableFrom(raw) || testOf(raw).isPresent();
    }

    /** A mod's own fluid ingredient's {@code test(FluidStack)}, if it has one. */
    private static Optional<Method> testOf(Class<?> type) {
        if (type.isPrimitive() || type.isEnum() || type.getName().startsWith("java.")) return Optional.empty();
        return TESTS.computeIfAbsent(type, c -> {
            try {
                Method test = c.getMethod("test", FluidStack.class);
                if (test.getReturnType() != boolean.class) return Optional.empty();
                test.setAccessible(true);
                return Optional.of(test);
            } catch (Throwable t) {
                return Optional.empty();
            }
        });
    }

    /** Gathers what the fields hold. */
    private static final class Collector {
        private final List<Input> inputs = new ArrayList<>();
        private final List<FluidStack> outputs = new ArrayList<>();

        Parts parts() {
            return inputs.isEmpty() && outputs.isEmpty() ? Parts.NONE : new Parts(inputs, outputs);
        }

        void add(@Nullable Object value, Side side, int depth) {
            if (value == null || depth > 3) return;
            switch (value) {
                case Optional<?> optional -> optional.ifPresent(inner -> add(inner, side, depth + 1));
                case Either<?, ?> either -> {
                    either.left().ifPresent(inner -> add(inner, side, depth + 1));
                    either.right().ifPresent(inner -> add(inner, side, depth + 1));
                }
                case Collection<?> many -> {
                    for (Object inner : many) add(inner, side, depth + 1);
                }
                case SizedFluidIngredient sized -> {
                    if (side != Side.OUTPUT) input(sized.ingredient(), sized.amount());
                }
                case FluidIngredient ingredient -> {
                    if (side != Side.OUTPUT) input(ingredient, FluidType.BUCKET_VOLUME);
                }
                case FluidStack stack -> stack(stack, side);
                case FluidStackTemplate template -> stack(template.create(), side);
                default -> {
                    if (value.getClass().isArray()) {
                        for (int i = 0; i < Array.getLength(value); i++) add(Array.get(value, i), side, depth + 1);
                        return;
                    }
                    if (side != Side.OUTPUT) duck(value);
                }
            }
        }

        private void stack(FluidStack stack, Side side) {
            if (stack.isEmpty()) return;
            // A stack named as taken is that fluid exactly; otherwise it's what's given.
            if (side == Side.INPUT) {
                FluidStack wanted = stack.copy();
                inputs.add(new Input(List.of(stack.getFluid()), stack.getAmount(), each -> FluidStack.isSameFluidSameComponents(each, wanted)));
            } else {
                outputs.add(stack.copy());
            }
        }

        private void input(FluidIngredient ingredient, int amount) {
            if (amount <= 0) return;
            List<Fluid> fluids = ingredient.fluids().stream().map(Holder::value).toList();
            if (fluids.isEmpty()) return;
            inputs.add(new Input(fluids, amount, ingredient));
        }

        /** A mod's own fluid ingredient: what it accepts, tried on every fluid there is; its amount, if it says. */
        private void duck(Object value) {
            Optional<Method> test = testOf(value.getClass());
            if (test.isEmpty()) return;
            int amount = amountOf(value);
            Predicate<FluidStack> accepts = stack -> {
                try {
                    return (Boolean) test.get().invoke(value, stack);
                } catch (Throwable t) {
                    return false;
                }
            };
            List<Fluid> fluids = new ArrayList<>();
            for (Fluid fluid : BuiltInRegistries.FLUID) {
                if (fluid == Fluids.EMPTY || !fluid.isSource(fluid.defaultFluidState())) continue;
                if (accepts.test(new FluidStack(fluid, amount))) fluids.add(fluid);
            }
            if (!fluids.isEmpty()) inputs.add(new Input(fluids, amount, accepts));
        }

        /** {@code amount()}, {@code getAmount()} or an {@code amount} field; a bucket if none. */
        private static int amountOf(Object value) {
            for (String name : new String[] {"amount", "getAmount"}) {
                try {
                    Method method = value.getClass().getMethod(name);
                    if (method.getReturnType() == int.class) {
                        method.setAccessible(true);
                        return (Integer) method.invoke(value);
                    }
                } catch (Throwable ignored) {
                    // the next way
                }
            }
            Object field = read(value, "amount");
            return field instanceof Integer amount && amount > 0 ? amount : FluidType.BUCKET_VOLUME;
        }
    }
}

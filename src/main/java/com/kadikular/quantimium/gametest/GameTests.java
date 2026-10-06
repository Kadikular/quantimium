package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.gametest.compat.Ae2CompatTests;
import com.kadikular.quantimium.gametest.compat.HnnCompatTests;
import com.kadikular.quantimium.gametest.compat.MiCompatTests;
import com.kadikular.quantimium.gametest.compat.MiTickTimingTests;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Registers the {@link GameTest} methods below as test functions and test instances. Each batch name becomes its
 * own (empty) test environment, because tests only run together when they share one and every batch waits for
 * the one before it.
 *
 * <p>The partner-mod tests are registered only when their mod is loaded: a test for a missing mod would
 * either fail for no reason or pass without testing anything.
 */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class GameTests {

    private GameTests() {}

    private static List<Class<?>> classes() {
        // -PexportStructures: only the guide's structures, written out, no tests.
        if (GuideStructures.DIRECTORY != null) return List.of(GuideStructures.class);
        List<Class<?>> classes = new ArrayList<>(List.of(
                AdvancementTests.class, BandRewardTests.class, ChamberTests.class, CrafterTests.class, CreativeEnergyCellTests.class,
                DockTests.class, FoldTests.class, ReactorTests.class, ReactorCountTests.class, FieldControlTests.class, FieldModelTests.class, FluxTests.class, HallTests.class,
                HarvesterTests.class, MirrorLensTests.class, MirrorTests.class, ProjectorTests.class,
                RiftTests.class, SimulatorTests.class, StabilizerTests.class, SuperpositionTests.class,
                TickTimingTests.class, TierTests.class, UnrealisedTests.class, VeiledTests.class, ZenoTests.class));
        if (ModList.get().isLoaded("modern_industrialization")) {
            classes.add(MiCompatTests.class);
            classes.add(MiTickTimingTests.class);
        }
        if (ModList.get().isLoaded("ae2")) {
            classes.add(Ae2CompatTests.class);
            classes.add(com.kadikular.quantimium.gametest.compat.Ae2ReactorPortTests.class);
        }
        if (ModList.get().isLoaded("hostilenetworks")) classes.add(HnnCompatTests.class);
        classes.add(com.kadikular.quantimium.gametest.compat.PartnerMachineTests.class);
        return classes;
    }

    /** ./gradlew runGameTestServer -PgtFilter=zenotests runs only the tests whose id contains the text. */
    private static final String FILTER = System.getProperty("quantimium.gametestFilter", "");

    private static List<Method> tests(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (method.isAnnotationPresent(GameTest.class) && Modifier.isStatic(method.getModifiers())
                    && id(type, method).getPath().contains(FILTER)) {
                method.setAccessible(true);
                methods.add(method);
            }
        }
        methods.sort((a, b) -> a.getName().compareTo(b.getName()));
        return methods;
    }

    private static Identifier id(Class<?> type, Method method) {
        return Identifier.fromNamespaceAndPath(Quantimium.MODID,
                type.getSimpleName().toLowerCase(Locale.ROOT) + "." + method.getName().toLowerCase(Locale.ROOT));
    }

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        if (!GameTestHooks.isGametestEnabled()) return;
        event.register(Registries.TEST_FUNCTION, helper -> {
            for (Class<?> type : classes()) {
                for (Method method : tests(type)) {
                    helper.register(id(type, method), (Consumer<GameTestHelper>) test -> invoke(method, test));
                }
            }
        });
    }

    private static void invoke(Method method, GameTestHelper helper) {
        try {
            method.invoke(null, helper);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            if (e.getCause() instanceof Error error) throw error;
            throw new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        Map<String, Holder<TestEnvironmentDefinition<?>>> environments = new HashMap<>();
        for (Class<?> type : classes()) {
            for (Method method : tests(type)) {
                GameTest test = method.getAnnotation(GameTest.class);
                Holder<TestEnvironmentDefinition<?>> environment = environments.computeIfAbsent(test.batch(),
                        batch -> event.registerEnvironment(
                                Identifier.fromNamespaceAndPath(Quantimium.MODID, "batch/" + batch),
                                new TestEnvironmentDefinition.AllOf(List.of())));
                String namespace = test.templateNamespace().isEmpty() ? Quantimium.MODID : test.templateNamespace();
                Identifier id = id(type, method);
                event.registerTest(id, new FunctionGameTestInstance(
                        ResourceKey.create(Registries.TEST_FUNCTION, id),
                        new TestData<>(environment, Identifier.fromNamespaceAndPath(namespace, test.template()),
                                test.timeoutTicks(), 0, true, Rotation.NONE, false, 1, 1, false, test.padding())));
            }
        }
    }
}

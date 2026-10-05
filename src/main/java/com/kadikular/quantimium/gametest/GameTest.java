package com.kadikular.quantimium.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A test method taking a {@code GameTestHelper}. {@link GameTests} registers each one as a test function and a
 * function test instance, since the annotation this used to be no longer exists.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GameTest {
    /** The structure the test runs in, under {@code data/<namespace>/structure/}. */
    String template();

    /** The structure's namespace; this mod's when empty. */
    String templateNamespace() default "";

    /** Tests in one batch run together, batches one after another. */
    String batch() default "default";

    /** How long the test may run before it fails. */
    int timeoutTicks() default 100;

    /**
     * Blocks of clear space round the structure. It widens the test's slot in the shared grid and is cleared
     * before the test, so a neighbour's leftovers (field, containment) sit further away.
     */
    int padding() default 0;
}

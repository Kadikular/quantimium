package com.kadikular.quantimium.compat.iris;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.renderer.QuantumRenderTypes;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * Tells Iris what each of our render pipelines is, so shader packs draw the Reactor's horizon,
 * Tesseract shells, beams and glows instead of skipping them or drawing them black. Through
 * reflection: Iris is optional, and nothing here runs without it.
 *
 * <p>{@code config/quantimium/iris.properties} can name another Iris program for a pipeline
 * ({@code flat_depth=BEACON_BEAM}), for a shader pack that draws one badly.
 */
public final class IrisPipelines {

    private static final String API = "net.irisshaders.iris.api.v0.IrisApi";
    private static final String PROGRAM = "net.irisshaders.iris.api.v0.IrisProgram";

    private IrisPipelines() {}

    public static void assign() {
        if (!ModList.get().isLoaded("iris")) return;
        Properties overrides = overrides();
        try {
            Class<?> apiType = Class.forName(API);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<Enum> programType = (Class<Enum>) Class.forName(PROGRAM);
            Object api = apiType.getMethod("getInstance").invoke(null);
            Method assign = apiType.getMethod("assignPipeline", RenderPipeline.class, programType);
            for (Map.Entry<RenderPipeline, String> entry : QuantumRenderTypes.SHADER_PROGRAMS.entrySet()) {
                String name = entry.getKey().getLocation().getPath().replace("pipeline/", "");
                String program = overrides.getProperty(name, entry.getValue()).trim().toUpperCase(java.util.Locale.ROOT);
                try {
                    @SuppressWarnings("unchecked")
                    Object value = Enum.valueOf(programType, program);
                    assign.invoke(api, entry.getKey(), value);
                } catch (IllegalArgumentException unknown) {
                    Quantimium.LOGGER.warn("Iris has no program {} for pipeline {}", program, name);
                }
            }
            Quantimium.LOGGER.info("Told Iris about {} render pipelines", QuantumRenderTypes.SHADER_PROGRAMS.size());
        } catch (ReflectiveOperationException | LinkageError e) {
            Quantimium.LOGGER.warn("Couldn't tell Iris about our render pipelines; shader packs may not draw them", e);
        }
    }

    private static Properties overrides() {
        Properties properties = new Properties();
        Path file = FMLPaths.CONFIGDIR.get().resolve(Quantimium.MODID).resolve("iris.properties");
        if (!Files.exists(file)) return properties;
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        } catch (IOException e) {
            Quantimium.LOGGER.warn("Couldn't read {}", file, e);
        }
        return properties;
    }
}

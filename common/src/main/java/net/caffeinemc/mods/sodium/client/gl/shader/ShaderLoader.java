package net.caffeinemc.mods.sodium.client.gl.shader;

import net.caffeinemc.mods.sodium.client.services.PlatformRuntimeInformation;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShaderLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger("Sodium-ShaderLoader");

    /**
     * Creates an OpenGL shader from GLSL sources. The GLSL source file should be made available on the classpath at the
     * path of `/assets/{namespace}/shaders/{path}`. User defines can be used to declare variables in the shader source
     * after the version header, allowing for conditional compilation with macro code.
     *
     * @param type The type of shader to create
     * @param name The identifier used to locate the shader source file
     * @param constants A list of constants for shader specialization
     * @return An OpenGL shader object compiled with the given user defines
     */
    public static GlShader loadShader(ShaderType type, ResourceLocation name, ShaderConstants constants) {
        var parsedShader = ShaderParser.parseShader(getShaderSource(name), constants);
        if (PlatformRuntimeInformation.INSTANCE.isDevelopmentEnvironment()) {
            LOGGER.info("Loaded shader {} with constants {}", name, constants);
            LOGGER.info(parsedShader.src());
        }
        return new GlShader(type, name, parsedShader);
    }

    public static String getShaderSource(ResourceLocation name) {
        String path = String.format("/assets/%s/shaders/%s", name.getNamespace(), name.getPath());

        try (InputStream in = ShaderLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new RuntimeException("Shader not found: " + path);
            }

            return IOUtils.toString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read shader source for " + path, e);
        }
    }
}

package me.jellysquid.mods.sodium.client.gl.shader;

import com.mojang.blaze3d.platform.GlStateManager;
import me.jellysquid.mods.sodium.client.gl.GlObject;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;

/**
 * An OpenGL shader program.
 */
public abstract class GlProgram extends GlObject {
    private static final Logger LOGGER = LogManager.getLogger(GlProgram.class);

    protected GlProgram(int program) {
        this.setHandle(program);
    }

    public static Builder builder(ResourceLocation location) {
        return new Builder(location);
    }

    public void bind() {
        GL20C.glUseProgram(this.handle());
    }

    public void unbind() {
        GL20C.glUseProgram(0);
    }

    /**
     * Retrieves the index of the uniform with the given name.
     * @param name The name of the uniform to find the index of
     * @return The uniform's index
     * @throws NullPointerException If no uniform exists with the given name
     */
    public int getUniformLocation(String name) {
        int index = GL20C.glGetUniformLocation(this.handle(), name);

        if (index < 0) {
            throw new NullPointerException("No uniform exists with name: " + name);
        }

        return index;
    }

    public void delete() {
        GL20C.glDeleteProgram(this.handle());

        this.invalidateHandle();
    }

    /**
     * Retrieves the index of the uniform block with the given name.
     * @param name The name of the uniform block to find the index of
     * @return The uniform block's index
     * @throws NullPointerException If no uniform block exists with the given name
     */
    protected int getUniformBlockIndex(String name) {
        int index = GL32C.glGetUniformBlockIndex(this.handle(), name);

        if (index < 0) {
            throw new NullPointerException("No uniform block exists with name: " + name);
        }

        return index;
    }

    public static class Builder {
        private final ResourceLocation name;
        private final int program;

        public Builder(ResourceLocation name) {
            this.name = name;
            this.program = GL20C.glCreateProgram();
        }

        public Builder attachShader(GlShader shader) {
            GL20C.glAttachShader(this.program, shader.handle());

            return this;
        }

        /**
         * Links the attached shaders to this program and returns a user-defined container which wraps the shader
         * program. This container can, for example, provide methods for updating the specific uniforms of that shader
         * set.
         *
         * @param factory The factory which will create the shader program's container
         * @param <P> The type which should be instantiated with the new program's handle
         * @return An instantiated shader container as provided by the factory
         */
        public <P extends GlProgram> P build(ProgramFactory<P> factory) {
            GL20C.glLinkProgram(this.program);

            String log = GL20C.glGetProgramInfoLog(this.program);

            if (!log.isEmpty()) {
                LOGGER.warn("Program link log for " + this.name + ": " + log);
            }

            int result = GlStateManager.glGetProgrami(this.program, GL20C.GL_LINK_STATUS);

            if (result != GL20C.GL_TRUE) {
                throw new RuntimeException("Shader program linking failed, see log for details");
            }

            return factory.create(this.program);
        }

        public Builder bindAttribute(String name, ShaderBindingPoint binding) {
            GL20C.glBindAttribLocation(this.program, binding.genericAttributeIndex(), name);

            return this;
        }

        public Builder bindFragmentData(String name, ShaderBindingPoint binding) {
            GL30C.glBindFragDataLocation(this.program, binding.genericAttributeIndex(), name);

            return this;
        }
    }

    public interface ProgramFactory<P extends GlProgram> {
        P create(int handle);
    }
}

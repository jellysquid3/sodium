package net.caffeinemc.mods.sodium.client.render.chunk.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformFloat;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformFloat4v;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformInt;

/**
 * These shader implementations try to remain compatible with the deprecated fixed function pipeline by manually
 * copying the state into each shader's uniforms. The shader code itself is a straight-forward implementation of the
 * fog functions themselves from the fixed-function pipeline, except that they use the distance from the camera
 * rather than the z-buffer to produce better looking fog that doesn't move with the player's view angle.
 *
 * Minecraft itself will actually try to enable distance-based fog by using the proprietary NV_fog_distance extension,
 * but as the name implies, this only works on graphics cards produced by NVIDIA. The shader implementation however does
 * not depend on any vendor-specific extensions and is written using very simple GLSL code.
 */
public abstract class ChunkShaderFogComponent {
    public abstract void setup();

    public static class None extends ChunkShaderFogComponent {
        public None(ShaderBindingContext context) {

        }

        @Override
        public void setup() {

        }
    }

    public static class Smooth extends ChunkShaderFogComponent {
        private final GlUniformFloat4v uFogColor;

        private final GlUniformInt uFogShape;
        private final GlUniformFloat uFogStart;
        private final GlUniformFloat uFogEnd;

        public Smooth(ShaderBindingContext context) {
            this.uFogColor = context.bindUniform("u_FogColor", GlUniformFloat4v::new);
            this.uFogShape = context.bindUniform("u_FogShape", GlUniformInt::new);
            this.uFogStart = context.bindUniform("u_FogStart", GlUniformFloat::new);
            this.uFogEnd = context.bindUniform("u_FogEnd", GlUniformFloat::new);
        }

        @Override
        public void setup() {
            this.uFogColor.set(RenderSystem.getShaderFog().red(), RenderSystem.getShaderFog().green(), RenderSystem.getShaderFog().blue(), RenderSystem.getShaderFog().alpha());
            this.uFogShape.set(RenderSystem.getShaderFog().shape().getIndex());

            this.uFogStart.setFloat(RenderSystem.getShaderFog().start());
            this.uFogEnd.setFloat(RenderSystem.getShaderFog().end());
        }
    }

}

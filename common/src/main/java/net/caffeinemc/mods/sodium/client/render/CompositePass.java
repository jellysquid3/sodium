package net.caffeinemc.mods.sodium.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderConstants;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderType;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformFloat4v;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformInt;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ShaderBindingContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL45C;

import java.util.EnumSet;

public class CompositePass {
    public static boolean ENABLED;

    public static boolean ENTITY_GLOW_IS_ACTIVE = false;

    private static final Vector4f VIGNETTE_COLOR = new Vector4f();

    private static int DEFAULT_VERTEX_ARRAY;
    private static GlProgram<CompositeProgramInterface> COMPOSITE_PROGRAM;

    public static void composite(@NotNull RenderTarget mainRT,
                                 @Nullable RenderTarget entityRT,
                                 int width,
                                 int height) {
        EnumSet<BlendOption> blendOptions = EnumSet.noneOf(BlendOption.class);

        if (entityRT != null && isEntityGlowIsActive()) {
            blendOptions.add(BlendOption.USE_ENTITY_GLOW);
        }

        if (isVignetteActive()) {
            blendOptions.add(BlendOption.USE_VIGNETTE);
        }

        if (blendOptions.isEmpty()) {
            // If no compositing is necessary, we can just blit the main render target to
            // the default framebuffer and avoid using the rasterization pipeline.
            mainRT.blitToScreen(width, height, true);
            return;
        }

        if (COMPOSITE_PROGRAM == null) {
            COMPOSITE_PROGRAM = GlProgram.builder(ResourceLocation.fromNamespaceAndPath("sodium", "composite"))
                    .attachShader(ShaderLoader.loadShader(ShaderType.VERTEX, ResourceLocation.fromNamespaceAndPath("sodium", "composite.vsh"), ShaderConstants.empty()))
                    .attachShader(ShaderLoader.loadShader(ShaderType.FRAGMENT, ResourceLocation.fromNamespaceAndPath("sodium", "composite.fsh"), ShaderConstants.empty()))
                    .bindFragmentData("fragColor", 0)
                    .link(CompositeProgramInterface::new);

            DEFAULT_VERTEX_ARRAY = GL33C.glGenVertexArrays();
        }

        VertexBuffer.unbind();

        GlStateManager._disableDepthTest();
        GlStateManager._disableBlend();

        GlStateManager._colorMask(true, true, true, false);
        GlStateManager._depthMask(false);

        GlStateManager._glUseProgram(COMPOSITE_PROGRAM.handle());
        GlStateManager._glBindVertexArray(DEFAULT_VERTEX_ARRAY);

        var uniforms = COMPOSITE_PROGRAM.getInterface();
        uniforms.mainColorRT.set(0);
        uniforms.entityColorRT.set(1);
        uniforms.vignetteTexture.set(2);
        uniforms.vignetteColorModulator.set(VIGNETTE_COLOR);
        uniforms.blendOptions.set(toBitfield(blendOptions));

        bindTextureToUnit(0, mainRT.getColorTextureId());

        if (entityRT != null) {
            bindTextureToUnit(1, blendOptions.contains(BlendOption.USE_ENTITY_GLOW) ? entityRT.getColorTextureId() : 0 /* default texture */);
        }

        bindTextureToUnit(2, getVignetteTextureId());

        GL45C.glDrawArrays(GL45C.GL_TRIANGLES, 0, 3);

        unbindTextureFromUnit(0);
        unbindTextureFromUnit(1);
        unbindTextureFromUnit(2);

        GlStateManager._depthMask(true);
        GlStateManager._colorMask(true, true, true, true);

        GlStateManager._enableBlend();
        GlStateManager._enableDepthTest();

        CompositePass.ENTITY_GLOW_IS_ACTIVE = false;
    }

    private static boolean isEntityGlowIsActive() {
        return CompositePass.ENTITY_GLOW_IS_ACTIVE;
    }

    private static boolean isVignetteActive() {
        return VIGNETTE_COLOR.w() >= 0.0025f;
    }

    private static int toBitfield(EnumSet<?> set) {
        int bits = 0;

        for (var value : set) {
            bits = (1 << value.ordinal());
        }

        return bits;
    }

    private static void unbindTextureFromUnit(int slot) {
        RenderSystem.activeTexture(GL33C.GL_TEXTURE0 + slot);
        RenderSystem.bindTexture(0);
    }

    private static void bindTextureToUnit(int slot, int texture) {
        RenderSystem.activeTexture(GL33C.GL_TEXTURE0 + slot);
        RenderSystem.bindTexture(texture);
    }

    public static void setVignetteColor(float[] color) {
        VIGNETTE_COLOR.set(color);
    }

    private static class CompositeProgramInterface {
        private final GlUniformInt mainColorRT;
        private final GlUniformInt entityColorRT;

        private final GlUniformInt vignetteTexture;
        private final GlUniformFloat4v vignetteColorModulator;

        private final GlUniformInt blendOptions;

        public CompositeProgramInterface(ShaderBindingContext ctx) {
            this.mainColorRT = ctx.bindUniform("mainColorRT", GlUniformInt::new);
            this.entityColorRT = ctx.bindUniform("entityColorRT", GlUniformInt::new);

            this.vignetteTexture = ctx.bindUniform("vignetteTexture", GlUniformInt::new);
            this.vignetteColorModulator = ctx.bindUniform("vignetteColorModulator", GlUniformFloat4v::new);

            this.blendOptions = ctx.bindUniform("blendOptions", GlUniformInt::new);
        }
    }

    private static int getVignetteTextureId() {
        Minecraft minecraft = Minecraft.getInstance();
        TextureManager textureManager = minecraft.getTextureManager();
        AbstractTexture texture = textureManager.getTexture(ResourceLocation.parse("textures/misc/vignette.png"));

        return texture.getId();
    }

    private enum BlendOption {
        USE_ENTITY_GLOW,
        USE_VIGNETTE
    }
}

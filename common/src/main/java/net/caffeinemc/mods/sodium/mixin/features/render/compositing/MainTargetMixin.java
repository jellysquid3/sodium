package net.caffeinemc.mods.sodium.mixin.features.render.compositing;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL32C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MainTarget.class)
public abstract class MainTargetMixin extends RenderTarget {
    @Unique
    private int depthRenderBufferId = -1;

    private MainTargetMixin(boolean useDepthBuffer) {
        super(useDepthBuffer);
    }

    /**
     * @author JellySquid
     * @reason Delegate to the default implementation (which is then overwritten)
     */
    @Overwrite
    protected void createFrameBuffer(int width, int height) {
        this.createBuffers(width, height, this.useDepth);
    }

    // For some reason, the main render target implements custom initialization behavior,
    // but resizing the render target uses the default implementation. For consistency’s sake when
    // resizing, we override the default implementation too.
    @Override
    public void createBuffers(int width, int height, boolean checkError) {
        RenderSystem.assertOnRenderThreadOrInit();
        MainTarget.Dimension dimension = this.allocateAttachments(width, height);

        this.frameBufferId = GlStateManager.glGenFramebuffers();

        GlStateManager._glBindFramebuffer(GL32C.GL_FRAMEBUFFER, this.frameBufferId);

        attachColorBuffer(this.colorTextureId);
        attachDepthBuffer(this.depthRenderBufferId);

        this.checkStatus();

        GlStateManager._glBindFramebuffer(GL32C.GL_FRAMEBUFFER, 0);

        this.viewWidth = dimension.width;
        this.viewHeight = dimension.height;
        this.width = dimension.width;
        this.height = dimension.height;
    }

    @Unique
    private static void attachColorBuffer(int colorTextureId) {
        GlStateManager._bindTexture(colorTextureId);
        GlStateManager._texParameter(GL32C.GL_TEXTURE_2D, 10241, 9728);
        GlStateManager._texParameter(GL32C.GL_TEXTURE_2D, 10240, 9728);
        GlStateManager._texParameter(GL32C.GL_TEXTURE_2D, 10242, 33071);
        GlStateManager._texParameter(GL32C.GL_TEXTURE_2D, 10243, 33071);
        GlStateManager._glFramebufferTexture2D(GL32C.GL_FRAMEBUFFER, GL32C.GL_COLOR_ATTACHMENT0,
                GL32C.GL_TEXTURE_2D, colorTextureId, 0);
    }

    @Unique
    private static void attachDepthBuffer(int depthRenderBufferId) {
        GL32C.glBindRenderbuffer(GL32C.GL_RENDERBUFFER, depthRenderBufferId);
        GL32C.glFramebufferRenderbuffer(GL32C.GL_FRAMEBUFFER, GL32C.GL_DEPTH_ATTACHMENT,
                GL32C.GL_RENDERBUFFER, depthRenderBufferId);
        GL32C.glBindRenderbuffer(GL32C.GL_RENDERBUFFER, 0);
    }

    @Inject(method = "allocateAttachments", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V", shift = At.Shift.AFTER))
    private void initDepthAttachment(int width, int height, CallbackInfoReturnable<MainTarget.Dimension> cir) {
        this.depthRenderBufferId = GL32C.glGenRenderbuffers();
    }

    @Shadow
    protected abstract MainTarget.Dimension allocateAttachments(int width, int height);

    /**
     * @author JellySquid
     * @reason Use RGB format instead of RGBA format
     */
    @Overwrite
    private boolean allocateColorAttachment(MainTarget.Dimension dimension) {
        GlStateManager._getError(); // clear the existing error, if any

        // The main render target does not need alpha, since the skybox is always rendered with full
        // opacity. Furthermore, when shaders attempt to read back the alpha from the color texture,
        // the default alpha (1.0, fully opaque) will always be used, so it should not cause problems.
        // Since we need to read from this color texture, it's not possible to replace it with a render buffer.
        GlStateManager._bindTexture(this.colorTextureId);
        GlStateManager._texImage2D(GL32C.GL_TEXTURE_2D, 0, GL32C.GL_RGB8, dimension.width, dimension.height,
                0, GL32C.GL_RGB, GL32C.GL_UNSIGNED_BYTE, null);

        return GlStateManager._getError() != GL32C.GL_OUT_OF_MEMORY;
    }

    /**
     * @author JellySquid
     * @reason Replace depth texture with depth render buffer
     */
    @Overwrite
    private boolean allocateDepthAttachment(MainTarget.Dimension dimension) {
        GlStateManager._getError(); // clear the existing error, if any

        // Depth textures are slower than depth render buffers, since it (in theory) prevents the GPU
        // from applying certain optimizations. At least with Intel Xe graphics, using a render buffer seems
        // to be slightly faster. But since fragment sorting needs access to the depth information, it is
        // necessary to fall back to a depth texture when Fabulous mode is activated.
        GL32C.glBindRenderbuffer(GL32C.GL_RENDERBUFFER, this.depthRenderBufferId);
        GL32C.glRenderbufferStorage(GL32C.GL_RENDERBUFFER, GL32C.GL_DEPTH_COMPONENT32F,
                dimension.width, dimension.height);
        GL32C.glBindRenderbuffer(GL32C.GL_RENDERBUFFER, 0);

        return GlStateManager._getError() != GL32C.GL_OUT_OF_MEMORY;
    }

    @Override
    public void destroyBuffers() {
        super.destroyBuffers();

        if (this.depthRenderBufferId != -1) {
            GL32C.glDeleteRenderbuffers(this.depthRenderBufferId);
            this.depthRenderBufferId = -1;
        }
    }
}

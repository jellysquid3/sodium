package me.jellysquid.mods.sodium.mixin.features.gui.fast_status_bars;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class MixinInGameHud extends DrawableHelper {
    @Shadow
    protected abstract PlayerEntity getCameraPlayer();

    private final BufferBuilder bufferBuilder = new BufferBuilder(8192);
    // It's possible for status bar rendering to be skipped
    private boolean isRenderingStatusBars;

    @Inject(method = "renderStatusBars", at = @At("HEAD"))
    private void preRenderStatusBars(MatrixStack matrices, CallbackInfo ci) {
        if (this.getCameraPlayer() != null) {
            this.bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
            this.isRenderingStatusBars = true;
        } else {
            this.isRenderingStatusBars = false;
        }
    }

    @Redirect(method = { "renderStatusBars", "drawHeart" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;drawTexture(Lnet/minecraft/client/util/math/MatrixStack;IIIIII)V"))
    private void drawTexture(InGameHud inGameHud, MatrixStack matrices, int x0, int y0, int u, int v, int width, int height) {
        Matrix4f matrix = matrices.peek().getModel();
        int x1 = x0 + width;
        int y1 = y0 + height;
        int z = this.getZOffset();
        // Default texture size is 256x256
        float u0 = u / 256f;
        float u1 = (u + width) / 256f;
        float v0 = v / 256f;
        float v1 = (v + height) / 256f;

        this.bufferBuilder.vertex(matrix, x0, y1, z).texture(u0, v1).next();
        this.bufferBuilder.vertex(matrix, x1, y1, z).texture(u1, v1).next();
        this.bufferBuilder.vertex(matrix, x1, y0, z).texture(u1, v0).next();
        this.bufferBuilder.vertex(matrix, x0, y0, z).texture(u0, v0).next();
    }

    @Inject(method = "renderStatusBars", at = @At("RETURN"))
    private void renderStatusBars(MatrixStack matrices, CallbackInfo ci) {
        if (this.isRenderingStatusBars) {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            this.bufferBuilder.end();
            BufferRenderer.draw(this.bufferBuilder);
        }
    }
}

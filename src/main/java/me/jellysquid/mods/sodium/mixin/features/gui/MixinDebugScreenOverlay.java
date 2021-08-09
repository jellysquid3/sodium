package me.jellysquid.mods.sodium.mixin.features.gui;

import com.google.common.base.Strings;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.apache.commons.lang3.Validate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
public abstract class MixinDebugScreenOverlay {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private Font font;

    private List<String> capturedList = null;

    @Redirect(method = { "drawGameInformation", "drawSystemInformation" }, at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"))
    private int preRenderText(List<String> list) {
        // Capture the list to be rendered later
        this.capturedList = list;

        return 0; // Prevent the rendering of any text
    }

    @Inject(method = "drawGameInformation", at = @At("RETURN"))
    public void renderLeftText(PoseStack matrixStack, CallbackInfo ci) {
        this.renderCapturedText(matrixStack, false);
    }

    @Inject(method = "drawSystemInformation", at = @At("RETURN"))
    public void renderRightText(PoseStack matrixStack, CallbackInfo ci) {
        this.renderCapturedText(matrixStack, true);
    }

    private void renderCapturedText(PoseStack matrixStack, boolean right) {
        Validate.notNull(this.capturedList, "Failed to capture string list");

        this.renderBackdrop(matrixStack, this.capturedList, right);
        this.renderStrings(matrixStack, this.capturedList, right);

        this.capturedList = null;
    }

    private void renderStrings(PoseStack matrixStack, List<String> list, boolean right) {
        MultiBufferSource.BufferSource immediate = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());

        Matrix4f modelMatrix = matrixStack.last().pose();

        for (int i = 0; i < list.size(); ++i) {
            String string = list.get(i);

            if (!Strings.isNullOrEmpty(string)) {
                int height = 9;
                int width = this.font.width(string);

                float x1 = right ? this.minecraft.getWindow().getGuiScaledWidth() - 2 - width : 2;
                float y1 = 2 + (height * i);

                this.font.drawInBatch(string, x1, y1, 0xe0e0e0, false, modelMatrix, immediate,
                        false, 0, 15728880, this.font.isBidirectional());
            }
        }

        immediate.endBatch();
    }

    private void renderBackdrop(PoseStack matrixStack, List<String> list, boolean right) {
        RenderSystem.enableBlend();
        RenderSystem.disableTexture();
        RenderSystem.defaultBlendFunc();

        int color = 0x90505050;

        float f = (float) (color >> 24 & 255) / 255.0F;
        float g = (float) (color >> 16 & 255) / 255.0F;
        float h = (float) (color >> 8 & 255) / 255.0F;
        float k = (float) (color & 255) / 255.0F;

        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = matrixStack.last()
                .pose();

        for (int i = 0; i < list.size(); ++i) {
            String string = list.get(i);

            if (Strings.isNullOrEmpty(string)) {
                continue;
            }

            int height = 9;
            int width = this.font.width(string);

            int x = right ? this.minecraft.getWindow().getGuiScaledWidth() - 2 - width : 2;
            int y = 2 + height * i;

            float x1 = x - 1;
            float y1 = y - 1;
            float x2 = x + width + 1;
            float y2 = y + height - 1;

            bufferBuilder.vertex(matrix, x1, y2, 0.0F).color(g, h, k, f).endVertex();
            bufferBuilder.vertex(matrix, x2, y2, 0.0F).color(g, h, k, f).endVertex();
            bufferBuilder.vertex(matrix, x2, y1, 0.0F).color(g, h, k, f).endVertex();
            bufferBuilder.vertex(matrix, x1, y1, 0.0F).color(g, h, k, f).endVertex();
        }

        bufferBuilder.end();

        BufferUploader.end(bufferBuilder);
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
    }
}

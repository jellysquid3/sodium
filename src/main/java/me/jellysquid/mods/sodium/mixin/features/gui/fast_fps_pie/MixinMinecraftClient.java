package me.jellysquid.mods.sodium.mixin.features.gui.fast_fps_pie;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.profiler.ProfileResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {
    @Shadow
    @Final
    public TextRenderer textRenderer;

    private VertexConsumerProvider.Immediate immediate;

    @Inject(method = "drawProfilerResults", at = @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/client/font/TextRenderer;drawWithShadow(Lnet/minecraft/client/util/math/MatrixStack;Ljava/lang/String;FFI)I"))
    private void preRenderText(MatrixStack matrices, ProfileResult profileResult, CallbackInfo ci) {
        this.immediate = VertexConsumerProvider.immediate(Tessellator.getInstance().getBuffer());
    }

    @Redirect(method = "drawProfilerResults", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;drawWithShadow(Lnet/minecraft/client/util/math/MatrixStack;Ljava/lang/String;FFI)I"))
    private int drawWithShadow(TextRenderer textRenderer, MatrixStack matrices, String text, float x, float y, int color) {
        if (text != null) {
            return this.textRenderer.draw(text, x, y, color, true, matrices.peek().getModel(), this.immediate,
                    false, 0, 15728880, this.textRenderer.isRightToLeft());
        }
        return 0;
    }

    @Inject(method = "drawProfilerResults", at = @At("TAIL"))
    private void renderText(MatrixStack matrices, ProfileResult profileResult, CallbackInfo ci) {
        this.immediate.draw();
    }
}

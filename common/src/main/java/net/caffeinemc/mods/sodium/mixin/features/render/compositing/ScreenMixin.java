package net.caffeinemc.mods.sodium.mixin.features.render.compositing;

import net.caffeinemc.mods.sodium.client.render.CompositePass;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = "renderBlurredBackground", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V", shift = At.Shift.BEFORE), cancellable = true)
    private void redirectBindDefaultFramebuffer(float partialTicks, CallbackInfo ci) {
        // FIXME
        if (CompositePass.ENABLED) {
            ci.cancel();
        }
    }
}

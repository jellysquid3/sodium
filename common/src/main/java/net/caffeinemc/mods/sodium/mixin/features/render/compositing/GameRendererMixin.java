package net.caffeinemc.mods.sodium.mixin.features.render.compositing;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import net.caffeinemc.mods.sodium.client.render.CompositePass;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V", ordinal = 0, shift = At.Shift.BEFORE))
    private void preRenderGui(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (!CompositePass.ENABLED) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();

        int width = window.getWidth();
        int height = window.getHeight();

        RenderTarget mainRT = minecraft.getMainRenderTarget();
        mainRT.unbindWrite();

        if (minecraft.level != null) {
            CompositePass.composite(mainRT, minecraft.levelRenderer.entityTarget(), width, height);
        } else {
            mainRT.blitToScreen(width, height, true);
        }
    }
}

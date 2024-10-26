package net.caffeinemc.mods.sodium.mixin.features.render.compositing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.render.CompositePass;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V", ordinal = 0, shift = At.Shift.BEFORE))
    private void preRunTick(boolean outOfMemory, CallbackInfo ci) {
        CompositePass.ENABLED = SodiumClientMod.options().performance.useSinglePassCompositing;
    }

    @WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V", ordinal = 0))
    private void cancelClear(int clearMask, boolean checkError, Operation<Void> original) {
        if (!CompositePass.ENABLED) {
            original.call(clearMask, checkError);
        }
    }

    @WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen(II)V"))
    private void redirectCompositePass(RenderTarget renderTarget, int width, int height, Operation<Void> original) {
        if (!CompositePass.ENABLED) {
            original.call(renderTarget, width, height);
        }
    }
}

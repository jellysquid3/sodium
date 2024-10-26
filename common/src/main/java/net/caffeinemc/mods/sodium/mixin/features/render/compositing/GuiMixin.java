package net.caffeinemc.mods.sodium.mixin.features.render.compositing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.caffeinemc.mods.sodium.client.render.CompositePass;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public class GuiMixin {
    // We can't separately query the vignette color, so we instead hook the render function
    // and look at the render state to figure out what color is used.
    @WrapOperation(method = "renderVignette", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIFFIIII)V"))
    public void beforeBlit(GuiGraphics gui, ResourceLocation texture, int x, int y, int z, float u1, float v1, int u2, int v2, int textureWidth, int textureHeight, Operation<Void> original) {
        if (CompositePass.ENABLED) {
            // The blit will happen later in the final compositing pass
            CompositePass.setVignetteColor(RenderSystem.getShaderColor());
        } else {
            original.call(gui, texture, x, y, z, u1, v1, u2, v2, textureWidth, textureHeight);
        }
    }
}

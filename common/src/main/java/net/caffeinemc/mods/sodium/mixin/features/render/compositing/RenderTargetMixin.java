package net.caffeinemc.mods.sodium.mixin.features.render.compositing;


import com.mojang.blaze3d.pipeline.RenderTarget;
import org.lwjgl.opengl.GL32C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTarget.class)
public class RenderTargetMixin {
    @Shadow
    public int frameBufferId;

    @Shadow
    public int width;

    @Shadow
    public int height;

}
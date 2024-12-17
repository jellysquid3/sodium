package net.caffeinemc.mods.sodium.mixin.features.textures.scan;

import com.mojang.blaze3d.platform.NativeImage;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.SpriteContentsExtension;
import net.caffeinemc.mods.sodium.client.util.NativeImageHelper;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin scans a {@link SpriteContents} for transparent and translucent pixels. This information is later used during mesh generation to reassign the render pass to either cutout if the sprite has no translucent pixels or solid if it doesn't even have any transparent pixels.
 *
 * @author douira
 */
@Mixin(SpriteContents.class)
public abstract class SpriteContentsMixin implements SpriteContentsExtension {
    @Unique
    private boolean sodium$needsAlphaTesting;

    @Unique
    private boolean sodium$needsAlphaBlending;

    @Inject(method = "<init>", at = @At(value = "RETURN"))
    private void analyzePixels(ResourceLocation name, FrameSize frameSize, NativeImage originalImage, ResourceMetadata metadata, CallbackInfo ci) {
        var analyzer = new NativeImageHelper.RenderPassAnalyzer();
        NativeImageHelper.forEachPixel(this.originalImage, analyzer);

        this.sodium$needsAlphaTesting |= analyzer.needsAlphaTesting();
        this.sodium$needsAlphaBlending |= analyzer.needsAlphaBlending();
    }


    @Override
    public boolean sodium$hasTransparentPixels() {
        return this.sodium$needsAlphaTesting;
    }

    @Override
    public boolean sodium$hasTranslucentPixels() {
        return this.sodium$needsAlphaBlending;
    }

    @Shadow @Final
    private NativeImage originalImage;
}

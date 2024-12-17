package net.caffeinemc.mods.sodium.mixin.features.textures.scan;

import com.mojang.blaze3d.platform.NativeImage;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.util.ColorU8;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.SpriteContentsExtension;
import net.caffeinemc.mods.sodium.client.util.NativeImageHelper;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.lwjgl.system.MemoryUtil;
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

    @Inject(method = "increaseMipLevel", at = @At("RETURN"))
    private void modifyMipMaps(int mipLevels, CallbackInfo ci) {
        for (int frameIndex = 0; frameIndex < this.getFrameCount(); frameIndex++) {
            float originalCoverage = this.calculateAlphaCoverage(this.originalImage, this.width, this.height, frameIndex, 1.0f);

            for (int mipIndex = 1; mipIndex < mipLevels; mipIndex++) {
                this.scaleAlphaToCoverage(
                        this.byMipLevel[mipIndex],
                        this.width >> mipLevels,
                        this.height >> mipLevels,
                        frameIndex,
                        originalCoverage);
            }
        }
    }

    @Unique
    private void scaleAlphaToCoverage(NativeImage image, int frameWidth, int frameHeight, int frameIndex, float targetCoverage) {
        float minAlphaScale = 0.0f;
        float maxAlphaScale = 4.0f;
        float alphaScale = 1.0f;

        float bestAlphaScale = 1.0f;
        float bestError = Float.MAX_VALUE;

        for (int step = 0; step < 10; step++) {
            final float coverage = this.calculateAlphaCoverage(image, frameWidth, frameHeight, frameIndex, alphaScale);
            final float error = Math.abs(coverage - targetCoverage);

            if (error < bestError) {
                bestError = error;
                bestAlphaScale = alphaScale;
            }

            if (coverage < targetCoverage) {
                minAlphaScale = alphaScale;
            } else if (coverage > targetCoverage) {
                maxAlphaScale = alphaScale;
            } else {
                break;
            }

            alphaScale = (minAlphaScale + maxAlphaScale) * 0.5f;
        }

        scaleAlpha(image, bestAlphaScale);
    }

    @Unique
    private float calculateAlphaCoverage(NativeImage image, int frameWidth, int frameHeight, int frameIndex, float alphaScale) {
        var analyzer = new NativeImageHelper.AlphaCoverageAnalyzer(alphaScale);
        NativeImageHelper.forEachPixelInFrame(image, frameWidth, frameHeight, frameIndex, analyzer);

        return analyzer.alphaCoverage(frameWidth * frameHeight);
    }

    @Unique
    private static void scaleAlpha(NativeImage nativeImage, float scale) {
        NativeImageHelper.forEachPixel(nativeImage, (ptr) -> {
            int color = MemoryUtil.memGetInt(ptr);
            int alpha = ColorABGR.unpackAlpha(color);

            MemoryUtil.memPutInt(ptr,
                    ColorABGR.withAlpha(color, ColorU8.normalizedFloatToByte(ColorU8.byteToNormalizedFloat(alpha) * scale)));
        });
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

    @Shadow @Final
    int height;

    @Shadow @Final
    int width;

    @Shadow
    NativeImage[] byMipLevel;

    @Shadow
    protected abstract int getFrameCount();
}

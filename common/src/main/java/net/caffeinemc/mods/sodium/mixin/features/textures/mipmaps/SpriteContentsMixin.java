
package net.caffeinemc.mods.sodium.mixin.features.textures.mipmaps;

import com.mojang.blaze3d.platform.NativeImage;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.util.ColorU8;
import net.caffeinemc.mods.sodium.client.util.NativeImageHelper;
import net.caffeinemc.mods.sodium.client.util.color.ColorSRGB;
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

@Mixin(SpriteContents.class)
public abstract class SpriteContentsMixin {
    @Unique
    private static final int SUB_SAMPLE_COUNT = 4;

    @Inject(method = "<init>", at = @At(value = "RETURN"))
    private void sodium$beforeGenerateMipLevels(ResourceLocation name, FrameSize frameSize, NativeImage originalImage, ResourceMetadata metadata, CallbackInfo ci) {
        sodium$fillInTransparentPixelColors(originalImage);
    }

    @Inject(method = "increaseMipLevel", at = @At("RETURN"))
    private void modifyMipMaps(int mipLevels, CallbackInfo ci) {
        float originalCoverage = this.calculateAlphaCoverage(this.originalImage, this.width, this.height, 0, 1.0f);

        for (int frameIndex = 0; frameIndex < this.getFrameCount(); frameIndex++) {
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
        int frameCount = image.getWidth() / frameWidth;
        int frameX = ((frameIndex % frameCount) * frameWidth);
        int frameY = ((frameIndex / frameCount) * frameHeight);

        float coverage = 0.0f;

        for (int y = frameY; y < (frameY + frameHeight) - 1; y++) {
            for (int x = frameX; x < (frameX + frameWidth) - 1; x++) {
                float alpha00 = alpha(image.getPixel(x + 0, y + 0)) * alphaScale;
                float alpha10 = alpha(image.getPixel(x + 1, y + 0)) * alphaScale;
                float alpha01 = alpha(image.getPixel(x + 0, y + 1)) * alphaScale;
                float alpha11 = alpha(image.getPixel(x + 1, y + 1)) * alphaScale;

                coverage += getTexelCoverage(alpha00, alpha10, alpha01, alpha11);
            }
        }

        return coverage / ((frameWidth - 1) * (frameHeight - 1));
    }

    @Unique
    private static float getTexelCoverage(float alpha00, float alpha10, float alpha01, float alpha11) {
        float coverage = 0.0f;

        for (int sY = 0; sY < SUB_SAMPLE_COUNT; sY++) {
            for (int sX = 0; sX < SUB_SAMPLE_COUNT; sX++) {
                float fY = (sY + 0.5f) / SUB_SAMPLE_COUNT;
                float fX = (sX + 0.5f) / SUB_SAMPLE_COUNT;

                float alpha = 0.0f;
                alpha += alpha00 * (1 - fX) * (1 - fY);
                alpha += alpha10 * (    fX) * (1 - fY);
                alpha += alpha01 * (1 - fX) * (    fY);
                alpha += alpha11 * (    fX) * (    fY);

                if (alpha > 0.5f) {
                    coverage += 1.0f;
                }
            }
        }

        return coverage / (SUB_SAMPLE_COUNT * SUB_SAMPLE_COUNT);
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

    @Unique
    private static float alpha(int rgba) {
        return ColorU8.byteToNormalizedFloat(ColorABGR.unpackAlpha(rgba));
    }

    /**
     * Fixes a common issue in image editing programs where fully transparent pixels are saved with fully black colors.
     *
     * This causes issues with mipmapped texture filtering, since the black color is used to calculate the final color
     * even though the alpha value is zero. While ideally it would be disregarded, we do not control that. Instead,
     * this code tries to calculate a decent average color to assign to these fully-transparent pixels so that their
     * black color does not leak over into sampling.
     */
    @Unique
    private static void sodium$fillInTransparentPixelColors(NativeImage nativeImage) {
        final long ppPixel = NativeImageHelper.getPointerRGBA(nativeImage);
        final int pixelCount = nativeImage.getHeight() * nativeImage.getWidth();

        // Calculate an average color from all pixels that are not completely transparent.
        // This average is weighted based on the (non-zero) alpha value of the pixel.
        float r = 0.0f;
        float g = 0.0f;
        float b = 0.0f;

        float totalWeight = 0.0f;

        for (int pixelIndex = 0; pixelIndex < pixelCount; pixelIndex++) {
            long pPixel = ppPixel + (pixelIndex * 4L);

            int color = MemoryUtil.memGetInt(pPixel);
            int alpha = ColorABGR.unpackAlpha(color);

            // Ignore all fully-transparent pixels for the purposes of computing an average color.
            if (alpha != 0) {
                float weight = (float) alpha;

                // Make sure to convert to linear space so that we don't lose brightness.
                r += ColorSRGB.srgbToLinear(ColorABGR.unpackRed(color)) * weight;
                g += ColorSRGB.srgbToLinear(ColorABGR.unpackGreen(color)) * weight;
                b += ColorSRGB.srgbToLinear(ColorABGR.unpackBlue(color)) * weight;

                totalWeight += weight;
            }
        }

        // Bail if none of the pixels are semi-transparent.
        if (totalWeight == 0.0f) {
            return;
        }

        r /= totalWeight;
        g /= totalWeight;
        b /= totalWeight;

        // Convert that color in linear space back to sRGB.
        // Use an alpha value of zero - this works since we only replace pixels with an alpha value of 0.
        int averageColor = ColorABGR.pack(ColorSRGB.linearToSrgb(r), ColorSRGB.linearToSrgb(g), ColorSRGB.linearToSrgb(b), 0x0);

        for (int pixelIndex = 0; pixelIndex < pixelCount; pixelIndex++) {
            long pPixel = ppPixel + (pixelIndex * 4L);

            int color = MemoryUtil.memGetInt(pPixel);
            int alpha = ColorABGR.unpackAlpha(color);

            // Replace the color values of pixels which are fully transparent, since they have no color data.
            if (alpha == 0) {
                MemoryUtil.memPutInt(pPixel, averageColor);
            }
        }
    }

    @Shadow
    @Final
    private NativeImage originalImage;

    @Shadow
    @Final
    int height;

    @Shadow @Final
    int width;

    @Shadow
    NativeImage[] byMipLevel;

    @Shadow
    protected abstract int getFrameCount();
}
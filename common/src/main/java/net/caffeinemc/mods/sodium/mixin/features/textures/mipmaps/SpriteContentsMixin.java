/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Original source: https://github.com/IrisShaders/Iris/blob/41095ac23ea0add664afd1b85c414d1f1ed94066/src/main/java/net/coderbot/iris/mixin/bettermipmaps/MixinTextureAtlasSprite.java
 */
package net.caffeinemc.mods.sodium.mixin.features.textures.mipmaps;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.NativeImage;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.client.util.NativeImageHelper;
import net.caffeinemc.mods.sodium.client.util.color.ColorSRGB;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryUtil;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SpriteContents.class)
public class SpriteContentsMixin {
    @Mutable
    @Shadow
    @Final
    private NativeImage originalImage;

    @WrapOperation(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/texture/SpriteContents;originalImage:Lcom/mojang/blaze3d/platform/NativeImage;", opcode = Opcodes.PUTFIELD))
    private void sodium$beforeGenerateMipLevels(SpriteContents instance, NativeImage nativeImage, Operation<Void> original) {
        sodium$fillInTransparentPixelColors(nativeImage);

        original.call(instance, nativeImage);
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
        int averageColor = ColorSRGB.linearToSrgb(r, g, b, 0);

        for (int pixelIndex = 0; pixelIndex < pixelCount; pixelIndex++) {
            long pPixel = ppPixel + (pixelIndex * 4);

            int color = MemoryUtil.memGetInt(pPixel);
            int alpha = ColorABGR.unpackAlpha(color);

            // Replace the color values of pixels which are fully transparent, since they have no color data.
            if (alpha == 0) {
                MemoryUtil.memPutInt(pPixel, averageColor);
            }
        }
    }
}
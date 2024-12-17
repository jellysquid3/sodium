package net.caffeinemc.mods.sodium.mixin.features.textures.mipmaps;

import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.util.ColorU8;
import net.caffeinemc.mods.sodium.client.util.color.ColorSRGB;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MipmapGenerator.class)
public class MipmapGeneratorMixin {
    /**
     * @author JellySquid
     * @reason Replace the vanilla blending function with our improved function
     */
    @Overwrite
    private static int alphaBlend(int m00, int m10, int m01, int m11, boolean checkAlpha) {
        float r = 0.0f;
        float g = 0.0f;
        float b = 0.0f;
        float a = 0.0f;

        r += ColorSRGB.srgbToLinear(ColorABGR.unpackRed(m00));
        g += ColorSRGB.srgbToLinear(ColorABGR.unpackGreen(m00));
        b += ColorSRGB.srgbToLinear(ColorABGR.unpackBlue(m00));
        a += ColorU8.byteToNormalizedFloat(ColorABGR.unpackAlpha(m00));

        r += ColorSRGB.srgbToLinear(ColorABGR.unpackRed(m10));
        g += ColorSRGB.srgbToLinear(ColorABGR.unpackGreen(m10));
        b += ColorSRGB.srgbToLinear(ColorABGR.unpackBlue(m10));
        a += ColorU8.byteToNormalizedFloat(ColorABGR.unpackAlpha(m10));

        r += ColorSRGB.srgbToLinear(ColorABGR.unpackRed(m01));
        g += ColorSRGB.srgbToLinear(ColorABGR.unpackGreen(m01));
        b += ColorSRGB.srgbToLinear(ColorABGR.unpackBlue(m01));
        a += ColorU8.byteToNormalizedFloat(ColorABGR.unpackAlpha(m01));

        r += ColorSRGB.srgbToLinear(ColorABGR.unpackRed(m11));
        g += ColorSRGB.srgbToLinear(ColorABGR.unpackGreen(m11));
        b += ColorSRGB.srgbToLinear(ColorABGR.unpackBlue(m11));
        a += ColorU8.byteToNormalizedFloat(ColorABGR.unpackAlpha(m11));

        r *= 0.25f; // 1.0 / 4.0
        g *= 0.25f;
        b *= 0.25f;
        a *= 0.25f;

        return ColorABGR.pack(
                ColorSRGB.linearToSrgb(r),
                ColorSRGB.linearToSrgb(g),
                ColorSRGB.linearToSrgb(b),
                ColorU8.normalizedFloatToByte(a)
        );
    }
}

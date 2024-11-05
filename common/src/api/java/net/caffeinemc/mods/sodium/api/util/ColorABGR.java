package net.caffeinemc.mods.sodium.api.util;

/**
 * Provides some utilities for packing and unpacking color components from packed integer colors in ABGR format, which
 * is used by OpenGL for color vectors.
 *
 * | 32        | 24        | 16        | 8          |
 * | 0110 1100 | 0110 1100 | 0110 1100 | 0110 1100  |
 * | Alpha     | Blue      | Green     | Red        |
 */
public class ColorABGR implements ColorU8 {
    private static final int RED_COMPONENT_OFFSET   = 0;
    private static final int GREEN_COMPONENT_OFFSET = 8;
    private static final int BLUE_COMPONENT_OFFSET  = 16;
    private static final int ALPHA_COMPONENT_OFFSET = 24;

    private static final int RED_COMPONENT_MASK     = COMPONENT_MASK << RED_COMPONENT_OFFSET;
    private static final int GREEN_COMPONENT_MASK   = COMPONENT_MASK << GREEN_COMPONENT_OFFSET;
    private static final int BLUE_COMPONENT_MASK    = COMPONENT_MASK << BLUE_COMPONENT_OFFSET;
    private static final int ALPHA_COMPONENT_MASK   = COMPONENT_MASK << ALPHA_COMPONENT_OFFSET;


    /**
     * Packs the specified color components into ABGR format. The alpha component is fully opaque.
     * @param r The red component of the color
     * @param g The green component of the color
     * @param b The blue component of the color
     */
    public static int pack(float r, float g, float b) {
        return pack(r, g, b, COMPONENT_MASK);
    }

    /**
     * Packs the specified color components into ABGR format.
     * @param r The red component of the color
     * @param g The green component of the color
     * @param b The blue component of the color
     * @param a The alpha component of the color
     */
    public static int pack(int r, int g, int b, int a) {
        return ((a & COMPONENT_MASK) << ALPHA_COMPONENT_OFFSET) |
                ((b & COMPONENT_MASK) << BLUE_COMPONENT_OFFSET) |
                ((g & COMPONENT_MASK) << GREEN_COMPONENT_OFFSET) |
                ((r & COMPONENT_MASK) << RED_COMPONENT_OFFSET);
    }

    /**
     * Packs the specified color components into ABGR format.
     * @param rgb The red/green/blue component of the color
     * @param alpha The alpha component of the color
     */
    public static int withAlpha(int rgb, float alpha) {
        return withAlpha(rgb, ColorU8.normalizedFloatToByte(alpha));
    }

    /**
     * Packs the specified color components into ABGR format.
     * @param rgb The red/green/blue component of the color
     * @param alpha The alpha component of the color
     */
    public static int withAlpha(int rgb, int alpha) {
        return (alpha << ALPHA_COMPONENT_OFFSET) | (rgb & ~(COMPONENT_MASK << ALPHA_COMPONENT_OFFSET));
    }

    /**
     * Converts the color components from normalized floating point into a packed integer in ABGR format.
     * @see ColorABGR#pack(int, int, int, int)
     */
    public static int pack(float r, float g, float b, float a) {
        return pack(ColorU8.normalizedFloatToByte(r),
                ColorU8.normalizedFloatToByte(g),
                ColorU8.normalizedFloatToByte(b),
                ColorU8.normalizedFloatToByte(a));
    }

    /**
     * @param color The packed 32-bit ABGR color to unpack
     * @return The red color component in the range of 0..255
     */
    public static int unpackRed(int color) {
        return (color >> RED_COMPONENT_OFFSET) & COMPONENT_MASK;
    }

    /**
     * @param color The packed 32-bit ABGR color to unpack
     * @return The green color component in the range of 0..255
     */
    public static int unpackGreen(int color) {
        return (color >> GREEN_COMPONENT_OFFSET) & COMPONENT_MASK;
    }

    /**
     * @param color The packed 32-bit ABGR color to unpack
     * @return The blue color component in the range of 0..255
     */
    public static int unpackBlue(int color) {
        return (color >> BLUE_COMPONENT_OFFSET) & COMPONENT_MASK;
    }

    /**
     * @param color The packed 32-bit ABGR color to unpack
     * @return The red color component in the range of 0..255
     */
    public static int unpackAlpha(int color) {
        return (color >> ALPHA_COMPONENT_OFFSET) & COMPONENT_MASK;
    }

    /**
     * Darkens the RGB components of the color by multiplying them with the provided factor. The alpha component is
     * not modified.
     * 
     * @param color The packed 32-bit ABGR color to be multiplied
     * @param factor The darkening factor (in the range of 0..255) to multiply with
     */
    public static int darken(int color, int factor) {
        return (ColorMixer.mul(color, factor) & ~ALPHA_COMPONENT_MASK) | (color & ALPHA_COMPONENT_MASK);
    }
}

package net.caffeinemc.mods.sodium.api.util;

/**
 * A collection of optimized color mixing functions which directly operate on packed color values. These functions are
 * agnostic to the ordering of color channels, and the output value will always use the same channel ordering as
 * the input values.
 */
public class ColorMixer {
    /**
     * <p>Linearly interpolate between the {@param start} and {@param end} points, represented as packed unsigned 8-bit
     * values within a 32-bit integer. The result is computed as <pre>(x * a) * (y * (255 - a))</pre>.</p>
     *
     * <p>The results are undefined if {@param a} is not within the interval [0, 255].</p>

     * @param color0 The start of the range to interpolate
     * @param color1 The end of the range to interpolate
     * @param weight The weight value used to interpolate between color values (in 0..255 range)
     * @return The color that was interpolated between the start and end points
     */
    public static int mix(int color0, int color1, int weight) {
        // Overflow is not possible, so adding the values is fine.
        return mul(color0, weight) + mul(color1, ColorU8.COMPONENT_MASK - weight);
    }

    /**
     * <p>This function is identical to {@link ColorMixer#mix(int, int, int)}, but {@param a} is a normalized
     * floating-point value within the interval of [0.0, 1.0].</p>
     *
     * <p>The results are undefined if {@param a} is not within the interval [0.0, 1.0].</p>
     *
     * @param color0 The start of the range to interpolate
     * @param color1 The end of the range to interpolate
     * @param weight The weight value used to interpolate between color values (in 0.0..1.0 range)
     * @return The color that was interpolated between the start and end points
     */
    public static int mix(int color0, int color1, float weight) {
        return mix(color0, color1, ColorU8.normalizedFloatToByte(weight));
    }

    /**
     * <p>Multiplies each 8-bit component of the packed 32-bit color.</p>
     *
     * @param color The packed color values
     * @param factor The multiplication factor (in 0..255 range)
     * @return The result of the multiplication
     */
    public static int mul(int color, int factor) {
        final long result = (((((color & 0x00FF00FFL) * factor) + 0x00FF00FFL) >>> 8) & 0x00FF00FFL) |
                            (((((color & 0xFF00FF00L) * factor) + 0xFF00FF00L) >>> 8) & 0xFF00FF00L);

        return (int) result;
    }
}

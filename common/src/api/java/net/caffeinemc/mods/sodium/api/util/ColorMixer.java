package net.caffeinemc.mods.sodium.api.util;

/**
 * A collection of optimized color mixing functions which directly operate on packed color values. These functions are
 * agnostic to the ordering of color channels, and the output value will always use the same channel ordering as
 * the input values.
 */
public class ColorMixer {
    /**
     * <p>Linearly interpolate between the {@param start} and {@param end} points, represented as packed unsigned 8-bit
     * values within a 32-bit integer. The result is computed as <pre>(start * weight) + (end * (255 - weight))</pre>.</p>
     *
     * <p>The results are undefined if {@param weight} is not within the interval [0, 255].</p>

     * @param start The start of the range to interpolate
     * @param end The end of the range to interpolate
     * @param weight The weight value used to interpolate between color values (in 0..255 range)
     * @return The color that was interpolated between the start and end points
     */
    public static int mix(int start, int end, int weight) {
        // Overflow is not possible, so adding the values is fine.
        return mul(start, weight) + mul(end, ColorU8.COMPONENT_MASK - weight);
    }

    /**
     * <p>This function is identical to {@link ColorMixer#mix(int, int, int)}, but {@param weight} is a normalized
     * floating-point value within the interval of [0.0, 1.0].</p>
     *
     * <p>The results are undefined if {@param weight} is not within the interval [0.0, 1.0].</p>
     *
     * @param start The start of the range to interpolate
     * @param end The end of the range to interpolate
     * @param weight The weight value used to interpolate between color values (in 0.0..1.0 range)
     * @return The color that was interpolated between the start and end points
     */
    public static int mix(int start, int end, float weight) {
        return mix(start, end, ColorU8.normalizedFloatToByte(weight));
    }

    /**
     * <p>Multiplies the packed 8-bit values component-wise to produce 16-bit intermediaries, and then round to the
     * nearest 8-bit representation (similar to floating-point.)</p>
     *
     * @param color0 The first color to multiply
     * @param color1 The second color to multiply
     * @return The product of the two colors
     */
    public static int mulComponentWise(int color0, int color1) {
        int comp0 = ((((color0 >>>  0) & 0xFF) * ((color1 >>>  0) & 0xFF)) + 0xFF) >>> 8;
        int comp1 = ((((color0 >>>  8) & 0xFF) * ((color1 >>>  8) & 0xFF)) + 0xFF) >>> 8;
        int comp2 = ((((color0 >>> 16) & 0xFF) * ((color1 >>> 16) & 0xFF)) + 0xFF) >>> 8;
        int comp3 = ((((color0 >>> 24) & 0xFF) * ((color1 >>> 24) & 0xFF)) + 0xFF) >>> 8;

        return (comp0 << 0) | (comp1 << 8) | (comp2 << 16) | (comp3 << 24);
    }

    /**
     * <p>Multiplies each 8-bit component against the factor to produce 16-bit intermediaries, and then round to the
     * nearest 8-bit representation (similar to floating-point.)</p>
     *
     * <p>The results are undefined if {@param factor} is not within the interval [0, 255].</p>
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

    /**
     * See {@link #mul(int, int)}, which this function is identical to, except that it takes a floating point value in
     * the interval of [0.0, 1.0] and maps it to [0, 255].
     *
     * <p>The results are undefined if {@param factor} is not within the interval [0.0, 1.0].</p>
     */
    public static int mul(int color, float factor) {
        return mul(color, ColorU8.normalizedFloatToByte(factor));
    }
}

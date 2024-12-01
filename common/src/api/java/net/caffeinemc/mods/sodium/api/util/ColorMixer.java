package net.caffeinemc.mods.sodium.api.util;

import org.jetbrains.annotations.ApiStatus;

/**
 * A collection of optimized color mixing functions which directly operate on packed color values. These functions are
 * agnostic to the ordering of color channels, and the output value will always use the same channel ordering as
 * the input values.
 */
public class ColorMixer {
    /**
     * <p>Linearly interpolate between the {@param start} and {@param end} points, represented as packed unsigned 8-bit
     * values within a 32-bit integer. The result is computed as <pre>(start * weight) + (end * (255 - weight))</pre>
     * using fixed-point arithmetic and round-to-nearest behavior.</p>
     *
     * <p>The results are undefined if {@param weight} is not within the interval [0, 255].</p>
     *
     * <p>If {@param start} and {@param end} are the same value, the result of this function will always be that value,
     * regardless of {@param weight}.</p>
     *
     * @param start The value at the start of the range to interpolate
     * @param end The value at the end of the range to interpolate
     * @param weight The weight value used to interpolate between color values (in 0..255 range)
     * @return The color that was interpolated between the start and end points
     */
    public static int mix(int start, int end, int weight) {
        // De-interleave the 8-bit component lanes into high and low halves for each point.
        // Multiply the start point by alpha, and the end point by 1-alpha, to produce Q8.8 fixed-point intermediates.
        // Add the Q8.8 fixed-point intermediaries together to obtain the mixed values.
        final long hi = ((start & 0x00FF00FFL) * weight) + ((end & 0x00FF00FFL) * (ColorU8.COMPONENT_MASK - weight));
        final long lo = ((start & 0xFF00FF00L) * weight) + ((end & 0xFF00FF00L) * (ColorU8.COMPONENT_MASK - weight));

        // Round the fixed-point values to the nearest integer, and interleave the high and low halves to
        // produce the final packed result.
        final long result =
                (((hi + 0x00FF00FFL) >>> 8) & 0x00FF00FFL) |
                (((lo + 0xFF00FF00L) >>> 8) & 0xFF00FF00L);

        return (int) result;
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
     * <p>Performs bi-linear interpolation on a 2x2 matrix of color values to derive the point (x, y). This is more
     * efficient than chaining {@link #mul(int, float)} calls.</p>
     *
     * <p>The results are undefined if {@param x} and {@param y} are not within the interval [0.0, 1.0].</p>
     *
     * @param m00 The packed color value for (0, 0)
     * @param m01 The packed color value for (0, 1)
     * @param m10 The packed color value for (1, 0)
     * @param m11 The packed color value for (1, 1)
     * @param x The amount to interpolate between x=0 and x=1
     * @param y The amount to interpolate between y=0 and y=1
     * @return The interpolated color value
     */
    @ApiStatus.Experimental
    public static int mix2d(int m00, int m01, int m10, int m11, float x, float y) {
        // The weights for each row and column in the matrix
        int x1 = ColorU8.normalizedFloatToByte(x), x0 = 255 - x1;
        int y1 = ColorU8.normalizedFloatToByte(y), y0 = 255 - y1;

        // Blend across the X-axis
        // (M00 * X0) + (M10 * X1)
        long row0a = ((((m00 & 0x00FF00FFL) * x0) + (((m10 & 0x00FF00FFL) * x1)) + 0x00FF00FFL) >>> 8) & 0x00FF00FFL;
        long row0b = ((((m00 & 0xFF00FF00L) * x0) + (((m10 & 0xFF00FF00L) * x1)) + 0xFF00FF00L) >>> 8) & 0xFF00FF00L;

        // (M10 * X0) + (M11 * X1)
        long row1a = ((((m01 & 0x00FF00FFL) * x0) + (((m11 & 0x00FF00FFL) * x1)) + 0x00FF00FFL) >>> 8) & 0x00FF00FFL;
        long row1b = ((((m01 & 0xFF00FF00L) * x0) + (((m11 & 0xFF00FF00L) * x1)) + 0xFF00FF00L) >>> 8) & 0xFF00FF00L;

        // Blend across the Y-axis
        // (ROW0 * Y0) + (ROW1 * Y1)
        long result = ((((row0a * y0) + ((row1a * y1)) + 0x00FF00FFL) >>> 8) & 0x00FF00FFL) |
                      ((((row0b * y0) + ((row1b * y1)) + 0xFF00FF00L) >>> 8) & 0xFF00FF00L);

        return (int) result;
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
        // De-interleave the 8-bit component lanes into high and low halves.
        // Perform 8-bit multiplication to produce Q8.8 fixed-point intermediaries.
        final long hi = (color & 0x00FF00FFL) * factor;
        final long lo = (color & 0xFF00FF00L) * factor;

        // Round the Q8.8 fixed-point values to the nearest integer, and interleave the high and low halves to
        // produce the packed result.
        final long result =
                (((hi + 0x00FF00FFL) >>> 8) & 0x00FF00FFL) |
                (((lo + 0xFF00FF00L) >>> 8) & 0xFF00FF00L);

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

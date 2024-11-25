package net.caffeinemc.mods.sodium.client.util.color;

import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.minecraft.util.Mth;

public class BoxBlur {
    public static void blur(int[] src, int[] tmp, int width, int height, int radius) {
        if (isHomogenous(src)) {
            return;
        }

        blurImpl(src, tmp, radius, width - radius, width, 0, height, height, radius); // X-axis
        blurImpl(tmp, src, radius, width - radius, width, radius, height - radius, height, radius); // Y-axis
    }

    private static void blurImpl(int[] src, int[] dst, int x0, int x1, int width, int y0, int y1, int height, int radius) {
        int windowSize = (radius * 2) + 1;
        int multiplier = getAveragingMultiplier(windowSize);

        for (int y = y0; y < y1; y++) {
            int accR = 0;
            int accG = 0;
            int accB = 0;

            int windowPivotIndex = ColorBuffer.getIndex(x0, y, width);
            int windowTailIndex = windowPivotIndex - radius;
            int windowHeadIndex = windowPivotIndex + radius;

            // Initialize window
            for (int x = -radius; x <= radius; x++) {
                var color = src[windowPivotIndex + x];
                accR += ColorARGB.unpackRed(color);
                accG += ColorARGB.unpackGreen(color);
                accB += ColorARGB.unpackBlue(color);
            }

            // Scan forwards
            int x = x0;

            while (true) {
                // The x and y coordinates are transposed to flip the output image
                // noinspection SuspiciousNameCombination
                dst[ColorBuffer.getIndex(y, x, width)] = averageRGB(accR, accG, accB, multiplier);
                x++;

                if (x >= x1) {
                    break;
                }

                {
                    // Remove the color values that are behind the window
                    var color = src[windowTailIndex++];
                    accR -= ColorARGB.unpackRed(color);
                    accG -= ColorARGB.unpackGreen(color);
                    accB -= ColorARGB.unpackBlue(color);
                }

                {
                    // Add the color values that are ahead of the window
                    var color = src[++windowHeadIndex];
                    accR += ColorARGB.unpackRed(color);
                    accG += ColorARGB.unpackGreen(color);
                    accB += ColorARGB.unpackBlue(color);
                }
            }
        }
    }

    /**
     * Pre-computes a multiplier that can be used to avoid costly division when averaging the color data in the
     * sliding window.
     * @param size The size of the rolling window
     * @author 2No2Name
     */
    private static int getAveragingMultiplier(int size) {
        return Mth.ceil((1L << 24) / (double) size);
    }

    /**
     * Calculates the average color within the sliding window using the pre-computed constant.
     * @param multiplier The pre-computed constant provided by {@link BoxBlur#getAveragingMultiplier(int)} for a window
     *                   of the given size
     * @author 2No2Name
     */
    public static int averageRGB(int red, int green, int blue, int multiplier) {
        int value = 0xFF << 24; // Alpha is constant (fully opaque)
        value |= ((blue * multiplier) >>> 24) << 0;
        value |= ((green * multiplier) >>> 24) << 8;
        value |= ((red * multiplier) >>> 24) << 16;

        return value;
    }

    private static boolean isHomogenous(int[] array) {
        int first = array[0];

        for (int i = 1; i < array.length; i++) {
            if (array[i] != first) {
                return false;
            }
        }

        return true;
    }

    public static class ColorBuffer {
        public final int[] data;
        protected final int width, height;

        public ColorBuffer(int width, int height) {
            this.data = new int[width * height];
            this.width = width;
            this.height = height;
        }

        public void set(int x, int y, int color) {
            this.data[getIndex(x, y, this.width)] = color;
        }

        public int get(int x, int y) {
            return this.data[getIndex(x, y, this.width)];
        }

        public static int getIndex(int x, int y, int width) {
            return x + (y * width);
        }
    }
}

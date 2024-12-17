package net.caffeinemc.mods.sodium.client.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.util.ColorU8;
import net.caffeinemc.mods.sodium.mixin.features.textures.NativeImageAccessor;
import org.lwjgl.system.MemoryUtil;

import java.util.Locale;

public class NativeImageHelper {
    public static long getPointerRGBA(NativeImage image) {
        if (image.format() != NativeImage.Format.RGBA) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "Tried to get pointer to RGBA pixel data on NativeImage of wrong format; have %s", image.format()));
        }

        return ((NativeImageAccessor) (Object) image) // duck type since NativeImage is final
                .getPixels();
    }

    public static void forEachPixel(NativeImage image, PointerCallback callback) {
        var ptr = NativeImageHelper.getPointerRGBA(image);
        var end = ptr + (image.getHeight() * image.getWidth() * 4L);

        while (ptr < end) {
            callback.accept(ptr);
            ptr += 4L;
        }
    }

    public static void forEachPixelInFrame(NativeImage image,
                                           int frameWidth,
                                           int frameHeight,
                                           int frameIndex,
                                           PointerCallback callback)
    {
        int frameCount = image.getWidth() / frameWidth;
        int frameX = ((frameIndex % frameCount) * frameWidth);
        int frameY = ((frameIndex / frameCount) * frameHeight);

        forEachPixelInRegion(image, frameX, frameY, frameX + frameWidth, frameY + frameHeight, callback);
    }

    public static void forEachPixelInRegion(NativeImage image,
                                            int x0,
                                            int y0,
                                            int x1,
                                            int y1,
                                            PointerCallback callback)
    {
        if (x1 < x0) {
            throw new IllegalArgumentException("Invalid region: x0 must be less than x1");
        } else if (y1 < y0) {
            throw new IllegalArgumentException("Invalid region: y0 must be less than y1");
        }

        final int width = x1 - x0;
        final int height = y1 - y0;

        if (width == 0 || height == 0) {
            throw new IllegalArgumentException("Invalid region: Must have non-zero size");
        }

        final long rowOffset = x0 * 4L;
        final long rowStride = image.getWidth() * 4L;
        final long rowLength = (x1 - x0) * 4L;

        var pImageData = NativeImageHelper.getPointerRGBA(image);

        for (long y = y0; y < y1; y++) {
            var ptr = pImageData + (y * rowStride) + rowOffset;
            var end = ptr + rowLength;

            while (ptr < end) {
                callback.accept(ptr);
                ptr += 4;
            }
        }
    }

    public static class AlphaCoverageAnalyzer implements PointerCallback {
        private final float alphaScale;
        private int alphaCoverage;

        public AlphaCoverageAnalyzer(float alphaScale) {
            this.alphaScale = alphaScale;
        }

        @Override
        public void accept(long ptr) {
            var pixel = MemoryUtil.memGetInt(ptr);
            var alpha = ColorU8.byteToNormalizedFloat(ColorABGR.unpackAlpha(pixel)) * this.alphaScale;

            if (alpha >= 0.5) {
                // The image contains a pixel that contributes to transparency
                this.alphaCoverage++;
            }
        }

        public float alphaCoverage(int pixelCount) {
            return (float) this.alphaCoverage / pixelCount;
        }
    }

    public static class RenderPassAnalyzer implements PointerCallback {
        private boolean needsAlphaTesting;
        private boolean needsAlphaBlending;

        @Override
        public void accept(long ptr) {
            var pixel = MemoryUtil.memGetInt(ptr);
            var alpha = ColorABGR.unpackAlpha(pixel);

            if (alpha < 128) { // a < 0.5
                // The image contains a pixel that affects the alpha-tested render pass
                this.needsAlphaTesting = true;
            }

            if (alpha < 255) { // a < 1.0
                // The image contains a pixel that affects the alpha-blended render pass
                this.needsAlphaBlending = true;
            }
        }

        public boolean needsAlphaTesting() {
            return this.needsAlphaTesting;
        }

        public boolean needsAlphaBlending() {
            return this.needsAlphaBlending;
        }
    }

    public interface PointerCallback {
        void accept(long ptr);
    }
}

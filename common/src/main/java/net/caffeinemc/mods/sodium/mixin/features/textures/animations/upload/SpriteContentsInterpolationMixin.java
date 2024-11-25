package net.caffeinemc.mods.sodium.mixin.features.textures.animations.upload;

import net.caffeinemc.mods.sodium.client.util.NativeImageHelper;
import net.caffeinemc.mods.sodium.mixin.features.textures.SpriteContentsInvoker;
import net.caffeinemc.mods.sodium.api.util.ColorMixer;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.List;

@Mixin(SpriteContents.InterpolationData.class)
public class SpriteContentsInterpolationMixin {
    @Shadow
    @Final
    private NativeImage[] activeFrame;

    @Unique
    private SpriteContents parent;

    @Unique
    private static final int STRIDE = 4;

    /**
     * @author IMS
     * @reason Replace fragile Shadow
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    public void assignParent(SpriteContents parent, CallbackInfo ci) {
        this.parent = parent;
    }

    /**
     * @author JellySquid
     * @reason Drastic optimizations
     */
    @Overwrite
    void uploadInterpolatedFrame(int x, int y, SpriteContents.Ticker arg) {
        SpriteContents.AnimatedTexture animation = ((SpriteContentsTickerAccessor) arg).getAnimationInfo();
        SpriteContentsAnimatedTextureAccessor animation2 = (SpriteContentsAnimatedTextureAccessor) ((SpriteContentsTickerAccessor) arg).getAnimationInfo();
        List<SpriteContents.FrameInfo> frames = ((SpriteContentsAnimatedTextureAccessor) animation).getFrames();
        SpriteContentsTickerAccessor accessor = (SpriteContentsTickerAccessor) arg;
        SpriteContentsFrameInfoAccessor animationFrame = (SpriteContentsFrameInfoAccessor) (Object) frames.get(accessor.getFrameIndex());

        int curIndex = animationFrame.getIndex();
        int nextIndex = ((SpriteContentsFrameInfoAccessor) (Object) animation2.getFrames().get((accessor.getFrameIndex() + 1) % frames.size())).getIndex();

        if (curIndex == nextIndex) {
            return;
        }

        // The mix factor between the current and next frame
        float mix = 1.0F - (float) accessor.getFrameTicks() / (float) animationFrame.getTime();

        for (int layer = 0; layer < this.activeFrame.length; layer++) {
            int width = this.parent.width() >> layer;
            int height = this.parent.height() >> layer;

            int curX = ((curIndex % animation2.getFrameRowSize()) * width);
            int curY = ((curIndex / animation2.getFrameRowSize()) * height);

            int nextX = ((nextIndex % animation2.getFrameRowSize()) * width);
            int nextY = ((nextIndex / animation2.getFrameRowSize()) * height);

            NativeImage src = ((SpriteContentsAccessor) this.parent).getImages()[layer];
            NativeImage dst = this.activeFrame[layer];

            long ppSrcPixel = NativeImageHelper.getPointerRGBA(src);
            long ppDstPixel = NativeImageHelper.getPointerRGBA(dst);

            for (int layerY = 0; layerY < height; layerY++) {
                // Pointers to the pixel array for the current and next frame
                long pRgba1 = ppSrcPixel + (curX + (long) (curY + layerY) * src.getWidth()) * STRIDE;
                long pRgba2 = ppSrcPixel + (nextX + (long) (nextY + layerY) * src.getWidth()) * STRIDE;

                for (int layerX = 0; layerX < width; layerX++) {
                    int rgba1 = MemoryUtil.memGetInt(pRgba1);
                    int rgba2 = MemoryUtil.memGetInt(pRgba2);

                    // Mix the RGB components and truncate the A component
                    int mixedRgb = ColorMixer.mix(rgba1, rgba2, mix) & 0x00FFFFFF;

                    // Take the A component from the source pixel
                    int alpha = rgba1 & 0xFF000000;

                    // Update the pixel within the interpolated frame using the combined RGB and A components
                    MemoryUtil.memPutInt(ppDstPixel, mixedRgb | alpha);

                    pRgba1 += STRIDE;
                    pRgba2 += STRIDE;

                    ppDstPixel += STRIDE;
                }
            }
        }

        ((SpriteContentsInvoker) this.parent).invokeUpload(x, y, 0, 0, this.activeFrame);
    }
}

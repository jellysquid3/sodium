package me.jellysquid.mods.sodium.mixin.features.texture_updates;

import com.mojang.blaze3d.platform.NativeImage;
import me.jellysquid.mods.sodium.client.util.color.ColorMixer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(TextureAtlasSprite.InterpolationData.class)
public class MixinSpriteInterpolationData {
    @Shadow
    @Final
    private NativeImage[] activeFrame;

    @Shadow(aliases = {"this$0", "field_21757"})
    private TextureAtlasSprite parent;

    private static final int STRIDE = 4;

    /**
     * @author JellySquid
     * @reason Drastic optimizations
     */
    @Overwrite
    void uploadInterpolatedFrame(TextureAtlasSprite.AnimatedTexture animation) {
        TextureAtlasSprite.FrameInfo animationFrame = animation.frames.get(animation.frame);

        int curIndex = animationFrame.index;
        int nextIndex = animation.frames.get((animation.frame + 1) % animation.frames.size()).index;

        if (curIndex == nextIndex) {
            return;
        }

        float delta = 1.0F - (float) animation.subFrame / (float) animationFrame.time;

        int f1 = ColorMixer.getStartRatio(delta);
        int f2 = ColorMixer.getEndRatio(delta);

        for (int layer = 0; layer < this.activeFrame.length; layer++) {
            int width = this.parent.width >> layer;
            int height = this.parent.height >> layer;

            int curX = ((curIndex % animation.frameRowSize) * width);
            int curY = ((curIndex / animation.frameRowSize) * height);

            int nextX = ((nextIndex % animation.frameRowSize) * width);
            int nextY = ((nextIndex / animation.frameRowSize) * height);

            NativeImage src = this.parent.mainImage[layer];
            NativeImage dst = this.activeFrame[layer];

            // Source pointers
            long s1p = src.pixels + (curX + ((long) curY * src.getWidth()) * STRIDE);
            long s2p = src.pixels + (nextX + ((long) nextY * src.getWidth()) * STRIDE);

            // Destination pointers
            long dp = dst.pixels;

            int pixelCount = width * height;

            for (int i = 0; i < pixelCount; i++) {
                MemoryUtil.memPutInt(dp, ColorMixer.mixARGB(MemoryUtil.memGetInt(s1p), MemoryUtil.memGetInt(s2p), f1, f2));

                s1p += STRIDE;
                s2p += STRIDE;
                dp += STRIDE;
            }
        }

        this.parent.upload(0, 0, this.activeFrame);
    }

}


package net.caffeinemc.mods.sodium.mixin.features.textures.mipmaps;

import com.mojang.blaze3d.platform.NativeImage;
import net.caffeinemc.mods.sodium.client.MipAdjuster;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteContents.class)
public abstract class SpriteContentsMixin {
    @Inject(method = "<init>", at = @At(value = "RETURN"))
    private void sodium$beforeGenerateMipLevels(ResourceLocation name, FrameSize frameSize, NativeImage originalImage, ResourceMetadata metadata, CallbackInfo ci) {
        MipAdjuster.fillInTransparentPixelColors(originalImage);
    }

    @Inject(method = "increaseMipLevel", at = @At("RETURN"))
    private void modifyMipMaps(int mipLevels, CallbackInfo ci) {
        float originalCoverage = MipAdjuster.calculateAlphaCoverage(this.originalImage, this.width, this.height, 0, 1.0f);

        for (int frameIndex = 0; frameIndex < this.getFrameCount(); frameIndex++) {
            for (int mipIndex = 1; mipIndex < mipLevels; mipIndex++) {
                MipAdjuster.scaleAlphaToCoverage(
                        this.byMipLevel[mipIndex],
                        this.width >> mipLevels,
                        this.height >> mipLevels,
                        frameIndex,
                        originalCoverage);
            }
        }
    }

    @Shadow
    @Final
    private NativeImage originalImage;

    @Shadow
    @Final
    int height;

    @Shadow @Final
    int width;

    @Shadow
    NativeImage[] byMipLevel;

    @Shadow
    protected abstract int getFrameCount();
}
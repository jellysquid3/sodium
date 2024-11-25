package net.caffeinemc.mods.sodium.mixin.features.textures.animations.tracking;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.render.texture.SpriteContentsExtension;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(SpriteContents.Ticker.class)
public class SpriteContentsTickerMixin {
    @Shadow
    int subFrame;
    @Shadow
    @Final
    SpriteContents.AnimatedTexture animationInfo;
    @Shadow
    int frame;

    @Unique
    private SpriteContents parent;

    /**
     * @author IMS
     * @reason Replace fragile Shadow
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    public void assignParent(SpriteContents spriteContents, SpriteContents.AnimatedTexture animation, SpriteContents.InterpolationData interpolation, CallbackInfo ci) {
        this.parent = spriteContents;
    }

    @Inject(method = "tickAndUpload", at = @At("HEAD"), cancellable = true)
    private void preTick(CallbackInfo ci) {
        SpriteContentsExtension parent = (SpriteContentsExtension) this.parent;

        boolean onDemand = SodiumClientMod.options().performance.animateOnlyVisibleTextures;

        if (onDemand && !parent.sodium$isActive()) {
            this.subFrame++;
            List<SpriteContents.FrameInfo> frames = ((AnimatedTextureAccessor)this.animationInfo).getFrames();
            if (this.subFrame >= ((SpriteContentsFrameInfoAccessor) (Object) frames.get(this.frame)).getTime()) {
                this.frame = (this.frame + 1) % frames.size();
                this.subFrame = 0;
            }
            ci.cancel();
        }
    }

    @Inject(method = "tickAndUpload", at = @At("TAIL"))
    private void postTick(CallbackInfo ci) {
        SpriteContentsExtension parent = (SpriteContentsExtension) this.parent;
        parent.sodium$setActive(false);
    }
}

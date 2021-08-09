package me.jellysquid.mods.sodium.mixin.features.texture_tracking;

import me.jellysquid.mods.sodium.client.render.texture.TextureAtlasSpriteExtended;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(TextureAtlasSprite.AnimatedTexture.class)
public class MixinAnimatedTexture {
    @Shadow(aliases = {"this$0", "field_28469"})
    private TextureAtlasSprite parent;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void preTick(CallbackInfo ci) {
        TextureAtlasSpriteExtended parent = (TextureAtlasSpriteExtended) this.parent;

        if (!parent.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void postTick(CallbackInfo ci) {
        TextureAtlasSpriteExtended parent = (TextureAtlasSpriteExtended) this.parent;
        parent.setActive(false);
    }
}

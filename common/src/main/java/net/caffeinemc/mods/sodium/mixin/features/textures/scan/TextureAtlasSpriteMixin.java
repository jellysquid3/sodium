package net.caffeinemc.mods.sodium.mixin.features.textures.scan;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.TextureAtlasSpriteExtension;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteTicker;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TextureAtlasSprite.class)
public class TextureAtlasSpriteMixin implements TextureAtlasSpriteExtension {
    @Unique
    private boolean hasUnknownImageContents;

    @WrapOperation(method = "createTicker", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/SpriteContents;createTicker()Lnet/minecraft/client/renderer/texture/SpriteTicker;"))
    private SpriteTicker hookTickerInstantiation(SpriteContents instance, Operation<SpriteTicker> original) {
        var ticker = original.call(instance);

        if (ticker != null && !(ticker instanceof SpriteContents.Ticker)) {
            this.hasUnknownImageContents = true;
        }

        return ticker;
    }

    @Override
    public boolean sodium$hasUnknownImageContents() {
        return this.hasUnknownImageContents;
    }
}

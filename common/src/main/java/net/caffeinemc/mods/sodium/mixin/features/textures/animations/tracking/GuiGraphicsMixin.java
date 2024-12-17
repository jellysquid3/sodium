package net.caffeinemc.mods.sodium.mixin.features.textures.animations.tracking;

import net.caffeinemc.mods.sodium.api.texture.SpriteUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public class GuiGraphicsMixin {
    @Inject(method = "blit(IIIIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V", at = @At("HEAD"))
    private void preDrawSprite(int x, int y, int z,
                               int width, int height,
                               TextureAtlasSprite sprite,
                               CallbackInfo ci)
    {
        SpriteUtil.INSTANCE.markSpriteActive(sprite);
    }

    @Inject(method = "blit(IIIIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;FFFF)V", at = @At("HEAD"))
    private void preDrawSprite(int x, int y, int z,
                               int width, int height,
                               TextureAtlasSprite sprite,
                               float red, float green, float blue, float alpha,
                               CallbackInfo ci)
    {
        SpriteUtil.INSTANCE.markSpriteActive(sprite);
    }
}

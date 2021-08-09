package me.jellysquid.mods.sodium.client.render.texture;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public class SpriteUtil {
    public static void markSpriteActive(TextureAtlasSprite sprite) {
        if (sprite instanceof TextureAtlasSpriteExtended) {
            ((TextureAtlasSpriteExtended) sprite).setActive(true);
        }
    }
}

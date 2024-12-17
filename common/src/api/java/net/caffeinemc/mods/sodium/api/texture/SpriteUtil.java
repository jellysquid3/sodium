package net.caffeinemc.mods.sodium.api.texture;

import net.caffeinemc.mods.sodium.api.internal.DependencyInjection;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
@ApiStatus.AvailableSince("0.6.0")
public interface SpriteUtil {
    SpriteUtil INSTANCE = DependencyInjection.load(SpriteUtil.class,
            "net.caffeinemc.mods.sodium.client.render.texture.SpriteUtilImpl");
    
    /**
     * Marks a provided sprite as active
     * @param sprite The sprite you want to mark as active
     */
    void markSpriteActive(@NotNull TextureAtlasSprite sprite);

    /**
     * Checks if the provided sprite has an animation
     * 
     * @param sprite The sprite you want to check
     * @return true if the provided sprite has an animation, false if it doesn't
     */
    boolean hasAnimation(@NotNull TextureAtlasSprite sprite);
}

package net.caffeinemc.mods.sodium.client.model.quad;

import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFlags;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/**
 * Provides a mutable view to a model quad.
 */
public interface ModelQuadViewMutable extends ModelQuadView {
    /**
     * Sets the x-position of the vertex at index {@param idx} to the value {@param x}
     */
    void setX(int idx, float x);

    /**
     * Sets the y-position of the vertex at index {@param idx} to the value {@param y}
     */
    void setY(int idx, float y);

    /**
     * Sets the z-position of the vertex at index {@param idx} to the value {@param z}
     */
    void setZ(int idx, float z);

    /**
     * Sets the integer-encoded color of the vertex at index {@param idx} to the value {@param color}
     */
    void setColor(int idx, int color);

    /**
     * Sets the texture x-coordinate of the vertex at index {@param idx} to the value {@param u}
     */
    void setTexU(int idx, float u);

    /**
     * Sets the texture y-coordinate of the vertex at index {@param idx} to the value {@param v}
     */
    void setTexV(int idx, float v);

    /**
     * Sets the light map texture coordinate of the vertex at index {@param idx} to the value {@param light}
     */
    void setLight(int idx, int light);

    /**
     * Sets the normal vector of the vertex at index {@param idx} to the value {@param normal}
     */
    void setNormal(int idx, int normal);

    /**
     * Sets the normal vector of the face to the value {@param normal}
     */
    void setFaceNormal(int normal);

    /**
     * Sets the bit-flag field which contains the {@link ModelQuadFlags} for this quad
     */
    void setFlags(int flags);

    /**
     * Sets the sprite used by this quad
     */
    void setSprite(TextureAtlasSprite sprite);

    /**
     * Sets the face used by this quad for lighting effects
     */
    void setLightFace(Direction direction);

    /**
     * Sets the tint index used by this quad
     */
    void setTintIndex(int index);
}

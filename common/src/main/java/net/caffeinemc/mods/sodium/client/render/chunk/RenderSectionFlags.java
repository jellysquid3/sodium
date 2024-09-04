package net.caffeinemc.mods.sodium.client.render.chunk;

public class RenderSectionFlags {
    public static final int HAS_BLOCK_GEOMETRY      = 0;
    public static final int HAS_BLOCK_ENTITIES      = 1;
    public static final int HAS_ANIMATED_SPRITES    = 2;
    public static final int IS_BUILT                = 3;

    public static final int MASK_HAS_BLOCK_GEOMETRY      = 1 << HAS_BLOCK_GEOMETRY;
    public static final int MASK_HAS_BLOCK_ENTITIES      = 1 << HAS_BLOCK_ENTITIES;
    public static final int MASK_HAS_ANIMATED_SPRITES    = 1 << HAS_ANIMATED_SPRITES;
    public static final int MASK_IS_BUILT                = 1 << IS_BUILT;
    public static final int MASK_NEEDS_RENDER           = MASK_HAS_BLOCK_GEOMETRY | MASK_HAS_BLOCK_ENTITIES | MASK_HAS_ANIMATED_SPRITES;

    public static final int NONE = 0;
}

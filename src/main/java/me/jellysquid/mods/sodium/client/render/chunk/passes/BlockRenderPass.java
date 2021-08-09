package me.jellysquid.mods.sodium.client.render.chunk.passes;

import net.minecraft.client.renderer.RenderType;

// TODO: Move away from using an enum, make this extensible
public enum BlockRenderPass {
    SOLID(RenderType.solid(), false),
    CUTOUT(RenderType.cutout(), false),
    CUTOUT_MIPPED(RenderType.cutoutMipped(), false),
    TRANSLUCENT(RenderType.translucent(), true),
    TRIPWIRE(RenderType.tripwire(), true);

    public static final BlockRenderPass[] VALUES = BlockRenderPass.values();
    public static final int COUNT = VALUES.length;

    private final RenderType layer;
    private final boolean translucent;

    BlockRenderPass(RenderType layer, boolean translucent) {
        this.layer = layer;
        this.translucent = translucent;
    }

    public boolean isTranslucent() {
        return this.translucent;
    }

    public RenderType getLayer() {
        return this.layer;
    }

    @Deprecated
    public void endDrawing() {
        this.layer.clearRenderState();
    }

    @Deprecated
    public void startDrawing() {
        this.layer.setupRenderState();
    }
}

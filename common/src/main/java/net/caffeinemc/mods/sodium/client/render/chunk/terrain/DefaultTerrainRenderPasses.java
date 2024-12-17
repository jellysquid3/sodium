package net.caffeinemc.mods.sodium.client.render.chunk.terrain;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class DefaultTerrainRenderPasses {
    public static final TerrainRenderPass SOLID = new TerrainRenderPass(RenderType.solid(), false, false);
    public static final TerrainRenderPass CUTOUT = new TerrainRenderPass(RenderType.cutoutMipped(), false, true);
    public static final TerrainRenderPass TRANSLUCENT = new TerrainRenderPass(RenderType.translucent(), true, false);

    public static final TerrainRenderPass[] ALL = new TerrainRenderPass[] { SOLID, CUTOUT, TRANSLUCENT };

    public static TerrainRenderPass forBlockState(BlockState state) {
        return forRenderLayer(ItemBlockRenderTypes.getChunkRenderType(state));
    }

    public static TerrainRenderPass forFluidState(FluidState state) {
        return forRenderLayer(ItemBlockRenderTypes.getRenderLayer(state));
    }

    public static TerrainRenderPass forRenderLayer(RenderType layer) {
        if (layer == RenderType.solid()) {
            return SOLID;
        } else if (layer == RenderType.cutout() || layer == RenderType.cutoutMipped()) {
            return CUTOUT;
        } else if (layer == RenderType.translucent() || layer == RenderType.tripwire()) {
            return TRANSLUCENT;
        }

        throw new IllegalArgumentException("No render pass mapping exists for render layer: " + layer);
    }
}

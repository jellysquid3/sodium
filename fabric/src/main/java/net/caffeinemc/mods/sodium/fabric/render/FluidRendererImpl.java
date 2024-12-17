package net.caffeinemc.mods.sodium.fabric.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.client.model.color.ColorProvider;
import net.caffeinemc.mods.sodium.client.model.color.ColorProviderRegistry;
import net.caffeinemc.mods.sodium.client.model.light.LightPipelineProvider;
import net.caffeinemc.mods.sodium.client.model.quad.blender.BlendedColorProvider;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.FluidRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.services.FluidRendererFactory;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRendering;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public class FluidRendererImpl extends FluidRenderer {
    private final ColorProviderRegistry colorProviderRegistry;
    private final DefaultFluidRenderer defaultRenderer;
    private final DefaultRenderContext defaultContext;

    public FluidRendererImpl(ColorProviderRegistry colorProviderRegistry, LightPipelineProvider lighters) {
        this.colorProviderRegistry = colorProviderRegistry;
        defaultRenderer = new DefaultFluidRenderer(lighters);
        defaultContext = new DefaultRenderContext();
    }

    @Override
    public void render(LevelSlice level, BlockState blockState, FluidState fluidState, BlockPos blockPos, BlockPos offset, TranslucentGeometryCollector collector, ChunkBuildBuffers buffers) {
        var meshBuilder = buffers.get(DefaultTerrainRenderPasses.forFluidState(fluidState));

        FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(fluidState.getType());
        boolean hasModOverride = FluidRenderHandlerRegistry.INSTANCE.getOverride(fluidState.getType()) != null;

        // Match the vanilla FluidRenderer's behavior if the handler is null
        if (handler == null) {
            boolean isLava = fluidState.is(FluidTags.LAVA);
            handler = FluidRenderHandlerRegistry.INSTANCE.get(isLava ? Fluids.LAVA : Fluids.WATER);
        }

        // Invoking FluidRenderHandler#renderFluid can invoke vanilla FluidRenderer#render.
        //
        // Sodium cannot let vanilla FluidRenderer#render run (during the invocation of FluidRenderHandler#renderFluid)
        // for two reasons.
        // 1. It is the hot path and vanilla FluidRenderer#render is not very fast.
        // 2. Fabric API's mixins to FluidRenderer#render expect it to be initially called from the chunk rebuild task,
        // not from inside FluidRenderHandler#renderFluid. Not upholding this assumption will result in all custom
        // geometry to be buffered twice.
        //
        // The default implementation of FluidRenderHandler#renderFluid invokes vanilla FluidRenderer#render, but
        // Fabric API does not support invoking vanilla FluidRenderer#render from FluidRenderHandler#renderFluid
        // directly and it does not support calling the default implementation of FluidRenderHandler#renderFluid (super)
        // more than once. Because of this, the parameters to vanilla FluidRenderer#render will be the same as those
        // initially passed to FluidRenderHandler#renderFluid, so they can be ignored.
        //
        // Due to all the above, Sodium injects into head of vanilla FluidRenderer#render before Fabric API and cancels
        // the call if it was invoked from inside FluidRenderHandler#renderFluid. The injector ends up calling
        // DefaultFluidRenderer#render, which emulates what vanilla FluidRenderer#render does, but is more efficient.
        // To allow invoking this method from the injector, where there is no local Sodium context, the renderer and
        // parameters are bundled into a DefaultRenderContext which is stored in a ThreadLocal.

        defaultContext.setUp(this.colorProviderRegistry, this.defaultRenderer, level, blockState, fluidState, blockPos, offset, collector, meshBuilder, handler, hasModOverride);

        try {
            FluidRendering.render(handler, level, blockPos, meshBuilder.asFallbackVertexConsumer(collector), blockState, fluidState, defaultContext);
        } finally {
            defaultContext.clear();
        }
    }

    private static class DefaultRenderContext implements FluidRendering.DefaultRenderer {
        private DefaultFluidRenderer renderer;
        private LevelSlice level;
        private BlockState blockState;
        private FluidState fluidState;
        private BlockPos blockPos;
        private BlockPos offset;
        private TranslucentGeometryCollector collector;
        private ChunkModelBuilder meshBuilder;
        private FluidRenderHandler handler;
        private ColorProviderRegistry colorProviderRegistry;
        private boolean hasModOverride;

        public void setUp(ColorProviderRegistry colorProviderRegistry, DefaultFluidRenderer renderer, LevelSlice level, BlockState blockState, FluidState fluidState, BlockPos blockPos, BlockPos offset, TranslucentGeometryCollector collector, ChunkModelBuilder meshBuilder, FluidRenderHandler handler, boolean hasModOverride) {
            this.colorProviderRegistry = colorProviderRegistry;
            this.renderer = renderer;
            this.level = level;
            this.blockState = blockState;
            this.fluidState = fluidState;
            this.blockPos = blockPos;
            this.offset = offset;
            this.collector = collector;
            this.meshBuilder = meshBuilder;
            this.handler = handler;
            this.hasModOverride = hasModOverride;
        }

        public void clear() {
            this.renderer = null;
            this.level = null;
            this.blockState = null;
            this.fluidState = null;
            this.blockPos = null;
            this.offset = null;
            this.collector = null;
            this.meshBuilder = null;
            this.handler = null;
            this.hasModOverride = false;
        }

        public ColorProvider<FluidState> getColorProvider(Fluid fluid) {
            var override = this.colorProviderRegistry.getColorProvider(fluid);

            if (!hasModOverride && override != null) {
                return override;
            }

            return FabricColorProviders.adapt(handler);
        }

        @Override
        public void render(FluidRenderHandler handler, BlockAndTintGetter world, BlockPos pos, VertexConsumer vertexConsumer, BlockState blockState, FluidState fluidState) {
            this.renderer.render(this.level, this.blockState, this.fluidState, this.blockPos, this.offset, this.collector, this.meshBuilder,
                    getColorProvider(fluidState.getType()), handler.getFluidSprites(this.level, this.blockPos, this.fluidState));
        }
    }

    public static class FabricFactory implements FluidRendererFactory {
        @Override
        public FluidRenderer createPlatformFluidRenderer(ColorProviderRegistry colorRegistry, LightPipelineProvider lightPipelineProvider) {
            return new FluidRendererImpl(colorRegistry, lightPipelineProvider);
        }

        @Override
        public BlendedColorProvider<FluidState> getWaterColorProvider() {
            return new BlendedColorProvider<>() {
                @Override
                protected int getColor(LevelSlice slice, FluidState state, BlockPos pos) {
                    return BiomeColors.getAverageWaterColor(slice, pos) | 0xFF000000;
                }
            };
        }

        @Override
        public BlendedColorProvider<BlockState> getWaterBlockColorProvider() {
            return new BlendedColorProvider<>() {
                @Override
                protected int getColor(LevelSlice slice, BlockState state, BlockPos pos) {
                    return BiomeColors.getAverageWaterColor(slice, pos) | 0xFF000000;
                }
            };
        }
    }
}

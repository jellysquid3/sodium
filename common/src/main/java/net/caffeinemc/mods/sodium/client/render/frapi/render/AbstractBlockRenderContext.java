package net.caffeinemc.mods.sodium.client.render.frapi.render;

import net.caffeinemc.mods.sodium.client.model.light.LightMode;
import net.caffeinemc.mods.sodium.client.model.light.LightPipeline;
import net.caffeinemc.mods.sodium.client.model.light.LightPipelineProvider;
import net.caffeinemc.mods.sodium.client.model.light.data.QuadLightData;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.ShapeComparisonCache;
import net.caffeinemc.mods.sodium.client.render.frapi.SodiumRenderer;
import net.caffeinemc.mods.sodium.client.render.frapi.helper.ColorHelper;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.EncodingFormat;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableQuadViewImpl;
import net.caffeinemc.mods.sodium.client.services.PlatformBlockAccess;
import net.caffeinemc.mods.sodium.client.services.PlatformModelAccess;
import net.caffeinemc.mods.sodium.client.services.SodiumModelData;
import net.caffeinemc.mods.sodium.client.util.DirectionUtil;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.material.ShadeMode;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ModelHelper;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Base class for the functions that can be shared between the terrain and non-terrain pipelines.
 *
 * <p>Make sure to set the {@link #lighters} in the subclass constructor.
 */
public abstract class AbstractBlockRenderContext extends AbstractRenderContext {
    private static final RenderMaterial[] STANDARD_MATERIALS;
    private static final RenderMaterial TRANSLUCENT_MATERIAL = SodiumRenderer.INSTANCE.materialFinder().blendMode(BlendMode.TRANSLUCENT).find();

    static {
        STANDARD_MATERIALS = new RenderMaterial[AmbientOcclusionMode.values().length];

        AmbientOcclusionMode[] values = AmbientOcclusionMode.values();
        for (int i = 0; i < values.length; i++) {
            TriState state = switch (values[i]) {
                case ENABLED -> TriState.TRUE;
                case DISABLED -> TriState.FALSE;
                case DEFAULT -> TriState.DEFAULT;
            };

            STANDARD_MATERIALS[i] = SodiumRenderer.INSTANCE.materialFinder().ambientOcclusion(state).find();
        }
    }

    public class BlockEmitter extends MutableQuadViewImpl {
        {
            data = new int[EncodingFormat.TOTAL_STRIDE];
            clear();
        }

        public void bufferDefaultModel(BakedModel model, BlockState state, Predicate<Direction> cullTest) {
            AbstractBlockRenderContext.this.bufferDefaultModel(model, state, cullTest);
        }

        @Override
        public void emitDirectly() {
            if (type == null) {
                throw new IllegalStateException("No render type is set but an FRAPI object was asked to render!");
            }
            renderQuad(this);
        }
    }



    private final BlockEmitter editorQuad = new BlockEmitter();

    /**
     * The world which the block is being rendered in.
     */
    protected BlockAndTintGetter level;
    /**
     * The level slice used for rendering
     */
    protected LevelSlice slice;
    /**
     * The state of the block being rendered.
     */
    protected BlockState state;
    /**
     * The position (in world space) of the block being rendered.
     */
    protected BlockPos pos;

    /**
     * The current render type being rendered.
     */
    protected RenderType type;

    /**
     * The current model's model data.
     */
    protected SodiumModelData modelData;

    private final ShapeComparisonCache occlusionCache = new ShapeComparisonCache();
    private final BlockPos.MutableBlockPos cachedPositionObject = new BlockPos.MutableBlockPos();
    private boolean enableCulling = true;
    // Cull cache (as it's checked per-quad instead of once per side like in vanilla)
    private int cullCompletionFlags;
    private int cullResultFlags;

    protected RandomSource random;
    protected long randomSeed;
    protected final Supplier<RandomSource> randomSupplier = () -> {
        random.setSeed(randomSeed);
        return random;
    };

    /**
     * Must be set by the subclass constructor.
     */
    protected LightPipelineProvider lighters;
    protected final QuadLightData quadLightData = new QuadLightData();
    protected boolean useAmbientOcclusion;
    // Default AO mode for model (can be overridden by material property)
    protected LightMode defaultLightMode;

    @Override
    public QuadEmitter getEmitter() {
        this.editorQuad.clear();
        return this.editorQuad;
    }

    /**
     * @param facing The facing direction of the side to check
     * @return True if the block side facing {@param facing} is not occluded, otherwise false
     */
    public boolean shouldDrawSide(Direction facing) {
        BlockPos.MutableBlockPos neighborPos = this.cachedPositionObject;
        neighborPos.setWithOffset(this.pos, facing);

        // The block state of the neighbor
        BlockState neighborBlockState = this.level.getBlockState(neighborPos);

        // The cull shape of the neighbor between the block being rendered and it
        VoxelShape neighborShape = neighborBlockState.getFaceOcclusionShape(DirectionUtil.getOpposite(facing));

        // Minecraft enforces that if the neighbor has a full-block occlusion shape, the face is always hidden
        if (ShapeComparisonCache.isFullShape(neighborShape)) {
            return false;
        }

        // Blocks can define special behavior to control whether their faces are rendered.
        // This is mostly used by transparent blocks (Leaves, Glass, etc.) to not render interior faces between blocks
        // of the same type.
        if (this.state.skipRendering(neighborBlockState, facing)) {
            return false;
        } else if (PlatformBlockAccess.getInstance()
                .shouldSkipRender(this.level, this.state, neighborBlockState, this.pos, neighborPos, facing)) {
            return false;
        }

        // After any custom behavior has been handled, check if the neighbor block is transparent or has an empty
        // cull shape. These blocks cannot hide any geometry.
        if (ShapeComparisonCache.isEmptyShape(neighborShape) || !neighborBlockState.canOcclude()) {
            return true;
        }

        // The cull shape between of the block being rendered, between it and the neighboring block
        VoxelShape selfShape = this.state.getFaceOcclusionShape(facing);

        // If the block being rendered has an empty cull shape, there will be no intersection with the neighboring
        // block's cull shape, so no geometry can be hidden.
        if (ShapeComparisonCache.isEmptyShape(selfShape)) {
            return true;
        }

        // No other simplifications apply, so we need to perform a full shape comparison, which is very slow
        return this.occlusionCache.lookup(selfShape, neighborShape);
    }

    public boolean isFaceCulled(@Nullable Direction face) {
        if (face == null || !this.enableCulling) {
            return false;
        }

        final int mask = 1 << face.get3DDataValue();

        if ((this.cullCompletionFlags & mask) == 0) {
            this.cullCompletionFlags |= mask;

            if (this.shouldDrawSide(face)) {
                this.cullResultFlags |= mask;
                return false;
            } else {
                return true;
            }
        } else {
            return (this.cullResultFlags & mask) == 0;
        }
    }

    /**
     * Pipeline entrypoint - handles transform and culling checks.
     */
    private void renderQuad(MutableQuadViewImpl quad) {
        if (this.isFaceCulled(quad.cullFace())) {
            return;
        }

        this.processQuad(quad);
    }

    /**
     * Quad pipeline function - after transform and culling checks.
     * Can also be used as entrypoint to skip some logic if the transform and culling checks have already been performed.
     */
    protected abstract void processQuad(MutableQuadViewImpl quad);

    protected void prepareCulling(boolean enableCulling) {
        this.enableCulling = enableCulling;
        this.cullCompletionFlags = 0;
        this.cullResultFlags = 0;
    }

    protected void prepareAoInfo(boolean modelAo) {
        this.useAmbientOcclusion = Minecraft.useAmbientOcclusion();
        // Ignore the incorrect IDEA warning here.
        this.defaultLightMode = this.useAmbientOcclusion && modelAo && PlatformBlockAccess.getInstance().getLightEmission(state, level, pos) == 0 ? LightMode.SMOOTH : LightMode.FLAT;
    }

    protected void shadeQuad(MutableQuadViewImpl quad, LightMode lightMode, boolean emissive, ShadeMode shadeMode) {
        LightPipeline lighter = this.lighters.getLighter(lightMode);
        QuadLightData data = this.quadLightData;
        lighter.calculate(quad, this.pos, data, quad.cullFace(), quad.lightFace(), quad.hasShade(), shadeMode == ShadeMode.ENHANCED);

        if (emissive) {
            for (int i = 0; i < 4; i++) {
                quad.lightmap(i, LightTexture.FULL_BRIGHT);
            }
        } else {
            int[] lightmaps = data.lm;

            for (int i = 0; i < 4; i++) {
                quad.lightmap(i, ColorHelper.maxBrightness(quad.lightmap(i), lightmaps[i]));
            }
        }
    }

    /* Handling of vanilla models - this is the hot path for non-modded models */
    public void bufferDefaultModel(BakedModel model, @Nullable BlockState state, Predicate<Direction> cullTest) {
        MutableQuadViewImpl editorQuad = this.editorQuad;


        // If there is no transform, we can check the culling face once for all the quads,
        // and we don't need to check for transforms per-quad.

        for (int i = 0; i <= ModelHelper.NULL_FACE_ID; i++) {
            final Direction cullFace = ModelHelper.faceFromIndex(i);

            if (cullTest.test(cullFace)) {
                continue;
            }

            RandomSource random = this.randomSupplier.get();
            AmbientOcclusionMode ao = PlatformBlockAccess.getInstance().usesAmbientOcclusion(model, state, modelData, type, slice, pos);

            final List<BakedQuad> quads = PlatformModelAccess.getInstance().getQuads(level, pos, model, state, cullFace, random, type, modelData);
            final int count = quads.size();

            for (int j = 0; j < count; j++) {
                final BakedQuad q = quads.get(j);
                editorQuad.fromVanilla(q, (type == RenderType.tripwire() || type == RenderType.translucent()) ? TRANSLUCENT_MATERIAL : STANDARD_MATERIALS[ao.ordinal()], cullFace);
                // Call processQuad instead of emit for efficiency
                // (avoid unnecessarily clearing data, trying to apply transforms, and performing cull check again)

                editorQuad.transformAndEmit();
            }
        }

        editorQuad.clear();
    }

    public SodiumModelData getModelData() {
        return modelData;
    }

    public RenderType getRenderType() {
        return type;
    }
}

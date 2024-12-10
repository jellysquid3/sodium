package net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline;


import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.caffeinemc.mods.sodium.api.util.NormI8;
import net.caffeinemc.mods.sodium.client.model.color.ColorProvider;
import net.caffeinemc.mods.sodium.client.model.light.LightMode;
import net.caffeinemc.mods.sodium.client.model.light.LightPipeline;
import net.caffeinemc.mods.sodium.client.model.light.LightPipelineProvider;
import net.caffeinemc.mods.sodium.client.model.light.data.QuadLightData;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuad;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadViewMutable;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFlags;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.client.services.PlatformBlockAccess;
import net.caffeinemc.mods.sodium.client.util.DirectionUtil;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// TODO: fix perfectly joining stairs from being water-transmitting and making weird fluid shapes, use shape operation BooleanOp.AND (extend cache to support it)
public class DefaultFluidRenderer {
    // TODO: allow this to be changed by vertex format, WARNING: make sure TranslucentGeometryCollector knows about EPSILON
    // TODO: move fluid rendering to a separate render pass and control glPolygonOffset and glDepthFunc to fix this properly
    public static final float EPSILON = 0.001f;
    private static final float ALIGNED_EQUALS_EPSILON = 0.011f;

    private static final float DISCARD_SAMPLE = -1.0f;
    private static final float FULL_HEIGHT = 0.8888889f;
    private static final float FLATTENING_FACTOR = 0.07f;

    private final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos occlusionScratchPos = new BlockPos.MutableBlockPos();
    private float scratchHeight = 0.0f;
    private int scratchSamples = 0;

    private final ShapeComparisonCache occlusionCache = new ShapeComparisonCache();

    private final ModelQuadViewMutable quad = new ModelQuad();

    private final LightPipelineProvider lighters;

    private final QuadLightData quadLightData = new QuadLightData();
    private final int[] quadColors = new int[4];
    private final float[] brightness = new float[4];

    private final ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();

    public DefaultFluidRenderer(LightPipelineProvider lighters) {
        this.quad.setLightFace(Direction.UP);

        this.lighters = lighters;
    }

    /**
     * Checks if a face of a fluid block, assumed to be a full block for now, should be considered for rendering based on the neighboring block state, but not the voxel shapes (that test is done later).
     *
     * @param view    The block view for this render context
     * @param selfPos The position of the fluid
     * @param facing  The facing direction of the side to check
     * @param fluid   The fluid state
     * @return True if the fluid side facing {@param facing} is not occluded, otherwise false
     */
    public boolean isFullBlockFluidSideVisible(BlockGetter view, BlockPos selfPos, Direction facing, FluidState fluid) {
        // perform occlusion against the neighboring block
        BlockState otherState = view.getBlockState(this.occlusionScratchPos.setWithOffset(selfPos, facing));

        // check for special fluid occlusion behavior
        if (PlatformBlockAccess.getInstance().shouldOccludeFluid(facing.getOpposite(), otherState, fluid)) {
            return false;
        }

        // don't render anything if the other blocks is the same fluid
        // NOTE: this check is already included in the default implementation of the above shouldOccludeFluid
        if (otherState.getFluidState().getType().isSame(fluid.getType())) {
            return false;
        }

        // the up direction doesn't do occlusion with other block shapes
        if (facing == Direction.UP) {
            return true;
        }

        // only occlude against blocks that can potentially occlude in the first place
        if (!otherState.canOcclude()) {
            return true;
        }

        var otherShape = otherState.getFaceOcclusionShape(DirectionUtil.getOpposite(facing));

        // If the other block has an empty cull shape, then it cannot hide any geometry
        if (ShapeComparisonCache.isEmptyShape(otherShape)) {
            return true;
        }

        // If both blocks use a full-cube cull shape, then they will always hide the faces between each other.
        // No voxel shape comparison is done after this point because it's redundant with the later more accurate check.
        return !ShapeComparisonCache.isFullShape(otherShape);
    }

    /**
     * Checks if a face of the fluid is occluded by the block it's contained in.
     *
     * @param selfBlockState The state of the block in the level
     * @param facing         The facing direction of the side to check
     * @param fluidShape     The shape of the fluid
     * @return True if the fluid side facing {@param facing} is occluded by the block it's contained in, otherwise false
     */
    public boolean isFluidSelfVisible(BlockState selfBlockState, Direction facing, VoxelShape fluidShape) {
        // only perform self-occlusion if the own block state can't occlude
        if (selfBlockState.canOcclude()) {
            var selfShape = selfBlockState.getFaceOcclusionShape(facing);

            // only a non-empty self-shape can occlude anything
            if (!ShapeComparisonCache.isEmptyShape(selfShape)) {
                // a full self-shape occludes everything
                if (ShapeComparisonCache.isFullShape(selfShape) && ShapeComparisonCache.isFullShape(fluidShape)) {
                    return false;
                }

                // perform occlusion of the fluid by the block it's contained in
                return this.occlusionCache.lookup(fluidShape, selfShape);
            }
        }

        return true;
    }

    private boolean isFullBlockFluidSelfVisible(BlockState blockState, Direction dir) {
        return this.isFluidSelfVisible(blockState, dir, Shapes.block());
    }

    /**
     * Checks if a face of a fluid block with a specific height should be rendered based on the neighboring block state.
     *
     * @param world       The block view for this render context
     * @param neighborPos The position of the neighboring block
     * @param facing      The facing direction of the side to check
     * @param height      The height of the fluid
     * @return True if the fluid side facing {@param facing} is not occluded, otherwise false
     */
    public boolean isFluidSideExposed(BlockAndTintGetter world, BlockPos neighborPos, Direction facing, float height) {
        var neighborBlockState = world.getBlockState(neighborPos);

        // zero-height fluids don't render anything anyway
        if (height <= 0.0F) {
            return false;
        }

        // only perform occlusion against blocks that can potentially occlude
        if (!neighborBlockState.canOcclude()) {
            return true;
        }

        // if it's an up-fluid and the height is not 1, it can't be occluded
        if (facing == Direction.UP && height < 1.0F) {
            return true;
        }

        VoxelShape neighborShape = neighborBlockState.getFaceOcclusionShape(DirectionUtil.getOpposite(facing));

        // empty neighbor occlusion shape can't occlude anything
        if (ShapeComparisonCache.isEmptyShape(neighborShape)) {
            return true;
        }

        // full neighbor occlusion shape occludes everything
        if (ShapeComparisonCache.isFullShape(neighborShape)) {
            return false;
        }

        VoxelShape fluidShape;
        if (height >= 1.0F) {
            fluidShape = Shapes.block();
        } else {
            fluidShape = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, height, 1.0D);
        }

        return this.occlusionCache.lookup(fluidShape, neighborShape);
    }

    private boolean isSideExposedOffset(BlockAndTintGetter world, BlockPos originPos, Direction dir, float height) {
        return this.isFluidSideExposed(world, this.scratchPos.setWithOffset(originPos, dir), dir, height);
    }

    private boolean isFullBlockFluidVisible(BlockAndTintGetter world, BlockPos pos, Direction dir, BlockState blockState, FluidState fluid) {
        return isFullBlockFluidSelfVisible(blockState, dir) && this.isFullBlockFluidSideVisible(world, pos, dir, fluid);
    }

    private float fluidHeight(BlockAndTintGetter world, Fluid fluid, BlockPos blockPos) {
        BlockState blockState = world.getBlockState(blockPos);
        FluidState fluidState = blockState.getFluidState();

        if (fluid.isSame(fluidState.getType())) {
            FluidState fluidStateUp = world.getFluidState(blockPos.above());

            if (fluid.isSame(fluidStateUp.getType())) {
                return 1.0f;
            } else {
                return fluidState.getOwnHeight();
            }
        }

        // NOTE: returning 0 here makes shallow water shallower and bends water surfaces towards non-occluding blocks like glass or non-waterlogged stairs
        return DISCARD_SAMPLE;
    }

    private float fluidHeightDiscardOccluded(BlockAndTintGetter world, Fluid fluid, BlockPos origin, Direction offset) {
        return this.fluidHeight(world, fluid, this.scratchPos.setWithOffset(origin, offset));
    }

    private void addHeightSample(float sample) {
        if (sample >= 0.8f) {
            this.scratchHeight += sample * 10.0f;
            this.scratchSamples += 10;
        } else if (sample >= 0.0f) {
            this.scratchHeight += sample;
            this.scratchSamples++;
        }


        // else -> sample == DISCARD_SAMPLE
    }

    private float fluidCornerHeight(BlockAndTintGetter world, BlockPos origin, Fluid fluid, float fluidHeight, Direction dirA, Direction dirB, float fluidHeightA, float fluidHeightB, boolean exposedA, boolean exposedB) {
        // if both sides are full height fluids, the corner is full height too
        float filteredHeightA = exposedA ? fluidHeightA : DISCARD_SAMPLE;
        float filteredHeightB = exposedB ? fluidHeightB : DISCARD_SAMPLE;
        if (filteredHeightA >= 1.0f || filteredHeightB >= 1.0f) {
            return 1.0f;
        }

        //  "D" stands for diagonal
        boolean exposedADB = false;

        // if there is any fluid on either side, check if the diagonal has any
        if (filteredHeightA > 0.0f || filteredHeightB > 0.0f) {
            // check that there's an accessible path to the diagonal
            var aNeighbor = this.scratchPos.setWithOffset(origin, dirA);
            var exposedAD = this.isFullBlockFluidSelfVisible(world.getBlockState(aNeighbor), dirB) &&
                    this.isSideExposedOffset(world, aNeighbor, dirB, 1.0f);
            var bNeighbor = this.scratchPos.setWithOffset(origin, dirB);
            var exposedBD = this.isFullBlockFluidSelfVisible(world.getBlockState(bNeighbor), dirA) &&
                    this.isSideExposedOffset(world, bNeighbor, dirA, 1.0f);

            exposedADB = exposedAD && exposedBD;
            if (exposedA && exposedAD || exposedB && exposedBD) {
                // add a sample using diagonal block's fluid height
                var abNeighbor = this.scratchPos.set(origin).move(dirA).move(dirB);
                float height = this.fluidHeight(world, fluid, abNeighbor);

                if (height >= 1.0f) {
                    return 1.0f;
                }

                this.addHeightSample(height);
            }
        }

        this.addHeightSample(fluidHeight);

        // add samples for the sides if they're exposed or if there's a path through the diagonal to them
        if (exposedB || exposedA && exposedADB) {
            this.addHeightSample(fluidHeightB);
        }
        if (exposedA || exposedB && exposedADB) {
            this.addHeightSample(fluidHeightA);
        }

        // gather the samples and reset
        float result = this.scratchHeight / this.scratchSamples;
        if (result < FULL_HEIGHT) {
            result -= (FULL_HEIGHT - result) * FLATTENING_FACTOR;
        }
        this.scratchHeight = 0.0f;
        this.scratchSamples = 0;

        return result;
    }

    public void render(LevelSlice level, BlockState blockState, FluidState fluidState, BlockPos blockPos, BlockPos offset, TranslucentGeometryCollector collector, ChunkModelBuilder meshBuilder, Material material, ColorProvider<FluidState> colorProvider, TextureAtlasSprite[] sprites) {
        Fluid fluid = fluidState.getType();

        boolean upVisible = this.isFullBlockFluidVisible(level, blockPos, Direction.UP, blockState, fluidState);
        boolean downVisible = this.isFullBlockFluidVisible(level, blockPos, Direction.DOWN, blockState, fluidState) &&
                this.isSideExposedOffset(level, blockPos, Direction.DOWN, FULL_HEIGHT);

        // TODO: disentangle why there are so many checks here. Can we just combine everything into one set of "visible/exposed" flags? Why does it seem to break when I do that, is it necessary to have self-visibility separate?
        boolean northSelfVisible = this.isFullBlockFluidSelfVisible(blockState, Direction.NORTH);
        boolean southSelfVisible = this.isFullBlockFluidSelfVisible(blockState, Direction.SOUTH);
        boolean westSelfVisible = this.isFullBlockFluidSelfVisible(blockState, Direction.WEST);
        boolean eastSelfVisible = this.isFullBlockFluidSelfVisible(blockState, Direction.EAST);
        boolean northVisible = northSelfVisible && this.isFullBlockFluidVisible(level, blockPos, Direction.NORTH, blockState, fluidState);
        boolean southVisible = southSelfVisible && this.isFullBlockFluidVisible(level, blockPos, Direction.SOUTH, blockState, fluidState);
        boolean westVisible = westSelfVisible && this.isFullBlockFluidVisible(level, blockPos, Direction.WEST, blockState, fluidState);
        boolean eastVisible = eastSelfVisible && this.isFullBlockFluidVisible(level, blockPos, Direction.EAST, blockState, fluidState);

        // stop rendering if all faces of the fluid are occluded
        if (!upVisible && !downVisible && !eastVisible && !westVisible && !northVisible && !southVisible) {
            return;
        }

        boolean isWater = fluidState.is(FluidTags.WATER);

        float fluidHeight = this.fluidHeight(level, fluid, blockPos);
        boolean fullFluidBlock = fluidHeight >= 1.0f;
        float northWestHeight, southWestHeight, southEastHeight, northEastHeight;
        if (fullFluidBlock) {
            northWestHeight = 1.0f;
            southWestHeight = 1.0f;
            southEastHeight = 1.0f;
            northEastHeight = 1.0f;
        } else {
            boolean northExposed = northSelfVisible && this.isSideExposedOffset(level, blockPos, Direction.NORTH, 1.0f);
            boolean southExposed = southSelfVisible && this.isSideExposedOffset(level, blockPos, Direction.SOUTH, 1.0f);
            boolean westExposed = westSelfVisible && this.isSideExposedOffset(level, blockPos, Direction.WEST, 1.0f);
            boolean eastExposed = eastSelfVisible && this.isSideExposedOffset(level, blockPos, Direction.EAST, 1.0f);
            float heightNorth = this.fluidHeightDiscardOccluded(level, fluid, blockPos, Direction.NORTH);
            float heightSouth = this.fluidHeightDiscardOccluded(level, fluid, blockPos, Direction.SOUTH);
            float heightEast = this.fluidHeightDiscardOccluded(level, fluid, blockPos, Direction.EAST);
            float heightWest = this.fluidHeightDiscardOccluded(level, fluid, blockPos, Direction.WEST);

            northWestHeight = this.fluidCornerHeight(level, blockPos, fluid, fluidHeight, Direction.NORTH, Direction.WEST, heightNorth, heightWest, northExposed, westExposed);
            southWestHeight = this.fluidCornerHeight(level, blockPos, fluid, fluidHeight, Direction.SOUTH, Direction.WEST, heightSouth, heightWest, southExposed, westExposed);
            southEastHeight = this.fluidCornerHeight(level, blockPos, fluid, fluidHeight, Direction.SOUTH, Direction.EAST, heightSouth, heightEast, southExposed, eastExposed);
            northEastHeight = this.fluidCornerHeight(level, blockPos, fluid, fluidHeight, Direction.NORTH, Direction.EAST, heightNorth, heightEast, northExposed, eastExposed);
        }
        float yOffset = !downVisible ? 0.0F : EPSILON;

        final ModelQuadViewMutable quad = this.quad;

        LightMode lightMode = isWater && Minecraft.useAmbientOcclusion() ? LightMode.SMOOTH : LightMode.FLAT;
        LightPipeline lighter = this.lighters.getLighter(lightMode);

        quad.setFlags(0);

        // calculate up fluid face visibility
        if (upVisible) {
            float totalMinHeight = Math.min(Math.min(northWestHeight, southWestHeight), Math.min(southEastHeight, northEastHeight));
            upVisible = this.isSideExposedOffset(level, blockPos, Direction.UP, totalMinHeight);
        }

        // apply heuristic to not render inner up face and outer up face if there's solid or same-fluid blocks around it
        boolean innerUpFaceVisible = true;
        if (upVisible) {
            innerUpFaceVisible = isUpFaceExposedByNeighbors(level, blockPos, fluid, 1, 1, -1) ||
                    isUpFaceExposedByNeighbors(level, blockPos, fluid, 0, 1, 0);
            upVisible = innerUpFaceVisible || isUpFaceExposedByNeighbors(level, blockPos, fluid, 1, 2, 1);
        }

        if (upVisible) {
            northWestHeight -= EPSILON;
            southWestHeight -= EPSILON;
            southEastHeight -= EPSILON;
            northEastHeight -= EPSILON;

            Vec3 velocity = fluidState.getFlow(level, blockPos);

            TextureAtlasSprite sprite;
            float u1, u2, u3, u4;
            float v1, v2, v3, v4;

            if (velocity.x == 0.0D && velocity.z == 0.0D) {
                sprite = sprites[0];
                u1 = sprite.getU(0.0f);
                v1 = sprite.getV(0.0f);
                u2 = u1;
                v2 = sprite.getV(1.0f);
                u3 = sprite.getU(1.0f);
                v3 = v2;
                u4 = u3;
                v4 = v1;
            } else {
                sprite = sprites[1];
                float dir = (float) Mth.atan2(velocity.z, velocity.x) - (1.5707964f);
                float sin = Mth.sin(dir) * 0.25F;
                float cos = Mth.cos(dir) * 0.25F;
                u1 = sprite.getU(0.5F + (-cos - sin));
                v1 = sprite.getV(0.5F + -cos + sin);
                u2 = sprite.getU(0.5F + -cos + sin);
                v2 = sprite.getV(0.5F + cos + sin);
                u3 = sprite.getU(0.5F + cos + sin);
                v3 = sprite.getV(0.5F + (cos - sin));
                u4 = sprite.getU(0.5F + (cos - sin));
                v4 = sprite.getV(0.5F + (-cos - sin));
            }

            float uAvg = (u1 + u2 + u3 + u4) / 4.0F;
            float vAvg = (v1 + v2 + v3 + v4) / 4.0F;
            float s3 = sprites[0].uvShrinkRatio();

            u1 = Mth.lerp(s3, u1, uAvg);
            u2 = Mth.lerp(s3, u2, uAvg);
            u3 = Mth.lerp(s3, u3, uAvg);
            u4 = Mth.lerp(s3, u4, uAvg);
            v1 = Mth.lerp(s3, v1, vAvg);
            v2 = Mth.lerp(s3, v2, vAvg);
            v3 = Mth.lerp(s3, v3, vAvg);
            v4 = Mth.lerp(s3, v4, vAvg);

            quad.setSprite(sprite);

            // top surface alignedness is calculated with a more relaxed epsilon
            boolean aligned = isAlignedEquals(northEastHeight, northWestHeight)
                    && isAlignedEquals(northWestHeight, southEastHeight)
                    && isAlignedEquals(southEastHeight, southWestHeight)
                    && isAlignedEquals(southWestHeight, northEastHeight);

            boolean creaseNorthEastSouthWest = aligned
                    || northEastHeight > northWestHeight && northEastHeight > southEastHeight
                    || northEastHeight < northWestHeight && northEastHeight < southEastHeight
                    || southWestHeight > northWestHeight && southWestHeight > southEastHeight
                    || southWestHeight < northWestHeight && southWestHeight < southEastHeight;

            if (creaseNorthEastSouthWest) {
                setVertex(quad, 1, 0.0f, northWestHeight, 0.0f, u1, v1);
                setVertex(quad, 2, 0.0f, southWestHeight, 1.0F, u2, v2);
                setVertex(quad, 3, 1.0F, southEastHeight, 1.0F, u3, v3);
                setVertex(quad, 0, 1.0F, northEastHeight, 0.0f, u4, v4);
            } else {
                setVertex(quad, 0, 0.0f, northWestHeight, 0.0f, u1, v1);
                setVertex(quad, 1, 0.0f, southWestHeight, 1.0F, u2, v2);
                setVertex(quad, 2, 1.0F, southEastHeight, 1.0F, u3, v3);
                setVertex(quad, 3, 1.0F, northEastHeight, 0.0f, u4, v4);
            }

            this.updateQuad(quad, level, blockPos, lighter, Direction.UP, ModelQuadFacing.POS_Y, 1.0F, colorProvider, fluidState);
            this.writeQuad(meshBuilder, collector, material, offset, quad, aligned ? ModelQuadFacing.POS_Y : ModelQuadFacing.UNASSIGNED, false);

            if (innerUpFaceVisible) {
                this.writeQuad(meshBuilder, collector, material, offset, quad,
                        aligned ? ModelQuadFacing.NEG_Y : ModelQuadFacing.UNASSIGNED, true);
            }
        }

        if (downVisible) {
            TextureAtlasSprite sprite = sprites[0];

            float minU = sprite.getU0();
            float maxU = sprite.getU1();
            float minV = sprite.getV0();
            float maxV = sprite.getV1();
            quad.setSprite(sprite);

            setVertex(quad, 0, 0.0f, yOffset, 1.0F, minU, maxV);
            setVertex(quad, 1, 0.0f, yOffset, 0.0f, minU, minV);
            setVertex(quad, 2, 1.0F, yOffset, 0.0f, maxU, minV);
            setVertex(quad, 3, 1.0F, yOffset, 1.0F, maxU, maxV);

            this.updateQuad(quad, level, blockPos, lighter, Direction.DOWN, ModelQuadFacing.NEG_Y, 1.0F, colorProvider, fluidState);
            this.writeQuad(meshBuilder, collector, material, offset, quad, ModelQuadFacing.NEG_Y, false);
        }

        quad.setFlags(ModelQuadFlags.IS_PARALLEL | ModelQuadFlags.IS_ALIGNED);

        for (Direction dir : DirectionUtil.HORIZONTAL_DIRECTIONS) {
            float c1;
            float c2;
            float x1;
            float z1;
            float x2;
            float z2;

            switch (dir) {
                case NORTH -> {
                    if (!northVisible) {
                        continue;
                    }
                    c1 = northWestHeight;
                    c2 = northEastHeight;
                    x1 = 0.0f;
                    x2 = 1.0F;
                    z1 = EPSILON;
                    z2 = z1;
                }
                case SOUTH -> {
                    if (!southVisible) {
                        continue;
                    }
                    c1 = southEastHeight;
                    c2 = southWestHeight;
                    x1 = 1.0F;
                    x2 = 0.0f;
                    z1 = 1.0f - EPSILON;
                    z2 = z1;
                }
                case WEST -> {
                    if (!westVisible) {
                        continue;
                    }
                    c1 = southWestHeight;
                    c2 = northWestHeight;
                    x1 = EPSILON;
                    x2 = x1;
                    z1 = 1.0F;
                    z2 = 0.0f;
                }
                case EAST -> {
                    if (!eastVisible) {
                        continue;
                    }
                    c1 = northEastHeight;
                    c2 = southEastHeight;
                    x1 = 1.0f - EPSILON;
                    x2 = x1;
                    z1 = 0.0f;
                    z2 = 1.0F;
                }
                default -> {
                    continue;
                }
            }

            var sideFluidHeight = Math.max(c1, c2);
            this.scratchPos.setWithOffset(blockPos, dir);

            if (this.isFluidSideExposed(level, this.scratchPos, dir, sideFluidHeight)) {
                int adjX = this.scratchPos.getX();
                int adjY = this.scratchPos.getY();
                int adjZ = this.scratchPos.getZ();

                TextureAtlasSprite sprite = sprites[1];

                boolean isOverlay = false;

                if (sprites.length > 2 && sprites[2] != null) {
                    BlockPos adjPos = this.scratchPos.set(adjX, adjY, adjZ);
                    BlockState adjBlock = level.getBlockState(adjPos);

                    if (PlatformBlockAccess.getInstance().shouldShowFluidOverlay(adjBlock, level, adjPos, fluidState)) {
                        sprite = sprites[2];
                        isOverlay = true;
                    }
                }

                float u1 = sprite.getU(0.0F);
                float u2 = sprite.getU(0.5F);
                float v1 = sprite.getV((1.0F - c1) * 0.5F);
                float v2 = sprite.getV((1.0F - c2) * 0.5F);
                float v3 = sprite.getV(0.5F);

                quad.setSprite(sprite);

                setVertex(quad, 0, x2, c2, z2, u2, v2);
                setVertex(quad, 1, x2, yOffset, z2, u2, v3);
                setVertex(quad, 2, x1, yOffset, z1, u1, v3);
                setVertex(quad, 3, x1, c1, z1, u1, v1);

                float br = dir.getAxis() == Direction.Axis.Z ? 0.8F : 0.6F;

                ModelQuadFacing facing = ModelQuadFacing.fromDirection(dir);

                this.updateQuad(quad, level, blockPos, lighter, dir, facing, br, colorProvider, fluidState);
                this.writeQuad(meshBuilder, collector, material, offset, quad, facing, false);

                if (!isOverlay) {
                    this.writeQuad(meshBuilder, collector, material, offset, quad, facing.getOpposite(), true);
                }
            }
        }
    }

    private boolean isUpFaceExposedByNeighbors(LevelSlice level, BlockPos blockPos, Fluid fluid, int yOffset, int range, int skipRange) {
        for (int i = -range; i <= range; ++i) {
            for (int j = -range; j <= range; ++j) {
                if (skipRange >= 0 && i <= skipRange && i >= -skipRange && j <= skipRange && j >= -skipRange) {
                    continue;
                }

                // the face is visible if any of the blocks
                BlockPos blockPos2 = this.scratchPos.setWithOffset(blockPos, i, yOffset, j);
                if (!level.getFluidState(blockPos2).getType().isSame(fluid) && !level.getBlockState(blockPos2).isSolidRender()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isAlignedEquals(float a, float b) {
        return Math.abs(a - b) <= ALIGNED_EQUALS_EPSILON;
    }

    private void updateQuad(ModelQuadViewMutable quad, LevelSlice level, BlockPos pos, LightPipeline lighter, Direction dir, ModelQuadFacing facing, float brightness,
                            ColorProvider<FluidState> colorProvider, FluidState fluidState) {

        int normal;
        if (facing.isAligned()) {
            normal = facing.getPackedAlignedNormal();
        } else {
            normal = quad.calculateNormal();
        }

        quad.setFaceNormal(normal);

        QuadLightData light = this.quadLightData;

        lighter.calculate(quad, pos, light, null, dir, false, false);

        colorProvider.getColors(level, pos, this.scratchPos, fluidState, quad, this.quadColors);

        // multiply the per-vertex color against the combined brightness
        // the combined brightness is the per-vertex brightness multiplied by the block's brightness
        for (int i = 0; i < 4; i++) {
            this.quadColors[i] = ColorARGB.toABGR(this.quadColors[i]);
            this.brightness[i] = light.br[i] * brightness;
        }
    }

    private void writeQuad(ChunkModelBuilder builder, TranslucentGeometryCollector collector, Material material, BlockPos offset, ModelQuadView quad,
                           ModelQuadFacing facing, boolean flip) {
        var vertices = this.vertices;

        for (int i = 0; i < 4; i++) {
            var out = vertices[flip ? (3 - i + 1) & 0b11 : i];
            out.x = offset.getX() + quad.getX(i);
            out.y = offset.getY() + quad.getY(i);
            out.z = offset.getZ() + quad.getZ(i);

            out.color = this.quadColors[i];
            out.ao = this.brightness[i];
            out.u = quad.getTexU(i);
            out.v = quad.getTexV(i);
            out.light = this.quadLightData.lm[i];
        }

        TextureAtlasSprite sprite = quad.getSprite();

        if (sprite != null) {
            builder.addSprite(sprite);
        }

        if (material.isTranslucent() && collector != null) {
            int normal;

            if (facing.isAligned()) {
                normal = facing.getPackedAlignedNormal();
            } else {
                // This was updated earlier in updateQuad. There is no situation where the normal vector should have changed.
                normal = quad.getFaceNormal();
            }

            if (flip) {
                normal = NormI8.flipPacked(normal);
            }

            collector.appendQuad(normal, vertices, facing);
        }

        var vertexBuffer = builder.getVertexBuffer(facing);
        vertexBuffer.push(vertices, material);
    }

    private static void setVertex(ModelQuadViewMutable quad, int i, float x, float y, float z, float u, float v) {
        quad.setX(i, x);
        quad.setY(i, y);
        quad.setZ(i, z);
        quad.setTexU(i, u);
        quad.setTexV(i, v);
    }
}

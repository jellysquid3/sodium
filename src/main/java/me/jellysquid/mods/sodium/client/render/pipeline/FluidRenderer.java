package me.jellysquid.mods.sodium.client.render.pipeline;

import me.jellysquid.mods.sodium.client.model.light.LightMode;
import me.jellysquid.mods.sodium.client.model.light.LightPipeline;
import me.jellysquid.mods.sodium.client.model.light.LightPipelineProvider;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuad;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadColorProvider;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadViewMutable;
import me.jellysquid.mods.sodium.client.model.quad.blender.BiomeColorBlender;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFlags;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadWinding;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import me.jellysquid.mods.sodium.client.util.Norm3b;
import me.jellysquid.mods.sodium.client.util.color.ColorABGR;
import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.impl.client.rendering.fluid.FluidRenderHandlerRegistryImpl;
import net.minecraft.block.BlockState;
import net.minecraft.block.SideShapeType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.texture.Sprite;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

public class FluidRenderer {
    // TODO: allow this to be changed by vertex format
    // TODO: move fluid rendering to a separate render pass and control glPolygonOffset and glDepthFunc to fix this properly
    private static final float EPSILON = 0.001f;

    private final BlockPos.Mutable scratchPos = new BlockPos.Mutable();

    private final Sprite waterOverlaySprite;

    private final ModelQuadViewMutable quad = new ModelQuad();

    private final LightPipelineProvider lighters;
    private final BiomeColorBlender biomeColorBlender;

    // Cached wrapper type that adapts FluidRenderHandler to support QuadColorProvider<FluidState>
    private final FabricFluidColorizerAdapter fabricColorProviderAdapter = new FabricFluidColorizerAdapter();

    private final QuadLightData quadLightData = new QuadLightData();
    private final int[] quadColors = new int[4];

    public FluidRenderer(LightPipelineProvider lighters, BiomeColorBlender biomeColorBlender) {
        this.waterOverlaySprite = ModelLoader.WATER_OVERLAY.getSprite();

        int normal = Norm3b.pack(0.0f, 1.0f, 0.0f);

        for (int i = 0; i < 4; i++) {
            this.quad.setNormal(i, normal);
        }

        this.lighters = lighters;
        this.biomeColorBlender = biomeColorBlender;
    }

    private boolean isFluidOccluded(BlockRenderView world, int x, int y, int z, Direction dir, Fluid fluid) {
        BlockPos pos = this.scratchPos.set(x, y, z);
        BlockState blockState = world.getBlockState(pos);
        BlockPos adjPos = this.scratchPos.set(x + dir.getOffsetX(), y + dir.getOffsetY(), z + dir.getOffsetZ());

        if (blockState.isOpaque()) {
            return world.getFluidState(adjPos).getFluid().matchesType(fluid) || blockState.isSideSolid(world,pos,dir, SideShapeType.FULL);
            // fluidlogged or next to water, occlude sides that are solid or the same liquid
            }
        return world.getFluidState(adjPos).getFluid().matchesType(fluid);
    }

    private boolean isSideExposed(BlockRenderView world, int x, int y, int z, Direction dir, float height) {
        BlockPos pos = this.scratchPos.set(x + dir.getOffsetX(), y + dir.getOffsetY(), z + dir.getOffsetZ());
        BlockState blockState = world.getBlockState(pos);

        if (blockState.isOpaque()) {
            VoxelShape shape = blockState.getCullingShape(world, pos);

            // Hoist these checks to avoid allocating the shape below
            if (shape == VoxelShapes.fullCube()) {
                // The top face always be inset, so if the shape above is a full cube it can't possibly occlude
                return dir == Direction.UP;
            } else if (shape.isEmpty()) {
                return true;
            }

            VoxelShape threshold = VoxelShapes.cuboid(0.0D, 0.0D, 0.0D, 1.0D, height, 1.0D);

            return !VoxelShapes.isSideCovered(threshold, shape, dir);
        }

        return true;
    }

    public boolean render(BlockRenderView world, FluidState fluidState, BlockPos pos, BlockPos offset, ChunkModelBuilder buffers) {
        int posX = pos.getX();
        int posY = pos.getY();
        int posZ = pos.getZ();

        Fluid fluid = fluidState.getFluid();

        boolean sfUp = this.isFluidOccluded(world, posX, posY, posZ, Direction.UP, fluid);
        boolean sfDown = this.isFluidOccluded(world, posX, posY, posZ, Direction.DOWN, fluid) ||
                !this.isSideExposed(world, posX, posY, posZ, Direction.DOWN, 0.8888889F);
        boolean sfNorth = this.isFluidOccluded(world, posX, posY, posZ, Direction.NORTH, fluid);
        boolean sfSouth = this.isFluidOccluded(world, posX, posY, posZ, Direction.SOUTH, fluid);
        boolean sfWest = this.isFluidOccluded(world, posX, posY, posZ, Direction.WEST, fluid);
        boolean sfEast = this.isFluidOccluded(world, posX, posY, posZ, Direction.EAST, fluid);

        if (sfUp && sfDown && sfEast && sfWest && sfNorth && sfSouth) {
            return false;
        }

        boolean isWater = fluidState.isIn(FluidTags.WATER);

        FluidRenderHandler handler = FluidRenderHandlerRegistryImpl.INSTANCE.getOverride(fluidState.getFluid());
        ModelQuadColorProvider<FluidState> colorizer = this.createColorProviderAdapter(handler);

        Sprite[] sprites = handler.getFluidSprites(world, pos, fluidState);

        boolean rendered = false;

        float h1 = this.getCornerHeight(world, posX, posY, posZ, fluidState.getFluid());
        float h2 = this.getCornerHeight(world, posX, posY, posZ + 1, fluidState.getFluid());
        float h3 = this.getCornerHeight(world, posX + 1, posY, posZ + 1, fluidState.getFluid());
        float h4 = this.getCornerHeight(world, posX + 1, posY, posZ, fluidState.getFluid());

        float yOffset = sfDown ? 0.0F : EPSILON;

        final ModelQuadViewMutable quad = this.quad;

        LightMode lightMode = isWater && MinecraftClient.isAmbientOcclusionEnabled() ? LightMode.SMOOTH : LightMode.FLAT;
        LightPipeline lighter = this.lighters.getLighter(lightMode);

        quad.setFlags(0);

        if (!sfUp && this.isSideExposed(world, posX, posY, posZ, Direction.UP, Math.min(Math.min(h1, h2), Math.min(h3, h4)))) {
            h1 -= EPSILON;
            h2 -= EPSILON;
            h3 -= EPSILON;
            h4 -= EPSILON;

            Vec3d velocity = fluidState.getVelocity(world, pos);

            Sprite sprite;
            ModelQuadFacing facing;
            float u1, u2, u3, u4;
            float v1, v2, v3, v4;

            if (velocity.x == 0.0D && velocity.z == 0.0D) {
                sprite = sprites[0];
                facing = ModelQuadFacing.UP;
                u1 = sprite.getFrameU(0.0D);
                v1 = sprite.getFrameV(0.0D);
                u2 = u1;
                v2 = sprite.getFrameV(16.0D);
                u3 = sprite.getFrameU(16.0D);
                v3 = v2;
                u4 = u3;
                v4 = v1;
            } else {
                sprite = sprites[1];
                facing = ModelQuadFacing.UNASSIGNED;
                float dir = (float) MathHelper.atan2(velocity.z, velocity.x) - (1.5707964f);
                float sin = MathHelper.sin(dir) * 0.25F;
                float cos = MathHelper.cos(dir) * 0.25F;
                u1 = sprite.getFrameU(8.0F + (-cos - sin) * 16.0F);
                v1 = sprite.getFrameV(8.0F + (-cos + sin) * 16.0F);
                u2 = sprite.getFrameU(8.0F + (-cos + sin) * 16.0F);
                v2 = sprite.getFrameV(8.0F + (cos + sin) * 16.0F);
                u3 = sprite.getFrameU(8.0F + (cos + sin) * 16.0F);
                v3 = sprite.getFrameV(8.0F + (cos - sin) * 16.0F);
                u4 = sprite.getFrameU(8.0F + (cos - sin) * 16.0F);
                v4 = sprite.getFrameV(8.0F + (-cos - sin) * 16.0F);
            }

            float uAvg = (u1 + u2 + u3 + u4) / 4.0F;
            float vAvg = (v1 + v2 + v3 + v4) / 4.0F;
            float s1 = (float) sprites[0].getWidth() / (sprites[0].getMaxU() - sprites[0].getMinU());
            float s2 = (float) sprites[0].getHeight() / (sprites[0].getMaxV() - sprites[0].getMinV());
            float s3 = 4.0F / Math.max(s2, s1);

            u1 = MathHelper.lerp(s3, u1, uAvg);
            u2 = MathHelper.lerp(s3, u2, uAvg);
            u3 = MathHelper.lerp(s3, u3, uAvg);
            u4 = MathHelper.lerp(s3, u4, uAvg);
            v1 = MathHelper.lerp(s3, v1, vAvg);
            v2 = MathHelper.lerp(s3, v2, vAvg);
            v3 = MathHelper.lerp(s3, v3, vAvg);
            v4 = MathHelper.lerp(s3, v4, vAvg);

            quad.setSprite(sprite);

            this.setVertex(quad, 0, 0.0f, h1, 0.0f, u1, v1);
            this.setVertex(quad, 1, 0.0f, h2, 1.0F, u2, v2);
            this.setVertex(quad, 2, 1.0F, h3, 1.0F, u3, v3);
            this.setVertex(quad, 3, 1.0F, h4, 0.0f, u4, v4);

            this.calculateQuadColors(quad, world, pos, lighter, Direction.UP, 1.0F, colorizer, fluidState);

            int vertexStart = this.writeVertices(buffers, offset, quad);

            buffers.getIndexBufferBuilder(facing)
                    .add(vertexStart, ModelQuadWinding.CLOCKWISE);

            if (fluidState.method_15756(world, this.scratchPos.set(posX, posY + 1, posZ))) {
                buffers.getIndexBufferBuilder(ModelQuadFacing.DOWN)
                        .add(vertexStart, ModelQuadWinding.COUNTERCLOCKWISE);
            }

            rendered = true;
        }

        if (!sfDown) {
            Sprite sprite = sprites[0];

            float minU = sprite.getMinU();
            float maxU = sprite.getMaxU();
            float minV = sprite.getMinV();
            float maxV = sprite.getMaxV();
            quad.setSprite(sprite);

            this.setVertex(quad, 0, 0.0f, yOffset, 1.0F, minU, maxV);
            this.setVertex(quad, 1, 0.0f, yOffset, 0.0f, minU, minV);
            this.setVertex(quad, 2, 1.0F, yOffset, 0.0f, maxU, minV);
            this.setVertex(quad, 3, 1.0F, yOffset, 1.0F, maxU, maxV);

            this.calculateQuadColors(quad, world, pos, lighter, Direction.DOWN, 1.0F, colorizer, fluidState);

            int vertexStart = this.writeVertices(buffers, offset, quad);

            buffers.getIndexBufferBuilder(ModelQuadFacing.DOWN)
                    .add(vertexStart, ModelQuadWinding.CLOCKWISE);

            rendered = true;
        }

        this.quad.setFlags(ModelQuadFlags.IS_ALIGNED);

        for (Direction dir : DirectionUtil.HORIZONTAL_DIRECTIONS) {
            float c1;
            float c2;
            float x1;
            float z1;
            float x2;
            float z2;

            switch (dir) {
                case NORTH:
                    if (sfNorth) {
                        continue;
                    }

                    c1 = h1;
                    c2 = h4;
                    x1 = 0.0f;
                    x2 = 1.0F;
                    z1 = EPSILON;
                    z2 = z1;
                    break;
                case SOUTH:
                    if (sfSouth) {
                        continue;
                    }

                    c1 = h3;
                    c2 = h2;
                    x1 = 1.0F;
                    x2 = 0.0f;
                    z1 = 1.0f - EPSILON;
                    z2 = z1;
                    break;
                case WEST:
                    if (sfWest) {
                        continue;
                    }

                    c1 = h2;
                    c2 = h1;
                    x1 = EPSILON;
                    x2 = x1;
                    z1 = 1.0F;
                    z2 = 0.0f;
                    break;
                case EAST:
                    if (sfEast) {
                        continue;
                    }

                    c1 = h4;
                    c2 = h3;
                    x1 = 1.0f - EPSILON;
                    x2 = x1;
                    z1 = 0.0f;
                    z2 = 1.0F;
                    break;
                default:
                    continue;
            }

            if (this.isSideExposed(world, posX, posY, posZ, dir, Math.max(c1, c2))) {
                int adjX = posX + dir.getOffsetX();
                int adjY = posY + dir.getOffsetY();
                int adjZ = posZ + dir.getOffsetZ();

                Sprite sprite = sprites[1];

                if (isWater) {
                    BlockPos adjPos = this.scratchPos.set(adjX, adjY, adjZ);
                    BlockState adjBlock = world.getBlockState(adjPos);

                    if (!adjBlock.isOpaque() && !adjBlock.isAir()) {
                        // ice, glass, stained glass, tinted glass
                        sprite = this.waterOverlaySprite;

                    }
                }

                float u1 = sprite.getFrameU(0.0D);
                float u2 = sprite.getFrameU(8.0D);
                float v1 = sprite.getFrameV((1.0F - c1) * 16.0F * 0.5F);
                float v2 = sprite.getFrameV((1.0F - c2) * 16.0F * 0.5F);
                float v3 = sprite.getFrameV(8.0D);

                quad.setSprite(sprite);

                this.setVertex(quad, 0, x2, c2, z2, u2, v2);
                this.setVertex(quad, 1, x2, yOffset, z2, u2, v3);
                this.setVertex(quad, 2, x1, yOffset, z1, u1, v3);
                this.setVertex(quad, 3, x1, c1, z1, u1, v1);

                float br = dir.getAxis() == Direction.Axis.Z ? 0.8F : 0.6F;

                ModelQuadFacing facing = ModelQuadFacing.fromDirection(dir);

                this.calculateQuadColors(quad, world, pos, lighter, dir, br, colorizer, fluidState);

                int vertexStart = this.writeVertices(buffers, offset, quad);

                buffers.getIndexBufferBuilder(facing)
                        .add(vertexStart, ModelQuadWinding.CLOCKWISE);

                if (sprite != this.waterOverlaySprite) {
                    buffers.getIndexBufferBuilder(facing.getOpposite())
                            .add(vertexStart, ModelQuadWinding.COUNTERCLOCKWISE);
                }

                rendered = true;
            }
        }

        return rendered;
    }

    private ModelQuadColorProvider<FluidState> createColorProviderAdapter(FluidRenderHandler handler) {
        FabricFluidColorizerAdapter adapter = this.fabricColorProviderAdapter;
        adapter.setHandler(handler);

        return adapter;
    }

    private void calculateQuadColors(ModelQuadView quad, BlockRenderView world, BlockPos pos, LightPipeline lighter, Direction dir, float brightness,
                                     ModelQuadColorProvider<FluidState> handler, FluidState fluidState) {
        QuadLightData light = this.quadLightData;
        lighter.calculate(quad, pos, light, dir, false);

        int[] biomeColors = this.biomeColorBlender.getColors(world, pos, quad, handler, fluidState);

        for (int i = 0; i < 4; i++) {
            this.quadColors[i] = ColorABGR.mul(biomeColors != null ? biomeColors[i] : 0xFFFFFFFF, light.br[i] * brightness);
        }
    }

    private int writeVertices(ChunkModelBuilder builder, BlockPos offset, ModelQuadView quad) {
        ModelVertexSink vertices = builder.getVertexSink();
        vertices.ensureCapacity(4);

        int vertexStart = vertices.getVertexCount();

        for (int i = 0; i < 4; i++) {
            float x = quad.getX(i);
            float y = quad.getY(i);
            float z = quad.getZ(i);

            int color = this.quadColors[i];

            float u = quad.getTexU(i);
            float v = quad.getTexV(i);

            int light = this.quadLightData.lm[i];

            vertices.writeVertex(offset, x, y, z, color, u, v, light, builder.getChunkId());
        }

        vertices.flush();

        Sprite sprite = quad.getSprite();

        if (sprite != null) {
            builder.addSprite(sprite);
        }

        return vertexStart;
    }

    private void setVertex(ModelQuadViewMutable quad, int i, float x, float y, float z, float u, float v) {
        quad.setX(i, x);
        quad.setY(i, y);
        quad.setZ(i, z);
        quad.setTexU(i, u);
        quad.setTexV(i, v);
    }

    private float getCornerHeight(BlockRenderView world, int x, int y, int z, Fluid fluid) {
        int samples = 0;
        float totalHeight = 0.0F;

        for (int i = 0; i < 4; ++i) {
            int x2 = x - (i & 1);
            int z2 = z - (i >> 1 & 1);

            if (world.getFluidState(this.scratchPos.set(x2, y + 1, z2)).getFluid().matchesType(fluid)) {
                return 1.0F;
            }

            BlockPos pos = this.scratchPos.set(x2, y, z2);

            BlockState blockState = world.getBlockState(pos);
            FluidState fluidState = blockState.getFluidState();

            if (fluidState.getFluid().matchesType(fluid)) {
                float height = fluidState.getHeight(world, pos);

                if (height >= 0.8F) {
                    totalHeight += height * 10.0F;
                    samples += 10;
                } else {
                    totalHeight += height;
                    ++samples;
                }
            } else if (!blockState.getMaterial().isSolid()) {
                ++samples;
            }
        }

        return totalHeight / (float) samples;
    }

    private static class FabricFluidColorizerAdapter implements ModelQuadColorProvider<FluidState> {
        private FluidRenderHandler handler;

        public void setHandler(FluidRenderHandler handler) {
            this.handler = handler;
        }

        @Override
        public int getColor(FluidState state, @Nullable BlockRenderView world, @Nullable BlockPos pos, int tintIndex) {
            if (this.handler == null) {
                return -1;
            }

            return this.handler.getFluidColor(world, pos, state);
        }
    }
}

package me.jellysquid.mods.sodium.client.render.pipeline;

import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.light.LightPipeline;
import me.jellysquid.mods.sodium.client.render.light.LightResult;
import me.jellysquid.mods.sodium.client.render.light.flat.FlatLightPipeline;
import me.jellysquid.mods.sodium.client.render.light.smooth.SmoothLightPipeline;
import me.jellysquid.mods.sodium.client.render.model.quad.ModelQuad;
import me.jellysquid.mods.sodium.client.render.model.quad.ModelQuadConsumer;
import me.jellysquid.mods.sodium.client.render.model.quad.ModelQuadFlags;
import me.jellysquid.mods.sodium.client.render.model.quad.ModelQuadViewMutable;
import me.jellysquid.mods.sodium.client.util.ColorUtil;
import me.jellysquid.mods.sodium.client.util.QuadUtil;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModels;
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

public class FluidRenderPipeline {
    private final BlockPos.Mutable scratchPos = new BlockPos.Mutable();

    private final Sprite[] lavaSprites = new Sprite[2];
    private final Sprite[] waterSprites = new Sprite[2];
    private final Sprite waterOverlaySprite;

    private final ModelQuadViewMutable quad = new ModelQuad();

    private final SmoothLightPipeline smoothLightPipeline;
    private final LightPipeline flatLightPipeline;

    private final LightResult lightResult = new LightResult();

    public FluidRenderPipeline(MinecraftClient client, SmoothLightPipeline smoothLightPipeline, FlatLightPipeline flatLightPipeline) {
        BlockModels models = client.getBakedModelManager().getBlockModels();

        this.lavaSprites[0] = models.getModel(Blocks.LAVA.getDefaultState()).getSprite();
        this.lavaSprites[1] = ModelLoader.LAVA_FLOW.getSprite();

        this.waterSprites[0] = models.getModel(Blocks.WATER.getDefaultState()).getSprite();
        this.waterSprites[1] = ModelLoader.WATER_FLOW.getSprite();

        this.waterOverlaySprite = ModelLoader.WATER_OVERLAY.getSprite();

        int normal = QuadUtil.encodeNormal(0.0f, 1.0f, 0.0f);

        for (int i = 0; i < 4; i++) {
            this.quad.setNormal(i, normal);
        }

        this.quad.setFlags(ModelQuadFlags.IS_ALIGNED);

        this.smoothLightPipeline = smoothLightPipeline;
        this.flatLightPipeline = flatLightPipeline;
    }

    private static boolean isSameFluid(WorldSlice world, int x, int y, int z, Fluid fluid) {
        return world.getFluidState(x, y, z).getFluid().matchesType(fluid);
    }

    private static boolean isSideCovered(WorldSlice world, int x, int y, int z, Direction dir, float height) {
        BlockState blockState = world.getBlockState(x + dir.getOffsetX(), y + dir.getOffsetY(), z + dir.getOffsetZ());

        if (blockState.isOpaque()) {
            VoxelShape a = VoxelShapes.cuboid(0.0D, 0.0D, 0.0D, 1.0D, height, 1.0D);
            VoxelShape b = blockState.getCullingShape(world, new BlockPos(x, y, z));

            return VoxelShapes.isSideCovered(a, b, dir);
        }

        return false;
    }

    public boolean render(ChunkRenderData.Builder meshInfo, WorldSlice world, BlockPos pos, VertexConsumer builder, FluidState fluidState) {
        int posX = pos.getX();
        int posY = pos.getY();
        int posZ = pos.getZ();

        Fluid fluid = fluidState.getFluid();

        boolean sfUp = !isSameFluid(world, posX, posY + 1, posZ, fluid);
        boolean sfDown = !isSameFluid(world, posX, posY - 1, posZ, fluid) &&
                !isSideCovered(world, posX, posY, posZ, Direction.DOWN, 0.8888889F);
        boolean sfNorth = !isSameFluid(world, posX, posY, posZ - 1, fluid);
        boolean sfSouth = !isSameFluid(world, posX, posY, posZ + 1, fluid);
        boolean sfWest = !isSameFluid(world, posX - 1, posY, posZ, fluid);
        boolean sfEast = !isSameFluid(world, posX + 1, posY, posZ, fluid);

        if (!sfUp && !sfDown && !sfEast && !sfWest && !sfNorth && !sfSouth) {
            return false;
        }

        boolean lava = fluidState.matches(FluidTags.LAVA);
        Sprite[] sprites = lava ? this.lavaSprites : this.waterSprites;
        int color = lava ? 0xFFFFFF : BiomeColors.getWaterColor(world, pos);

        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;

        boolean rendered = false;

        float h1 = this.getCornerHeight(world, posX, posY, posZ, fluidState.getFluid());
        float h2 = this.getCornerHeight(world, posX, posY, posZ + 1, fluidState.getFluid());
        float h3 = this.getCornerHeight(world, posX + 1, posY, posZ + 1, fluidState.getFluid());
        float h4 = this.getCornerHeight(world, posX + 1, posY, posZ, fluidState.getFluid());

        float x = pos.getX() & 15;
        float y = pos.getY() & 15;
        float z = pos.getZ() & 15;

        float float_13 = sfDown ? 0.001F : 0.0F;

        final ModelQuadViewMutable quad = this.quad;
        final LightResult light = this.lightResult;

        LightPipeline lighter = !lava && MinecraftClient.isAmbientOcclusionEnabled() ? this.smoothLightPipeline : this.flatLightPipeline;
        lighter.reset();

        if (sfUp && !isSideCovered(world, posX, posY, posZ, Direction.UP, Math.min(Math.min(h1, h2), Math.min(h3, h4)))) {
            h1 -= 0.001F;
            h2 -= 0.001F;
            h3 -= 0.001F;
            h4 -= 0.001F;

            Vec3d velocity = fluidState.getVelocity(world, pos);

            float u1, u2, u3, u4;
            float v1, v2, v3, v4;

            if (velocity.x == 0.0D && velocity.z == 0.0D) {
                Sprite sprite = sprites[0];
                u1 = sprite.getFrameU(0.0D);
                v1 = sprite.getFrameV(0.0D);
                u2 = u1;
                v2 = sprite.getFrameV(16.0D);
                u3 = sprite.getFrameU(16.0D);
                v3 = v2;
                u4 = u3;
                v4 = v1;
            } else {
                Sprite sprite = sprites[1];
                float dir = (float) MathHelper.atan2(velocity.z, velocity.x) - ((float) Math.PI / 2F);
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

            this.writeVertex(quad, 0, x, y + h1, z, u1, v1);
            this.writeVertex(quad, 1, x, y + h2, z + 1.0F, u2, v2);
            this.writeVertex(quad, 2, x + 1.0F, y + h3, z + 1.0F, u3, v3);
            this.writeVertex(quad, 3, x + 1.0F, y + h4, z, u4, v4);

            this.applyLighting(quad, pos, lighter, light, Direction.UP);
            this.writeQuad(builder, quad, red, green, blue, false);

            if (fluidState.method_15756(world, this.scratchPos.set(posX, posY + 1, posZ))) {
                this.writeVertex(quad, 3, x, y + h1, z, u1, v1);
                this.writeVertex(quad, 2, x, y + h2, z + 1.0F, u2, v2);
                this.writeVertex(quad, 1, x + 1.0F, y + h3, z + 1.0F, u3, v3);
                this.writeVertex(quad, 0, x + 1.0F, y + h4, z, u4, v4);
                this.writeQuad(builder, quad, red, green, blue, true);
            }

            rendered = true;
        }

        if (sfDown) {
            float minU = sprites[0].getMinU();
            float maxU = sprites[0].getMaxU();
            float minV = sprites[0].getMinV();
            float maxV = sprites[0].getMaxV();

            this.writeVertex(quad, 0, x, y + float_13, z + 1.0F, minU, maxV);
            this.writeVertex(quad, 1, x, y + float_13, z, minU, minV);
            this.writeVertex(quad, 2, x + 1.0F, y + float_13, z, maxU, minV);
            this.writeVertex(quad, 3, x + 1.0F, y + float_13, z + 1.0F, maxU, maxV);

            this.applyLighting(quad, pos, lighter, light, Direction.DOWN);
            this.writeQuad(builder, quad, red, green, blue, true);

            rendered = true;
        }

        for (Direction dir : DirectionUtil.HORIZONTAL_DIRECTIONS) {
            float c1;
            float c2;
            float x1;
            float z1;
            float x2;
            float z2;

            switch (dir) {
                case NORTH:
                    if (!sfNorth) {
                        continue;
                    }

                    c1 = h1;
                    c2 = h4;
                    x1 = x;
                    x2 = x + 1.0F;
                    z1 = z + 0.001F;
                    z2 = z1;
                    break;
                case SOUTH:
                    if (!sfSouth) {
                        continue;
                    }

                    c1 = h3;
                    c2 = h2;
                    x1 = x + 1.0F;
                    x2 = x;
                    z1 = z + 0.999f;
                    z2 = z1;
                    break;
                case WEST:
                    if (!sfWest) {
                        continue;
                    }

                    c1 = h2;
                    c2 = h1;
                    x1 = x + 0.001F;
                    x2 = x1;
                    z1 = z + 1.0F;
                    z2 = z;
                    break;
                case EAST:
                    if (!sfEast) {
                        continue;
                    }

                    c1 = h4;
                    c2 = h3;
                    x1 = x + 0.999f;
                    x2 = x1;
                    z1 = z;
                    z2 = z + 1.0F;
                    break;
                default:
                    continue;
            }

            if (!isSideCovered(world, posX, posY, posZ, dir, Math.max(c1, c2))) {
                int adjX = posX + dir.getOffsetX();
                int adjY = posY + dir.getOffsetY();
                int adjZ = posZ + dir.getOffsetZ();

                Sprite sprite = sprites[1];

                if (!lava) {
                    Block block = world.getBlockState(adjX, adjY, adjZ).getBlock();

                    if (block == Blocks.GLASS || block instanceof StainedGlassBlock) {
                        sprite = this.waterOverlaySprite;
                    }
                }

                float u1 = sprite.getFrameU(0.0D);
                float u2 = sprite.getFrameU(8.0D);
                float v1 = sprite.getFrameV((1.0F - c1) * 16.0F * 0.5F);
                float v2 = sprite.getFrameV((1.0F - c2) * 16.0F * 0.5F);
                float v3 = sprite.getFrameV(8.0D);

                float br = dir.getAxis() == Direction.Axis.Z ? 0.8F : 0.6F;

                float redM = br * red;
                float greenM = br * green;
                float blueM = br * blue;

                this.writeVertex(quad, 0, x2, y + c2, z2, u2, v2);
                this.writeVertex(quad, 1, x2, y + float_13, z2, u2, v3);
                this.writeVertex(quad, 2, x1, y + float_13, z1, u1, v3);
                this.writeVertex(quad, 3, x1, y + c1, z1, u1, v1);

                this.applyLighting(quad, pos, lighter, light, dir);
                this.writeQuad(builder, quad, redM, greenM, blueM, false);

                if (sprite != this.waterOverlaySprite) {
                    this.writeVertex(quad, 0, x1, y + c1, z1, u1, v1);
                    this.writeVertex(quad, 1, x1, y + float_13, z1, u1, v3);
                    this.writeVertex(quad, 2, x2, y + float_13, z2, u2, v3);
                    this.writeVertex(quad, 3, x2, y + c2, z2, u2, v2);

                    this.writeQuad(builder, quad, redM, greenM, blueM, true);
                }

                rendered = true;
            }
        }

        if (rendered) {
            meshInfo.addSprites(sprites);
        }

        return rendered;
    }

    private void applyLighting(ModelQuadViewMutable quad, BlockPos pos, LightPipeline lighter, LightResult light, Direction dir) {
        lighter.apply(quad, pos, light, dir);
    }

    private void writeQuad(VertexConsumer consumer, ModelQuadViewMutable quad, float r, float g, float b, boolean flipLight) {
        LightResult lightResult = this.lightResult;

        int lightIndex, lightOrder;

        if (flipLight) {
            lightIndex = 3;
            lightOrder = -1;
        } else {
            lightIndex = 0;
            lightOrder = 1;
        }

        for (int i = 0; i < 4; i++) {
            float br = lightResult.br[lightIndex];
            int lm = lightResult.lm[lightIndex];

            quad.setColor(i, ColorUtil.encodeRGBA(r * br, g * br, b * br, 1.0f));
            quad.setLight(i, lm);

            lightIndex += lightOrder;
        }

        // TODO: allow for fallback rendering
        ((ModelQuadConsumer) consumer).write(quad);
    }

    private void writeVertex(ModelQuadViewMutable quad, int i, float x, float y, float z, float u, float v) {
        quad.setX(i, x);
        quad.setY(i, y);
        quad.setZ(i, z);
        quad.setTexU(i, u);
        quad.setTexV(i, v);
    }

    private float getCornerHeight(WorldSlice world, int x, int y, int z, Fluid fluid) {
        int samples = 0;
        float totalHeight = 0.0F;

        for (int i = 0; i < 4; ++i) {
            int x2 = x - (i & 1);
            int z2 = z - (i >> 1 & 1);

            if (world.getFluidState(x2, y + 1, z2).getFluid().matchesType(fluid)) {
                return 1.0F;
            }

            BlockState blockState = world.getBlockState(x2, y, z2);
            FluidState fluidState = blockState.getFluidState();

            if (fluidState.getFluid().matchesType(fluid)) {
                float height = fluidState.getHeight(world, this.scratchPos.set(x2, y, z2));

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
}

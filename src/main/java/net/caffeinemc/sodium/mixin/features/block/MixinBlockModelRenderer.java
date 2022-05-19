package net.caffeinemc.sodium.mixin.features.block;

import net.caffeinemc.sodium.interop.vanilla.vertex.VanillaVertexFormats;
import net.caffeinemc.sodium.interop.vanilla.vertex.formats.quad.QuadVertexSink;
import net.caffeinemc.sodium.render.terrain.quad.ModelQuadUtil;
import net.caffeinemc.sodium.render.terrain.quad.ModelQuadView;
import net.caffeinemc.sodium.render.texture.SpriteUtil;
import net.caffeinemc.sodium.render.vertex.VertexDrain;
import net.caffeinemc.sodium.util.DirectionUtil;
import net.caffeinemc.sodium.util.packed.ColorABGR;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.AbstractRandom;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.gen.random.Xoroshiro128PlusPlusRandom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BlockModelRenderer.class)
public class MixinBlockModelRenderer {
    private final Xoroshiro128PlusPlusRandom random = new Xoroshiro128PlusPlusRandom(42L);

    @Inject(method = "render(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLnet/minecraft/util/math/random/AbstractRandom;JI)V", at = @At("HEAD"), cancellable = true)
    private void preRenderBlockInWorld(BlockRenderView world, BakedModel model, BlockState state, BlockPos pos, MatrixStack matrices, VertexConsumer vertexConsumer, boolean cull, AbstractRandom random, long seed, int overlay, CallbackInfo ci) {
//        GlobalRenderContext renderer = GlobalRenderContext.getInstance(world);
//        BlockRenderer blockRenderer = renderer.getBlockRenderer();
//
//        boolean ret = blockRenderer.renderModel(world, state, pos, model, new FallbackChunkModelBuffers(consumer, matrixStack), cull, seed);
//
//        cir.setReturnValue(ret);
    }

    /**
     * @reason Use optimized vertex writer intrinsics, avoid allocations
     * @author JellySquid
     */
    @Overwrite
    public void render(MatrixStack.Entry entry, VertexConsumer vertexConsumer, BlockState blockState, BakedModel bakedModel, float red, float green, float blue, int light, int overlay) {
        QuadVertexSink drain = VertexDrain.of(vertexConsumer)
                .createSink(VanillaVertexFormats.QUADS);
        Xoroshiro128PlusPlusRandom random = this.random;

        // Clamp color ranges
        red = MathHelper.clamp(red, 0.0F, 1.0F);
        green = MathHelper.clamp(green, 0.0F, 1.0F);
        blue = MathHelper.clamp(blue, 0.0F, 1.0F);

        int defaultColor = ColorABGR.pack(red, green, blue, 1.0F);

        for (Direction direction : DirectionUtil.ALL_DIRECTIONS) {
            random.setSeed(42L);
            List<BakedQuad> quads = bakedModel.getQuads(blockState, direction, random);

            if (!quads.isEmpty()) {
                renderQuad(entry, drain, defaultColor, quads, light, overlay);
            }
        }

        random.setSeed(42L);
        List<BakedQuad> quads = bakedModel.getQuads(blockState, null, random);

        if (!quads.isEmpty()) {
            renderQuad(entry, drain, defaultColor, quads, light, overlay);
        }

        drain.flush();
    }

    private static void renderQuad(MatrixStack.Entry entry, QuadVertexSink drain, int defaultColor, List<BakedQuad> list, int light, int overlay) {
        if (list.isEmpty()) {
            return;
        }

        drain.ensureCapacity(list.size() * 4);

        for (BakedQuad bakedQuad : list) {
            int color = bakedQuad.hasColor() ? defaultColor : 0xFFFFFFFF;

            ModelQuadView quad = ((ModelQuadView) bakedQuad);

            for (int i = 0; i < 4; i++) {
                drain.writeQuad(entry, quad.getX(i), quad.getY(i), quad.getZ(i), color, quad.getTexU(i), quad.getTexV(i),
                        light, overlay, ModelQuadUtil.getFacingNormal(bakedQuad.getFace()));
            }

            SpriteUtil.markSpriteActive(quad.getSprite());
        }
    }
}

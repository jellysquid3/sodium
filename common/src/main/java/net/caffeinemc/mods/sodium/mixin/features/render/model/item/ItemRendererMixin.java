package net.caffeinemc.mods.sodium.mixin.features.render.model.item;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.client.model.quad.BakedQuadView;
import net.caffeinemc.mods.sodium.client.render.immediate.model.BakedModelEncoder;
import net.caffeinemc.mods.sodium.client.render.texture.SpriteUtil;
import net.caffeinemc.mods.sodium.client.render.vertex.VertexConsumerUtils;
import net.caffeinemc.mods.sodium.client.util.DirectionUtil;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.RenderType;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Unique
    private static final ThreadLocal<RandomSource> random = ThreadLocal.withInitial(() -> new SingleThreadedRandomSource(42L));

    @Unique
    private static final ThreadLocal<ItemDisplayContext> currentRenderContext = ThreadLocal.withInitial(() -> ItemDisplayContext.NONE);

    @Unique
    private static final Vector3f FACE_NORMAL = new Vector3f();
    @Unique
    private static final Vector3f VERTEX_POS = new Vector3f();
    @Unique
    private static final Matrix4f FACE_NORMAL_MATRIX = new Matrix4f();
    @Unique
    private static final Vector3f EDGE1 = new Vector3f();
    @Unique
    private static final Vector3f EDGE2 = new Vector3f();
    @Unique
    private static final float[] VERTEX_CACHE = new float[24];

    @Shadow
    private static int getLayerColorSafe(int[] is, int i) {
        throw new AssertionError("Not shadowed");
    }

    @Inject(method = "renderItem", at = @At("HEAD"))
    private static void onRenderItemStart(ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, int[] tintLayers, BakedModel model, RenderType renderType, ItemStackRenderState.FoilType foilType, CallbackInfo ci) {
        currentRenderContext.set(displayContext);
    }

    @Inject(method = "renderItem", at = @At("RETURN"))
    private static void onRenderItemEnd(ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource,
                                        int packedLight, int packedOverlay, int[] tintLayers, BakedModel model,
                                        RenderType renderType, ItemStackRenderState.FoilType foilType, CallbackInfo ci) {
        currentRenderContext.set(ItemDisplayContext.NONE);
    }

    @Unique
    private static boolean isFacingAway(PoseStack.Pose matrices, BakedQuad quad) {
        if (currentRenderContext.get() == ItemDisplayContext.GUI) {
            return false;  // Never cull faces in GUI
        }

        int[] vertices = quad.getVertices();
        if (vertices.length < 32) {
            return false;
        }

        // Extract normal directly from first vertex - most common case
        float nx = Float.intBitsToFloat(vertices[6]);
        float ny = Float.intBitsToFloat(vertices[7]);
        float nz = Float.intBitsToFloat(vertices[14]);

        // Fast path for quads with normals
        if (nx != 0 || ny != 0 || nz != 0) {
            matrices.pose().normal(FACE_NORMAL_MATRIX);

            FACE_NORMAL.set(
                    nx * FACE_NORMAL_MATRIX.m00() + ny * FACE_NORMAL_MATRIX.m01() + nz * FACE_NORMAL_MATRIX.m02(),
                    nx * FACE_NORMAL_MATRIX.m10() + ny * FACE_NORMAL_MATRIX.m11() + nz * FACE_NORMAL_MATRIX.m12(),
                    nx * FACE_NORMAL_MATRIX.m20() + ny * FACE_NORMAL_MATRIX.m21() + nz * FACE_NORMAL_MATRIX.m22()
            );

            VERTEX_POS.set(
                    Float.intBitsToFloat(vertices[0]),
                    Float.intBitsToFloat(vertices[1]),
                    Float.intBitsToFloat(vertices[2])
            );
            matrices.pose().transformPosition(VERTEX_POS);

            return (FACE_NORMAL.x * -VERTEX_POS.x +
                    FACE_NORMAL.y * -VERTEX_POS.y +
                    FACE_NORMAL.z * -VERTEX_POS.z) < 0.0f;
        }

        // Fallback path for quads without normals
        for (int i = 0; i < 24; i += 8) {
            int baseIndex = (i / 8) * 8;
            VERTEX_CACHE[i] = Float.intBitsToFloat(vertices[baseIndex]);
            VERTEX_CACHE[i + 1] = Float.intBitsToFloat(vertices[baseIndex + 1]);
            VERTEX_CACHE[i + 2] = Float.intBitsToFloat(vertices[baseIndex + 2]);
        }

        EDGE1.set(
                VERTEX_CACHE[8] - VERTEX_CACHE[0],
                VERTEX_CACHE[9] - VERTEX_CACHE[1],
                VERTEX_CACHE[10] - VERTEX_CACHE[2]
        );

        EDGE2.set(
                VERTEX_CACHE[16] - VERTEX_CACHE[0],
                VERTEX_CACHE[17] - VERTEX_CACHE[1],
                VERTEX_CACHE[18] - VERTEX_CACHE[2]
        );

        FACE_NORMAL.set(
                EDGE1.y * EDGE2.z - EDGE1.z * EDGE2.y,
                EDGE1.z * EDGE2.x - EDGE1.x * EDGE2.z,
                EDGE1.x * EDGE2.y - EDGE1.y * EDGE2.x
        );

        matrices.pose().normal(FACE_NORMAL_MATRIX);
        FACE_NORMAL_MATRIX.transformDirection(FACE_NORMAL);

        VERTEX_POS.set(VERTEX_CACHE[0], VERTEX_CACHE[1], VERTEX_CACHE[2]);
        matrices.pose().transformPosition(VERTEX_POS);

        return (FACE_NORMAL.x * -VERTEX_POS.x +
                FACE_NORMAL.y * -VERTEX_POS.y +
                FACE_NORMAL.z * -VERTEX_POS.z) < 0.0f;
    }

    /**
     * @reason Avoid Allocations
     * @return JellySquid
     */
    @WrapOperation(method = "renderModelLists", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;create()Lnet/minecraft/util/RandomSource;"))
    private static RandomSource renderModelFastRandom(Operation<RandomSource> original) {
        return ItemRendererMixin.random.get();
    }

    /**
     * @reason Avoid Allocations
     * @return JellySquid
     */
    @WrapOperation(method = "renderModelLists", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction;values()[Lnet/minecraft/core/Direction;"))
    private static Direction[] renderModelFastDirections(Operation<RandomSource> original) {
        return DirectionUtil.ALL_DIRECTIONS;
    }

    /**
     * @reason Avoid Allocations
     * @return JellySquid
     */
    @WrapOperation(method = "renderModelLists", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderQuadList(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Ljava/util/List;[III)V"))
    private static void renderModelFast(PoseStack poseStack, VertexConsumer vertexConsumer, List<BakedQuad> quads, int[] colors, int light, int overlay, Operation<Void> original) {
        var writer = VertexConsumerUtils.convertOrLog(vertexConsumer);

        if (writer == null) {
            original.call(poseStack, vertexConsumer, quads, colors, light, overlay);
            return;
        }

        // TODO/NOTE: Should .last be a LocalRef?
        if (!quads.isEmpty()) {
            renderBakedItemQuads(poseStack.last(), writer, quads, colors, light, overlay);
        }
    }

    @Unique
    @SuppressWarnings("ForLoopReplaceableByForEach")
    private static void renderBakedItemQuads(PoseStack.Pose matrices, VertexBufferWriter writer, List<BakedQuad> quads, int[] colors, int light, int overlay) {
        for (int i = 0; i < quads.size(); i++) {
            BakedQuad bakedQuad = quads.get(i);

            if (bakedQuad.getVertices().length < 32) {
                continue; // ignore bad quads
            }

            if (!isFacingAway(matrices, bakedQuad)) {
                BakedQuadView quad = (BakedQuadView) bakedQuad;

                int color = 0xFFFFFFFF;

                if (bakedQuad.isTinted()) {
                    color = ColorARGB.toABGR(getLayerColorSafe(colors, bakedQuad.getTintIndex()));
                }

                BakedModelEncoder.writeQuadVertices(writer, matrices, quad, color, light, overlay, BakedModelEncoder.shouldMultiplyAlpha());

                SpriteUtil.markSpriteActive(quad.getSprite());
            }
        }
    }
}
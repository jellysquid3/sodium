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
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Unique
    private static final ThreadLocal<RandomSource> random = ThreadLocal.withInitial(() -> new SingleThreadedRandomSource(42L));

    @Shadow
    private static int getLayerColorSafe(int[] is, int i) {
        throw new AssertionError("Not shadowed");
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

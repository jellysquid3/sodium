package net.caffeinemc.mods.sodium.mixin.features.render.immediate.buffer_builder.intrinsics;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.api.texture.SpriteUtil;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.caffeinemc.mods.sodium.client.render.immediate.model.BakedModelEncoder;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@SuppressWarnings({ "SameParameterValue" })
@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin implements VertexConsumer {
    @Shadow
    @Final
    private boolean fastFormat;

    @Override
    public void putBulkData(PoseStack.Pose matrices, BakedQuad bakedQuad, float r, float g, float b, float a, int light, int overlay) {
        if (!this.fastFormat) {
            VertexConsumer.super.putBulkData(matrices, bakedQuad, r, g, b, a, light, overlay);

            TextureAtlasSprite sprite = bakedQuad.getSprite();
            // This can be null
            //noinspection ConstantValue
            if (sprite != null) {
                SpriteUtil.INSTANCE.markSpriteActive(sprite);
            }

            return;
        }

        if (bakedQuad.getVertices().length < 32) {
            return; // we do not accept quads with less than 4 properly sized vertices
        }

        VertexBufferWriter writer = VertexBufferWriter.of(this);

        ModelQuadView quad = (ModelQuadView) bakedQuad;

        int color = ColorABGR.pack(r, g, b, a);
        BakedModelEncoder.writeQuadVertices(writer, matrices, quad, color, light, overlay, false);

        TextureAtlasSprite sprite = quad.getSprite();
        if (sprite != null) {
            SpriteUtil.INSTANCE.markSpriteActive(sprite);
        }
    }

    @Override
    public void putBulkData(PoseStack.Pose matrices, BakedQuad bakedQuad, float[] brightnessTable, float r, float g, float b, float a, int[] light, int overlay, boolean colorize) {
        if (!this.fastFormat) {
            VertexConsumer.super.putBulkData(matrices, bakedQuad, brightnessTable, r, g, b, a, light, overlay, colorize);

            TextureAtlasSprite sprite = bakedQuad.getSprite();
            // This can be null
            //noinspection ConstantValue
            if (sprite != null) {
                SpriteUtil.INSTANCE.markSpriteActive(sprite);
            }

            return;
        }

        if (bakedQuad.getVertices().length < 32) {
            return; // we do not accept quads with less than 4 properly sized vertices
        }

        VertexBufferWriter writer = VertexBufferWriter.of(this);

        ModelQuadView quad = (ModelQuadView) bakedQuad;

        BakedModelEncoder.writeQuadVertices(writer, matrices, quad, r, g, b, a, brightnessTable, colorize, light, overlay);

        TextureAtlasSprite sprite = quad.getSprite();
        if (sprite != null) {
            SpriteUtil.INSTANCE.markSpriteActive(sprite);
        }
    }
}

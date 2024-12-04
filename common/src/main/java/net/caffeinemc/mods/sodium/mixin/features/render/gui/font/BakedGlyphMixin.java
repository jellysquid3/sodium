package net.caffeinemc.mods.sodium.mixin.features.render.gui.font;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.caffeinemc.mods.sodium.client.render.vertex.VertexConsumerUtils;
import net.caffeinemc.mods.sodium.api.vertex.format.common.GlyphVertex;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.math.MatrixHelper;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BakedGlyph.class)
public class BakedGlyphMixin {
    @Shadow
    @Final
    private float left;

    @Shadow
    @Final
    private float right;

    @Shadow
    @Final
    private float up;

    @Shadow
    @Final
    private float down;

    @Shadow
    @Final
    private float u0;

    @Shadow
    @Final
    private float v0;

    @Shadow
    @Final
    private float v1;

    @Shadow
    @Final
    private float u1;

    /**
     * @reason Use intrinsics
     * @author JellySquid
     */
    @Inject(method = "render(ZFFFLorg/joml/Matrix4f;Lcom/mojang/blaze3d/vertex/VertexConsumer;IZI)V", at = @At("HEAD"), cancellable = true)
    private void drawFast(boolean italic, float x, float y, float z, Matrix4f matrix, VertexConsumer vertexConsumer, int c, boolean bl2, int light, CallbackInfo ci) {
        var writer = VertexConsumerUtils.convertOrLog(vertexConsumer);

        if (writer == null) {
            return;
        }

        ci.cancel();

        float x1 = x + this.left;
        float x2 = x + this.right;
        float h1 = y + this.up;
        float h2 = y + this.down;
        float w1 = italic ? 1.0F - 0.25F * this.up : 0.0F;
        float w2 = italic ? 1.0F - 0.25F * this.down : 0.0F;
        float offset = bl2 ? 0.1F : 0.0F;

        int color = ColorARGB.toABGR(c);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            long buffer = stack.nmalloc(4 * GlyphVertex.STRIDE);
            long ptr = buffer;

            write(ptr, matrix, x1 + w1 - offset, h1 - offset, z, color, this.u0, this.v0, light);
            ptr += GlyphVertex.STRIDE;

            write(ptr, matrix, x1 + w2 - offset, h2 + offset, z, color, this.u0, this.v1, light);
            ptr += GlyphVertex.STRIDE;

            write(ptr, matrix, x2 + w2 + offset, h2 + offset, z, color, this.u1, this.v1, light);
            ptr += GlyphVertex.STRIDE;

            write(ptr, matrix, x2 + w1 + offset, h1 - offset, z, color, this.u1, this.v0, light);
            ptr += GlyphVertex.STRIDE;

            writer.push(stack, buffer, 4, GlyphVertex.FORMAT);
        }
    }

    /**
     * @reason Use intrinsics
     * @author JellySquid
     */
    @Inject(method = "buildEffect", at = @At("HEAD"), cancellable = true)
    private void drawEffectFast(BakedGlyph.Effect effect, float offset, float depthOffset, int c, VertexConsumer vertexConsumer, int light, Matrix4f matrix, CallbackInfo ci) {
        var writer = VertexConsumerUtils.convertOrLog(vertexConsumer);

        if (writer == null) {
            return;
        }

        ci.cancel();

        float x1 = effect.x0();
        float x2 = effect.x1();
        float h1 = effect.y0();
        float h2 = effect.y1();
        float z = effect.depth() + depthOffset;

        int color = ColorARGB.toABGR(c);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            long buffer = stack.nmalloc(4 * GlyphVertex.STRIDE);
            long ptr = buffer;

            write(ptr, matrix, x1 + offset, h1 + offset, z, color, this.u0, this.v0, light);
            ptr += GlyphVertex.STRIDE;

            write(ptr, matrix, x2 + offset, h1 + offset, z, color, this.u0, this.v1, light);
            ptr += GlyphVertex.STRIDE;

            write(ptr, matrix, x2 + offset, h2 + offset, z, color, this.u1, this.v1, light);
            ptr += GlyphVertex.STRIDE;

            write(ptr, matrix, x1 + offset, h2 + offset, z, color, this.u1, this.v0, light);
            ptr += GlyphVertex.STRIDE;

            writer.push(stack, buffer, 4, GlyphVertex.FORMAT);
        }
    }

    @Unique
    private static void write(long buffer,
                              Matrix4f matrix, float x, float y, float z, int color, float u, float v, int light) {
        float x2 = MatrixHelper.transformPositionX(matrix, x, y, z);
        float y2 = MatrixHelper.transformPositionY(matrix, x, y, z);
        float z2 = MatrixHelper.transformPositionZ(matrix, x, y, z);

        GlyphVertex.put(buffer, x2, y2, z2, color, u, v, light);
    }

}

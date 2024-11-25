package net.caffeinemc.mods.sodium.mixin.features.render.gui.outlines;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.client.render.vertex.VertexConsumerUtils;
import net.caffeinemc.mods.sodium.api.vertex.format.common.LineVertex;
import net.caffeinemc.mods.sodium.client.render.vertex.buffer.BufferBuilderExtension;
import net.minecraft.client.renderer.LevelRenderer;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.util.NormI8;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.minecraft.client.renderer.ShapeRenderer;
import org.joml.Math;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShapeRenderer.class)
public class LevelRendererMixin {
    /**
     * @author JellySquid
     * @reason Use intrinsics where possible to speed up vertex writing
     */
    @Inject(method = "renderLineBox(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;DDDDDDFFFFFFF)V", at = @At("HEAD"), cancellable = true)
    private static void drawBoxFast(PoseStack matrices, VertexConsumer vertexConsumer, double x1, double y1, double z1,
                                    double x2, double y2, double z2, float red, float green, float blue, float alpha,
                                    float xAxisRed, float yAxisGreen, float zAxisBlue, CallbackInfo ci) {
        var writer = VertexConsumerUtils.convertOrLog(vertexConsumer);

        if (writer == null) {
            return;
        }

        ci.cancel();

        Matrix4f position = matrices.last().pose();
        Matrix3f normal = matrices.last().normal();

        float x1f = (float) x1;
        float y1f = (float) y1;
        float z1f = (float) z1;
        float x2f = (float) x2;
        float y2f = (float) y2;
        float z2f = (float) z2;

        int color = ColorABGR.pack(red, green, blue, alpha);

        float v1x = Math.fma(position.m00(), x1f, Math.fma(position.m10(), y1f, Math.fma(position.m20(), z1f, position.m30())));
        float v1y = Math.fma(position.m01(), x1f, Math.fma(position.m11(), y1f, Math.fma(position.m21(), z1f, position.m31())));
        float v1z = Math.fma(position.m02(), x1f, Math.fma(position.m12(), y1f, Math.fma(position.m22(), z1f, position.m32())));

        float v2x = Math.fma(position.m00(), x2f, Math.fma(position.m10(), y1f, Math.fma(position.m20(), z1f, position.m30())));
        float v2y = Math.fma(position.m01(), x2f, Math.fma(position.m11(), y1f, Math.fma(position.m21(), z1f, position.m31())));
        float v2z = Math.fma(position.m02(), x2f, Math.fma(position.m12(), y1f, Math.fma(position.m22(), z1f, position.m32())));

        float v3x = Math.fma(position.m00(), x1f, Math.fma(position.m10(), y2f, Math.fma(position.m20(), z1f, position.m30())));
        float v3y = Math.fma(position.m01(), x1f, Math.fma(position.m11(), y2f, Math.fma(position.m21(), z1f, position.m31())));
        float v3z = Math.fma(position.m02(), x1f, Math.fma(position.m12(), y2f, Math.fma(position.m22(), z1f, position.m32())));

        float v4x = Math.fma(position.m00(), x1f, Math.fma(position.m10(), y1f, Math.fma(position.m20(), z2f, position.m30())));
        float v4y = Math.fma(position.m01(), x1f, Math.fma(position.m11(), y1f, Math.fma(position.m21(), z2f, position.m31())));
        float v4z = Math.fma(position.m02(), x1f, Math.fma(position.m12(), y1f, Math.fma(position.m22(), z2f, position.m32())));

        float v5x = Math.fma(position.m00(), x2f, Math.fma(position.m10(), y2f, Math.fma(position.m20(), z1f, position.m30())));
        float v5y = Math.fma(position.m01(), x2f, Math.fma(position.m11(), y2f, Math.fma(position.m21(), z1f, position.m31())));
        float v5z = Math.fma(position.m02(), x2f, Math.fma(position.m12(), y2f, Math.fma(position.m22(), z1f, position.m32())));

        float v6x = Math.fma(position.m00(), x1f, Math.fma(position.m10(), y2f, Math.fma(position.m20(), z2f, position.m30())));
        float v6y = Math.fma(position.m01(), x1f, Math.fma(position.m11(), y2f, Math.fma(position.m21(), z2f, position.m31())));
        float v6z = Math.fma(position.m02(), x1f, Math.fma(position.m12(), y2f, Math.fma(position.m22(), z2f, position.m32())));

        float v7x = Math.fma(position.m00(), x2f, Math.fma(position.m10(), y1f, Math.fma(position.m20(), z2f, position.m30())));
        float v7y = Math.fma(position.m01(), x2f, Math.fma(position.m11(), y1f, Math.fma(position.m21(), z2f, position.m31())));
        float v7z = Math.fma(position.m02(), x2f, Math.fma(position.m12(), y1f, Math.fma(position.m22(), z2f, position.m32())));

        float v8x = Math.fma(position.m00(), x2f, Math.fma(position.m10(), y2f, Math.fma(position.m20(), z2f, position.m30())));
        float v8y = Math.fma(position.m01(), x2f, Math.fma(position.m11(), y2f, Math.fma(position.m21(), z2f, position.m31())));
        float v8z = Math.fma(position.m02(), x2f, Math.fma(position.m12(), y2f, Math.fma(position.m22(), z2f, position.m32())));

        if (vertexConsumer instanceof BufferBuilderExtension ext) {
            ext.sodium$duplicateVertex();
        }

        writeLineVertices(writer, v1x, v1y, v1z, ColorABGR.pack(red, yAxisGreen, zAxisBlue, alpha), NormI8.pack(normal.m00(), normal.m01(), normal.m02()));
        writeLineVertices(writer, v2x, v2y, v2z, ColorABGR.pack(red, yAxisGreen, zAxisBlue, alpha), NormI8.pack(normal.m00(), normal.m01(), normal.m02()));
        writeLineVertices(writer, v1x, v1y, v1z, ColorABGR.pack(xAxisRed, green, zAxisBlue, alpha), NormI8.pack(normal.m10(), normal.m11(), normal.m12()));
        writeLineVertices(writer, v3x, v3y, v3z, ColorABGR.pack(xAxisRed, green, zAxisBlue, alpha), NormI8.pack(normal.m10(), normal.m11(), normal.m12()));
        writeLineVertices(writer, v1x, v1y, v1z, ColorABGR.pack(xAxisRed, yAxisGreen, blue, alpha), NormI8.pack(normal.m20(), normal.m21(), normal.m22()));
        writeLineVertices(writer, v4x, v4y, v4z, ColorABGR.pack(xAxisRed, yAxisGreen, blue, alpha), NormI8.pack(normal.m20(), normal.m21(), normal.m22()));
        writeLineVertices(writer, v2x, v2y, v2z, color, NormI8.pack(normal.m10(), normal.m11(), normal.m12()));
        writeLineVertices(writer, v5x, v5y, v5z, color, NormI8.pack(normal.m10(), normal.m11(), normal.m12()));
        writeLineVertices(writer, v5x, v5y, v5z, color, NormI8.pack(-normal.m00(), -normal.m01(), -normal.m02()));
        writeLineVertices(writer, v3x, v3y, v3z, color, NormI8.pack(-normal.m00(), -normal.m01(), -normal.m02()));
        writeLineVertices(writer, v3x, v3y, v3z, color, NormI8.pack(normal.m20(), normal.m21(), normal.m22()));
        writeLineVertices(writer, v6x, v6y, v6z, color, NormI8.pack(normal.m20(), normal.m21(), normal.m22()));
        writeLineVertices(writer, v6x, v6y, v6z, color, NormI8.pack(-normal.m10(), -normal.m11(), -normal.m12()));
        writeLineVertices(writer, v4x, v4y, v4z, color, NormI8.pack(-normal.m10(), -normal.m11(), -normal.m12()));
        writeLineVertices(writer, v4x, v4y, v4z, color, NormI8.pack(normal.m00(), normal.m01(), normal.m02()));
        writeLineVertices(writer, v7x, v7y, v7z, color, NormI8.pack(normal.m00(), normal.m01(), normal.m02()));
        writeLineVertices(writer, v7x, v7y, v7z, color, NormI8.pack(-normal.m20(), -normal.m21(), -normal.m22()));
        writeLineVertices(writer, v2x, v2y, v2z, color, NormI8.pack(-normal.m20(), -normal.m21(), -normal.m22()));
        writeLineVertices(writer, v6x, v6y, v6z, color, NormI8.pack(normal.m00(), normal.m01(), normal.m02()));
        writeLineVertices(writer, v8x, v8y, v8z, color, NormI8.pack(normal.m00(), normal.m01(), normal.m02()));
        writeLineVertices(writer, v7x, v7y, v7z, color, NormI8.pack(normal.m10(), normal.m11(), normal.m12()));
        writeLineVertices(writer, v8x, v8y, v8z, color, NormI8.pack(normal.m10(), normal.m11(), normal.m12()));
        writeLineVertices(writer, v5x, v5y, v5z, color, NormI8.pack(normal.m20(), normal.m21(), normal.m22()));
        writeLineVertex(writer, v8x, v8y, v8z, color, NormI8.pack(normal.m20(), normal.m21(), normal.m22()));
    }

    @Unique
    private static void writeLineVertices(VertexBufferWriter writer, float x, float y, float z, int color, int normal) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long buffer = stack.nmalloc(2 * LineVertex.STRIDE);
            long ptr = buffer;

            for (int i = 0; i < 2; i++) {
                LineVertex.put(ptr, x, y, z, color, normal);
                ptr += LineVertex.STRIDE;
            }

            writer.push(stack, buffer, 2, LineVertex.FORMAT);
        }
    }

    @Unique
    private static void writeLineVertex(VertexBufferWriter writer, float x, float y, float z, int color, int normal) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long buffer = stack.nmalloc(LineVertex.STRIDE);
            LineVertex.put(buffer, x, y, z, color, normal);

            writer.push(stack, buffer, 1, LineVertex.FORMAT);
        }
    }

}

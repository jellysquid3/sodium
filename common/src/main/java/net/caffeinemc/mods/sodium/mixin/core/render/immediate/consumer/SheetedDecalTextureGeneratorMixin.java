package net.caffeinemc.mods.sodium.mixin.core.render.immediate.consumer;

import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.util.NormI8;
import net.caffeinemc.mods.sodium.api.vertex.attributes.common.ColorAttribute;
import net.caffeinemc.mods.sodium.api.vertex.attributes.common.TextureAttribute;
import net.minecraft.core.Direction;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SheetedDecalTextureGenerator.class)
public class SheetedDecalTextureGeneratorMixin implements VertexBufferWriter {
    @Shadow
    @Final
    private VertexConsumer delegate;

    @Shadow
    @Final
    private Matrix3f normalInversePose;

    @Shadow
    @Final
    private Matrix4f cameraInversePose;

    @Shadow
    @Final
    private float textureScale;

    @Unique
    private boolean canUseIntrinsics;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        this.canUseIntrinsics = VertexBufferWriter.tryOf(this.delegate) != null;
    }

    @Override
    public boolean canUseIntrinsics() {
        return this.canUseIntrinsics;
    }

    @Override
    public void push(MemoryStack stack, long ptr, int count, VertexFormat format) {
        transform(ptr, count, format,
                this.normalInversePose, this.cameraInversePose, this.textureScale);

        VertexBufferWriter.of(this.delegate)
                .push(stack, ptr, count, format);
    }

    /**
     * Transforms the overlay UVs element of each vertex to create a perspective-mapped effect.
     *
     * @param ptr    The buffer of vertices to transform
     * @param count  The number of vertices to transform
     * @param format The format of the vertices
     * @param inverseNormalMatrix The inverted normal matrix
     * @param inverseTextureMatrix The inverted texture matrix
     * @param textureScale The amount which the overlay texture should be adjusted
     */
    @Unique
    private static void transform(long ptr, int count, VertexFormat format,
                                  Matrix3f inverseNormalMatrix, Matrix4f inverseTextureMatrix, float textureScale) {
        long stride = format.getVertexSize();

        var offsetPosition = format.getOffset(VertexFormatElement.POSITION);
        var offsetColor = format.getOffset(VertexFormatElement.COLOR);
        var offsetNormal = format.getOffset(VertexFormatElement.NORMAL);
        var offsetTexture = format.getOffset(VertexFormatElement.UV0);

        int color = ColorABGR.pack(1.0f, 1.0f, 1.0f, 1.0f);

        var normal = new Vector3f(Float.NaN);
        var position = new Vector4f(Float.NaN);

        for (int vertexIndex = 0; vertexIndex < count; vertexIndex++) {
            position.x = MemoryUtil.memGetFloat(ptr + offsetPosition + 0);
            position.y = MemoryUtil.memGetFloat(ptr + offsetPosition + 4);
            position.z = MemoryUtil.memGetFloat(ptr + offsetPosition + 8);
            position.w = 1.0f;

            int packedNormal = MemoryUtil.memGetInt(ptr + offsetNormal);
            normal.x = NormI8.unpackX(packedNormal);
            normal.y = NormI8.unpackY(packedNormal);
            normal.z = NormI8.unpackZ(packedNormal);

            Vector3f transformedNormal = inverseNormalMatrix.transform(normal);
            Direction direction = Direction.getApproximateNearest(transformedNormal.x(), transformedNormal.y(), transformedNormal.z());

            Vector4f transformedTexture = inverseTextureMatrix.transform(position);
            transformedTexture.rotateY(3.1415927F);
            transformedTexture.rotateX(-1.5707964F);
            transformedTexture.rotate(direction.getRotation());

            float textureU = -transformedTexture.x() * textureScale;
            float textureV = -transformedTexture.y() * textureScale;

            ColorAttribute.set(ptr + offsetColor, color);
            TextureAttribute.put(ptr + offsetTexture, textureU, textureV);

            ptr += stride;
        }
    }
}

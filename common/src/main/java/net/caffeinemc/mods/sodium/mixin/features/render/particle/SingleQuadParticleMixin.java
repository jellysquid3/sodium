package net.caffeinemc.mods.sodium.mixin.features.render.particle;

import net.caffeinemc.mods.sodium.api.vertex.format.common.ParticleVertex;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.client.render.vertex.VertexConsumerUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SingleQuadParticle.class)
public abstract class SingleQuadParticleMixin extends Particle {
    @Shadow
    public abstract float getQuadSize(float tickDelta);

    @Shadow
    protected abstract float getU0();

    @Shadow
    protected abstract float getU1();

    @Shadow
    protected abstract float getV0();

    @Shadow
    protected abstract float getV1();

    protected SingleQuadParticleMixin(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    /**
     * @reason Optimize function
     * @author JellySquid
     */
    @Inject(method = "renderRotatedQuad(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Quaternionf;FFFF)V", at = @At("HEAD"), cancellable = true)
    protected void renderRotatedQuad(VertexConsumer vertexConsumer, Quaternionf quaternionf, float x, float y, float z, float tickDelta, CallbackInfo ci) {
        final var writer = VertexConsumerUtils.convertOrLog(vertexConsumer);

        if (writer == null) {
            return;
        }

        ci.cancel();

        float size = this.getQuadSize(tickDelta);
        float minU = this.getU0();
        float maxU = this.getU1();
        float minV = this.getV0();
        float maxV = this.getV1();
        int light = this.getLightColor(tickDelta);

        int color = ColorABGR.pack(this.rCol, this.gCol, this.bCol, this.alpha);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            long buffer = stack.nmalloc(4 * ParticleVertex.STRIDE);
            long ptr = buffer;

            this.sodium$writeVertex(ptr, quaternionf, x, y, z, 1.0F, -1.0F, size, maxU, maxV, color, light);
            ptr += ParticleVertex.STRIDE;

            this.sodium$writeVertex(ptr, quaternionf, x, y, z, 1.0F, 1.0F, size, maxU, minV, color, light);
            ptr += ParticleVertex.STRIDE;

            this.sodium$writeVertex(ptr, quaternionf, x, y, z, -1.0F, 1.0F, size, minU, minV, color, light);
            ptr += ParticleVertex.STRIDE;

            this.sodium$writeVertex(ptr, quaternionf, x, y, z, -1.0F, -1.0F, size, minU, maxV, color, light);
            ptr += ParticleVertex.STRIDE;

            writer.push(stack, buffer, 4, ParticleVertex.FORMAT);
        }
    }

    @Unique
    private final Vector3f sodium$scratchVertex = new Vector3f(); // not thread-safe

    @Unique
    private void sodium$writeVertex(long ptr, Quaternionf quaternionf, float originX, float originY, float originZ, float posX, float posY, float size, float u, float v, int color, int light) {
        final var vertex = this.sodium$scratchVertex;
        vertex.set(posX, posY, 0.0f);
        vertex.rotate(quaternionf);
        vertex.mul(size);
        vertex.add(originX, originY, originZ);

        ParticleVertex.put(ptr, vertex.x(), vertex.y(), vertex.z(), u, v, color, light);
    }
}

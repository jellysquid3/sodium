package net.caffeinemc.mods.sodium.mixin.features.render.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.ParticleVertex;
import net.caffeinemc.mods.sodium.client.render.vertex.VertexConsumerUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Math;
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

    @Unique
    private static final Vector3f TEMP_LEFT = new Vector3f();

    @Unique
    private static final Vector3f TEMP_UP = new Vector3f();

    /**
     * @reason Build vertex data using the left and up vectors to avoid quaternion calculations
     * @author MoePus
     */
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V", at = @At("HEAD"), cancellable = true)
    protected void render(VertexConsumer vertexConsumer, Camera camera, float tickDelta, CallbackInfo ci) {
        final var writer = VertexConsumerUtils.convertOrLog(vertexConsumer);

        if (writer == null) {
            return;
        }

        ci.cancel();

        float size = this.getQuadSize(tickDelta);

        Vector3f left = TEMP_LEFT;
        left.set(camera.getLeftVector())
                .mul(size);

        Vector3f up = TEMP_UP;
        up.set(camera.getUpVector())
                .mul(size);

        if (!Mth.equal(this.roll, 0.0f)) {
            float roll = Mth.lerp(tickDelta, this.oRoll, this.roll);

            float sinRoll = Math.sin(roll);
            float cosRoll = Math.cosFromSin(sinRoll, roll);

            float rv1x = Math.fma(cosRoll, left.x, sinRoll * up.x),
                    rv1y = Math.fma(cosRoll, left.y, sinRoll * up.y),
                    rv1z = Math.fma(cosRoll, left.z, sinRoll * up.z);

            float rv2x = Math.fma(-sinRoll, left.x, cosRoll * up.x),
                    rv2y = Math.fma(-sinRoll, left.y, cosRoll * up.y),
                    rv2z = Math.fma(-sinRoll, left.z, cosRoll * up.z);

            left.set(rv1x, rv1y, rv1z);
            up.set(rv2x, rv2y, rv2z);
        }

        this.sodium$emitVertices(writer, camera.getPosition(), left, up, tickDelta);
    }

    /**
     * @reason Build vertex data using the left and up vectors to avoid quaternion calculations
     * @author MoePus
     */
    @Inject(method = "renderRotatedQuad(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;Lorg/joml/Quaternionf;F)V", at = @At("HEAD"), cancellable = true)
    protected void renderRotatedQuad(VertexConsumer vertexConsumer, Camera camera, Quaternionf quaternion, float tickDelta, CallbackInfo ci) {
        final var writer = VertexConsumerUtils.convertOrLog(vertexConsumer);

        if (writer == null) {
            return;
        }

        ci.cancel();

        float size = this.getQuadSize(tickDelta);

        // Some particle class implementations may call this function directly, in which case we cannot assume anything
        // about the transform being used. However, we can still extract the left/up vectors from the quaternion,
        // it's just slightly slower than using the camera's left/up vectors directly.
        Vector3f left = TEMP_LEFT;
        left.set(-size, 0.0f, 0.0f)
                .rotate(quaternion);

        Vector3f up = TEMP_UP;
        up.set(0.0f, size, 0.0f)
                .rotate(quaternion);

        this.sodium$emitVertices(writer, camera.getPosition(), left, up, tickDelta);
    }

    @Unique
    private void sodium$emitVertices(VertexBufferWriter writer, Vec3 camera, Vector3f left, Vector3f up, float tickDelta) {
        float minU = this.getU0();
        float maxU = this.getU1();
        float minV = this.getV0();
        float maxV = this.getV1();

        int light = this.getLightColor(tickDelta);
        int color = ColorABGR.pack(this.rCol, this.gCol, this.bCol, this.alpha);

        float x = (float) (Mth.lerp(tickDelta, this.xo, this.x) - camera.x());
        float y = (float) (Mth.lerp(tickDelta, this.yo, this.y) - camera.y());
        float z = (float) (Mth.lerp(tickDelta, this.zo, this.z) - camera.z());

        try (MemoryStack stack = MemoryStack.stackPush()) {
            long buffer = stack.nmalloc(4 * ParticleVertex.STRIDE);
            long ptr = buffer;

            ParticleVertex.put(ptr, -left.x - up.x + x, -left.y - up.y + y, -left.z - up.z + z, maxU, maxV, color, light);
            ptr += ParticleVertex.STRIDE;

            ParticleVertex.put(ptr, -left.x + up.x + x, -left.y + up.y + y, -left.z + up.z + z, maxU, minV, color, light);
            ptr += ParticleVertex.STRIDE;

            ParticleVertex.put(ptr, left.x + up.x + x, left.y + up.y + y, left.z + up.z + z, minU, minV, color, light);
            ptr += ParticleVertex.STRIDE;

            ParticleVertex.put(ptr, left.x - up.x + x, left.y - up.y + y, left.z - up.z + z, minU, maxV, color, light);
            ptr += ParticleVertex.STRIDE;

            writer.push(stack, buffer, 4, ParticleVertex.FORMAT);
        }
    }
}

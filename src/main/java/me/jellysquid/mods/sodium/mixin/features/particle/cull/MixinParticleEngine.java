package me.jellysquid.mods.sodium.mixin.features.particle.cull;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.render.SodiumLevelRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;

@Mixin(ParticleEngine.class)
public class MixinParticleEngine {
    @Shadow
    @Final
    private Map<ParticleRenderType, Queue<Particle>> particles;

    private final Queue<Particle> cachedQueue = new ArrayDeque<>();
    private boolean useCulling;

    @Inject(method = "render", at = @At("HEAD"))
    private void preRenderParticles(PoseStack matrixStack, MultiBufferSource.BufferSource immediate, LightTexture lightmapTextureManager, Camera camera, float f, CallbackInfo ci) {
        // Setup the frustum state before rendering particles
        this.useCulling = SodiumClientMod.options().advanced.useParticleCulling;
    }

    @SuppressWarnings({ "SuspiciousMethodCalls", "unchecked" })
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private <V> V filterParticleList(Map<ParticleRenderType, Queue<Particle>> map, Object key, PoseStack matrixStack, MultiBufferSource.BufferSource immediate, LightTexture lightmapTextureManager, Camera camera, float f) {
        Queue<Particle> queue = this.particles.get(key);

        if (queue == null || queue.isEmpty()) {
            return null;
        }

        // If culling isn't enabled, simply return the queue as-is
        if (!this.useCulling) {
            return (V) queue;
        }

        // Filter particles which are not visible
        Queue<Particle> filtered = this.cachedQueue;
        filtered.clear();

        SodiumLevelRenderer levelRenderer = SodiumLevelRenderer.getInstance();

        for (Particle particle : queue) {
            AABB box = particle.getBoundingBox();

            // Hack: Grow the particle's bounding box in order to work around mis-behaved particles
            if (!levelRenderer.isBoxVisible(box.minX - 1.0D, box.minY - 1.0D, box.minZ - 1.0D, box.maxX + 1.0D, box.maxY + 1.0D, box.maxZ + 1.0D)) {
                continue;
            }

            filtered.add(particle);
        }

        return (V) filtered;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void postRenderParticles(PoseStack matrixStack, MultiBufferSource.BufferSource immediate, LightTexture lightmapTextureManager, Camera camera, float f, CallbackInfo ci) {
        // Ensure particles don't linger in the temporary collection
        this.cachedQueue.clear();
    }
}

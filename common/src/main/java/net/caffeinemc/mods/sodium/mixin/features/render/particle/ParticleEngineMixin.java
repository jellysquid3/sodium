package net.caffeinemc.mods.sodium.mixin.features.render.particle;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

    /**
     * @author AnOpenSauceDev
     * @reason Prevent Minecraft from rendering particles that are fog occluded.
     */
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V"))
    public void particleFogOcclusion(Particle instance, VertexConsumer vertexConsumer, Camera camera, float v){
        if(!SodiumClientMod.options().performance.useFogOcclusion){
            instance.render(vertexConsumer,camera,v);
            return;
        }

        if(sodium$isParticleFogOccluded(camera.getPosition(),instance.getBoundingBox().getCenter())){
            instance.render(vertexConsumer,camera,v);
        }
    }

    @Unique
    public boolean sodium$isParticleFogOccluded(Vec3 pointA, Vec3 pointB){
        double dx = pointA.x - pointB.x;
        double dz = pointA.z - pointB.z;
        double distance = (dx * dx + dz * dz);

        var shaderFog = RenderSystem.getShaderFog();
        double shaderFogDistance = shaderFog.end();

        var renderDistance = Minecraft.getInstance().gameRenderer.getRenderDistance();

        if(!Mth.equal(shaderFog.alpha(),1.0f)){
            return false;
        }

        var fogDist = Math.min(renderDistance,shaderFogDistance);

        return distance < fogDist * fogDist;
    }
}

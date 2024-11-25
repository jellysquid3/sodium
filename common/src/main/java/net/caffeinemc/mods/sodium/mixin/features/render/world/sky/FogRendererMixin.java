package net.caffeinemc.mods.sodium.mixin.features.render.world.sky;

import net.caffeinemc.mods.sodium.client.util.color.FastCubicSampler;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.CubicSampler;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FogRenderer.class)
public class FogRendererMixin {
    @Redirect(method = "computeFogColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/CubicSampler;gaussianSampleVec3(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/util/CubicSampler$Vec3Fetcher;)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 redirectSampleColor(Vec3 pos, CubicSampler.Vec3Fetcher fetcher, Camera camera, float tickDelta, ClientLevel level, int i, float f) {
        float u = Mth.clamp(Mth.cos(level.getTimeOfDay(tickDelta) * 6.2831855F) * 2.0F + 0.5F, 0.0F, 1.0F);

        return FastCubicSampler.sampleColor(pos,
                (x, y, z) -> level.getBiomeManager().getNoiseBiomeAtQuart(x, y, z).value().getFogColor(),
                (v) -> level.effects().getBrightnessDependentFogColor(v, u));
    }
}

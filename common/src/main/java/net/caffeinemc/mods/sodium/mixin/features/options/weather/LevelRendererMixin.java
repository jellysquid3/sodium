package net.caffeinemc.mods.sodium.mixin.features.options.weather;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WeatherEffectRenderer.class)
public class LevelRendererMixin {
    @Redirect(method = "render(Lnet/minecraft/world/level/Level;Lnet/minecraft/client/renderer/MultiBufferSource;IFLnet/minecraft/world/phys/Vec3;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;useFancyGraphics()Z"))
    private boolean redirectGetFancyWeather() {
        return SodiumClientMod.options().quality.weatherQuality.isFancy(Minecraft.getInstance().options.graphicsMode().get());
    }
}
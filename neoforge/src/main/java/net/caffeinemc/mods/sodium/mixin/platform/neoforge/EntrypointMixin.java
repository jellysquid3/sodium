package net.caffeinemc.mods.sodium.mixin.platform.neoforge;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.neoforge.config.ConfigLoaderForge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This Mixin is specially designed so the Sodium initializer always runs even if mod initialization has failed.
 */
@Mixin(Minecraft.class)
public class EntrypointMixin {
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;loadSelectedResourcePacks(Lnet/minecraft/server/packs/repository/PackRepository;)V"))
    private void sodium$loadConfig(GameConfig gameConfig, CallbackInfo ci) {
        ConfigLoaderForge.collectConfigEntryPoints();
        SodiumClientMod.onInitialization(ModList.get().getModContainerById("sodium").map(t -> t.getModInfo().getVersion().toString()).orElse("UNKNOWN"));
    }
}

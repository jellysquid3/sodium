package net.caffeinemc.mods.sodium.fabric;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.render.frapi.SodiumRenderer;
import net.caffeinemc.mods.sodium.client.util.FlawlessFrames;
import net.caffeinemc.mods.sodium.fabric.config.ConfigLoaderFabric;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import java.util.function.Consumer;

public class SodiumFabricMod implements ClientModInitializer {
    @Override
    @SuppressWarnings("unchecked")
    public void onInitializeClient() {
        ModContainer mod = FabricLoader.getInstance()
                .getModContainer("sodium")
                .orElseThrow(NullPointerException::new);

        ConfigLoaderFabric.collectConfigEntryPoints();
        SodiumClientMod.onInitialization(mod.getMetadata().getVersion().getFriendlyString());

        FabricLoader.getInstance()
                .getEntrypoints("frex_flawless_frames", Consumer.class)
                .forEach(api -> api.accept(FlawlessFrames.getProvider()));

        Renderer.register(SodiumRenderer.INSTANCE);
    }
}

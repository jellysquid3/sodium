package net.caffeinemc.mods.sodium.fabric.config;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.gui.SodiumConfigBuilder;
import net.fabricmc.loader.api.FabricLoader;

public class ConfigLoaderFabric {
    public static void collectConfigEntryPoints() {
        var entryPointContainers = FabricLoader.getInstance().getEntrypointContainers(ConfigManager.JSON_KEY_SODIUM_CONFIG_INTEGRATIONS, ConfigEntryPoint.class);
        for (var container : entryPointContainers) {
            var mod = container.getProvider();
            var metadata = mod.getMetadata();

            var modId = metadata.getId();
            var modName = metadata.getName();
            var modVersion = metadata.getVersion().getFriendlyString();

            ConfigManager.registerConfigEntryPoint(container::getEntrypoint, modId, modName, modVersion);
        }

        var sodiumMod = FabricLoader.getInstance().getModContainer("sodium").orElseThrow(NullPointerException::new);
        var sodiumMetadata = sodiumMod.getMetadata();
        ConfigManager.registerConfigEntryPoint(SodiumConfigBuilder::new, sodiumMetadata.getId(), sodiumMetadata.getName(), sodiumMetadata.getVersion().getFriendlyString());
    }
}

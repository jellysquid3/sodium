package net.caffeinemc.mods.sodium.fabric.config;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.gui.SodiumConfigBuilder;
import net.fabricmc.loader.api.FabricLoader;

public class ConfigLoaderFabric {
    private static ConfigManager.ModMetadata getModMetadata(String modId) {
        var mod = FabricLoader.getInstance().getModContainer(modId).orElseThrow(NullPointerException::new);
        var metadata = mod.getMetadata();
        return new ConfigManager.ModMetadata(metadata.getName(), metadata.getVersion().getFriendlyString());
    }

    public static void collectConfigEntryPoints() {
        ConfigManager.setModInfoFunction(ConfigLoaderFabric::getModMetadata);

        var entryPointContainers = FabricLoader.getInstance().getEntrypointContainers(ConfigManager.CONFIG_ENTRY_POINT_KEY, ConfigEntryPoint.class);
        for (var container : entryPointContainers) {
            ConfigManager.registerConfigEntryPoint(container::getEntrypoint, container.getProvider().getMetadata().getId());
        }

        ConfigManager.registerConfigEntryPoint(SodiumConfigBuilder::new, "sodium");
    }
}

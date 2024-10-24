package net.caffeinemc.mods.sodium.neoforge.config;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.gui.SodiumConfigBuilder;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

/**
 * Written with help from <a href="https://github.com/KingContaria/sodium-fabric/blob/de61e59a369dd8906ddb54050f48c02a29e3f217/neoforge/src/main/java/net/caffeinemc/mods/sodium/neoforge/gui/SodiumConfigIntegrationAPIForge.java">Contaria's implementation of this class</a>.
 */
public class ConfigLoaderForge {
    public static void collectConfigEntryPoints() {
        for (IModInfo mod : ModList.get().getMods()) {
            var modId = mod.getModId();
            var modName = mod.getDisplayName();
            var modVersion = mod.getVersion().toString();

            if (modId.equals("sodium")) {
                ConfigManager.registerConfigEntryPoint(SodiumConfigBuilder::new, modId, modName, modVersion);
            } else {
                Object modProperty = mod.getModProperties().get(ConfigManager.JSON_KEY_SODIUM_CONFIG_INTEGRATIONS);
                if (modProperty == null) {
                    continue;
                }

                if (!(modProperty instanceof String)) {
                    SodiumClientMod.logger().warn("Mod '{}' provided a custom config integration but the value is of the wrong type: {}", modId, modProperty.getClass());
                    continue;
                }

                ConfigManager.registerConfigEntryPoint((String) modProperty, modId, modName, modVersion);
            }
        }
    }
}

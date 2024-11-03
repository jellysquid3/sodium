package net.caffeinemc.mods.sodium.neoforge.config;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPointForge;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.gui.SodiumConfigBuilder;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import org.objectweb.asm.Type;

import java.lang.annotation.ElementType;

/**
 * Written with help from <a href="https://github.com/KingContaria/sodium-fabric/blob/de61e59a369dd8906ddb54050f48c02a29e3f217/neoforge/src/main/java/net/caffeinemc/mods/sodium/neoforge/gui/SodiumConfigIntegrationAPIForge.java">Contaria's implementation of this class</a>.
 */
public class ConfigLoaderForge {
    private static ConfigManager.ModMetadata getModMetadata(String modId) {
        var mod = ModList.get().getModContainerById(modId).orElseThrow(() -> new
                NullPointerException("Mod with id " + modId + " not found in ModList")
        ).getModInfo();
        return new ConfigManager.ModMetadata(mod.getDisplayName(), mod.getVersion().toString());
    }

    public static void collectConfigEntryPoints() {
        ConfigManager.setModInfoFunction(ConfigLoaderForge::getModMetadata);

        // collect entry points from modes that specify it in their properties
        for (IModInfo mod : ModList.get().getMods()) {
            var modId = mod.getModId();

            if (modId.equals("sodium")) {
                ConfigManager.registerConfigEntryPoint(SodiumConfigBuilder::new, modId);
            } else {
                Object modProperty = mod.getModProperties().get(ConfigManager.CONFIG_ENTRY_POINT_KEY);
                if (modProperty == null) {
                    continue;
                }

                if (!(modProperty instanceof String)) {
                    SodiumClientMod.logger().warn("Mod '{}' provided a custom config integration but the value is of the wrong type: {}", modId, modProperty.getClass());
                    continue;
                }

                ConfigManager.registerConfigEntryPoint((String) modProperty, modId);
            }
        }

        // collect entry points from mods that specify it as an annotation
        var entryPointAnnotationType = Type.getType(ConfigEntryPointForge.class);
        for (var scanData : ModList.get().getAllScanData()) {
            for (var annotation : scanData.getAnnotations()) {
                if (annotation.targetType() == ElementType.TYPE && annotation.annotationType().equals(entryPointAnnotationType)) {
                    var className = annotation.clazz().getClassName();
                    var modIdData = annotation.annotationData().get("value");
                    if (modIdData == null) {
                        SodiumClientMod.logger().warn("Class '{}' has a sodium config api entry point annotation but didn't specify which mod it belongs to with the annotation's default parameter.", className);
                        continue;
                    }

                    var modId = modIdData.toString();
                    if (ModList.get().getModContainerById(modId).isEmpty()) {
                        SodiumClientMod.logger().warn("The mod with id '{}' that was provided as the owner of a sodium config api entry point annotation on class '{}' doesn't exist.", modId, className);
                        continue;
                    }

                    ConfigManager.registerConfigEntryPoint(className, modId);
                }
            }
        }
    }
}

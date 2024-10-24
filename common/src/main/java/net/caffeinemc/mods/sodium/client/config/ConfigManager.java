package net.caffeinemc.mods.sodium.client.config;


import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.config.structure.Config;
import net.caffeinemc.mods.sodium.client.config.structure.ConfigBuilderImpl;
import net.caffeinemc.mods.sodium.client.config.structure.ModOptions;
import net.minecraft.CrashReport;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

// TODO: is different handling of long version strings necessary?
// TODO: move classes in impl and interface into reasonable packages and figure out correct visibilities
public class ConfigManager {
    public static final String JSON_KEY_SODIUM_CONFIG_INTEGRATIONS = "sodium:config_api_user";

    private record ConfigUser(Supplier<ConfigEntryPoint> configEntrypoint, String modId, String modName, String modVersion) {
    }
    private static final Collection<ConfigUser> configUsers = new ArrayList<>();

    public static Config CONFIG;

    public static void registerConfigEntryPoint(String className, String modId, String modName, String modVersion) {
        Class<?> entryPointClass;
        try {
            entryPointClass = Class.forName(className);
        } catch (ClassNotFoundException e) {
            SodiumClientMod.logger().warn("Mod '{}' provided a custom config integration but the class is missing: {}", modId, className);
            return;
        }
        if (!ConfigEntryPoint.class.isAssignableFrom(entryPointClass)) {
            SodiumClientMod.logger().warn("Mod '{}' provided a custom config integration but the class is of the wrong type: {}", modId, entryPointClass);
            return;
        }

        registerConfigEntryPoint(() -> {
            try {
                Constructor<?> constructor = entryPointClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                return (ConfigEntryPoint) constructor.newInstance();
            } catch (ReflectiveOperationException e) {
                SodiumClientMod.logger().warn("Mod '{}' provided a custom config integration but the class could not be constructed: {}", modId, entryPointClass);
            }
            return null;
        }, modId, modName, modVersion);
    }

    public static void registerConfigEntryPoint(Supplier<ConfigEntryPoint> entryPoint, String modId, String modName, String modVersion) {
        configUsers.add(new ConfigUser(entryPoint, modId, modName, modVersion));
    }

    public static void registerConfigsEarly() {
        registerConfigs(ConfigEntryPoint::registerConfigEarly);
    }

    public static void registerConfigsLate() {
        registerConfigs(ConfigEntryPoint::registerConfigLate);
    }

    private static void registerConfigs(BiConsumer<ConfigEntryPoint, ConfigBuilder> registerMethod) {
        var namespaces = new ObjectOpenHashSet<>();
        ModOptions sodiumModOptions = null;
        var modConfigs = new ObjectArrayList<ModOptions>();

        for (ConfigUser configUser : configUsers) {
            var entryPoint = configUser.configEntrypoint.get();
            if (entryPoint == null) {
                continue;
            }

            var builder = new ConfigBuilderImpl(configUser.modId, configUser.modName, configUser.modVersion);
            registerMethod.accept(entryPoint, builder);
            Collection<ModOptions> builtConfigs;
            try {
                builtConfigs = builder.build();
            } catch (Exception e) {
                Minecraft.getInstance().emergencySaveAndCrash(new CrashReport("Failed to build config for mod " + configUser.modId, e));
                return;
            }

            for (var modConfig : builtConfigs) {
                var namespace = modConfig.namespace();
                if (namespaces.contains(namespace)) {
                    SodiumClientMod.logger().warn("Mod '{}' provided a duplicate mod id: {}", configUser.modId, namespace);
                    continue;
                }

                namespaces.add(namespace);

                if (namespace.equals("sodium")) {
                    sodiumModOptions = modConfig;
                } else {
                    modConfigs.add(modConfig);
                }
            }
        }

        modConfigs.sort(Comparator.comparing(ModOptions::name));

        if (sodiumModOptions == null) {
            throw new RuntimeException("Sodium mod config not found");
        }
        modConfigs.add(0, sodiumModOptions);

        CONFIG = new Config(ImmutableList.copyOf(modConfigs));
    }
}

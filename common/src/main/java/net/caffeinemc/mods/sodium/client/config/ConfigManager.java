package net.caffeinemc.mods.sodium.client.config;


import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.config.structure.Config;
import net.caffeinemc.mods.sodium.client.config.builder.ConfigBuilderImpl;
import net.caffeinemc.mods.sodium.client.config.structure.ModOptions;
import net.minecraft.CrashReport;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ConfigManager {
    public static final String CONFIG_ENTRY_POINT_KEY = "sodium:config_api_user";

    private record ConfigUser(
            Supplier<ConfigEntryPoint> configEntrypoint,
            String modId) {
    }
    public record ModMetadata(String modName, String modVersion) {
    }

    private static final Collection<ConfigUser> configUsers = new ArrayList<>();

    public static Config CONFIG;
    private static Function<String, ModMetadata> modInfoFunction;

    public static void setModInfoFunction(Function<String, ModMetadata> modInfoFunction) {
        ConfigManager.modInfoFunction = modInfoFunction;
    }

    public static void registerConfigEntryPoint(String className, String modId) {
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
        }, modId);
    }

    public static void registerConfigEntryPoint(Supplier<ConfigEntryPoint> entryPoint, String modId) {
        configUsers.add(new ConfigUser(entryPoint, modId));
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

            var builder = new ConfigBuilderImpl(modInfoFunction, configUser.modId);
            Collection<ModOptions> builtConfigs;
            try {
                registerMethod.accept(entryPoint, builder);
                builtConfigs = builder.build();

                for (var modConfig : builtConfigs) {
                    var namespace = modConfig.namespace();
                    if (namespaces.contains(namespace)) {
                        throw new IllegalArgumentException("Mod '" + configUser.modId + "' provided a duplicate mod id: " + namespace);
                    }

                    namespaces.add(namespace);

                    if (namespace.equals("sodium")) {
                        sodiumModOptions = modConfig;
                    } else {
                        modConfigs.add(modConfig);
                    }
                }
            } catch (Exception e) {
                crashWithMessage("Mod '" + configUser.modId + "' failed while registering config options.", e);
                return;
            }
        }

        modConfigs.sort(Comparator.comparing(ModOptions::name));

        if (sodiumModOptions == null) {
            throw new RuntimeException("Sodium mod config not found");
        }
        modConfigs.add(0, sodiumModOptions);

        try {
            CONFIG = new Config(ImmutableList.copyOf(modConfigs));
        } catch (Exception e) {
            crashWithMessage("Failed to build config options", e);
        }
    }

    private static void crashWithMessage(String message, Exception e) {
        Minecraft.crash(null, Minecraft.getInstance().gameDirectory, new CrashReport(message, e));
    }
}

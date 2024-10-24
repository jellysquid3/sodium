package net.caffeinemc.mods.sodium.api.config;

import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;

public interface ConfigEntryPoint {
    void registerConfigEarly(ConfigBuilder builder);

    void registerConfigLate(ConfigBuilder builder);
}

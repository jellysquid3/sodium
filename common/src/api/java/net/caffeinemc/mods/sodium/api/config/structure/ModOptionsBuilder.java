package net.caffeinemc.mods.sodium.api.config.structure;

public interface ModOptionsBuilder {
    ModOptionsBuilder setName(String name);

    ModOptionsBuilder setVersion(String version);

    ModOptionsBuilder addPage(OptionPageBuilder page);
}

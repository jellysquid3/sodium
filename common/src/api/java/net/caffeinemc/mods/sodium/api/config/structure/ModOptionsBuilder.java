package net.caffeinemc.mods.sodium.api.config.structure;

public interface ModOptionsBuilder {
    ModOptionsBuilder setName(String name);

    ModOptionsBuilder setVersion(String version);

    ModOptionsBuilder setColorThemeRGB(int theme, int themeHighlight, int themeDisabled);

    ModOptionsBuilder setColorThemeRGB(int theme);

    ModOptionsBuilder addPage(OptionPageBuilder page);
}

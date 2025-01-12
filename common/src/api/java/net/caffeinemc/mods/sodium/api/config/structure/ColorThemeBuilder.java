package net.caffeinemc.mods.sodium.api.config.structure;

public interface ColorThemeBuilder {
    ColorThemeBuilder setBaseThemeRGB(int theme);

    ColorThemeBuilder setFullThemeRGB(int theme, int themeHighlight, int themeDisabled);
}

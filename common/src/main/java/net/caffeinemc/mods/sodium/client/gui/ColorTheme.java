package net.caffeinemc.mods.sodium.client.gui;

public class ColorTheme {
    public final int theme;
    public final int themeLighter;
    public final int themeDarker;

    public static final ColorTheme DEFAULT = new ColorTheme(Colors.THEME, Colors.THEME_LIGHTER, Colors.THEME_DARKER);

    public ColorTheme(int theme, int themeLighter, int themeDarker) {
        this.theme = theme;
        this.themeLighter = themeLighter;
        this.themeDarker = themeDarker;
    }
}

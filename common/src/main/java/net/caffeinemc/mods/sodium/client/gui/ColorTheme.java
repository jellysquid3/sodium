package net.caffeinemc.mods.sodium.client.gui;

import java.util.stream.Stream;

public class ColorTheme {
    public final int theme;
    public final int themeLighter;
    public final int themeDarker;

    public static final ColorTheme DEFAULT = new ColorTheme(Colors.THEME, Colors.THEME_LIGHTER, Colors.THEME_DARKER);

    public static final ColorTheme[] PRESETS = Stream.of(
            0xFFE494A5, 0xFFAB94E4, 0xFFCDE494, 0xFFD394E4, 0xFFE4D394
    ).map(ColorTheme::new).toArray(ColorTheme[]::new);

    public ColorTheme(int theme, int themeLighter, int themeDarker) {
        this.theme = theme;
        this.themeLighter = themeLighter;
        this.themeDarker = themeDarker;
    }

    public ColorTheme(int theme) {
        this(theme, Colors.lighten(theme), Colors.darken(theme));
    }
}

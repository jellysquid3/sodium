package net.caffeinemc.mods.sodium.client.gui;

public class ButtonTheme extends ColorTheme {
    public final int bgHighlight;
    public final int bgDefault;
    public final int bgInactive;

    public ButtonTheme(int theme, int themeLighter, int themeDarker, int bgHighlight, int bgDefault, int bgInactive) {
        super(theme, themeLighter, themeDarker);
        this.bgHighlight = bgHighlight;
        this.bgDefault = bgDefault;
        this.bgInactive = bgInactive;
    }

    public ButtonTheme(ColorTheme theme, int bgHighlight, int bgDefault, int bgInactive) {
        this(theme.theme, theme.themeLighter, theme.themeDarker, bgHighlight, bgDefault, bgInactive);
    }
}

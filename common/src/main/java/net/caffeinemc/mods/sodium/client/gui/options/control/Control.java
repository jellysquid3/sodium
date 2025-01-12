package net.caffeinemc.mods.sodium.client.gui.options.control;

import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.gui.ColorTheme;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.client.gui.screens.Screen;

public interface Control {
    Option getOption();

    ControlElement createElement(Screen screen, AbstractOptionList list, Dim2i dim, ColorTheme theme);

    int getMaxWidth();
}

package net.caffeinemc.mods.sodium.api.config.option;

import net.minecraft.network.chat.Component;

public interface ControlValueFormatter {
    Component format(int value);
}

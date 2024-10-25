package net.caffeinemc.mods.sodium.client.config.structure;

import com.google.common.collect.ImmutableList;
import net.caffeinemc.mods.sodium.client.gui.ColorTheme;

public record ModOptions(String namespace, String name, String version, ColorTheme theme, ImmutableList<Page> pages) {
}

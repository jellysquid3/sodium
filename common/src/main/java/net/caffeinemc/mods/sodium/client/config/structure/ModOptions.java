package net.caffeinemc.mods.sodium.client.config.structure;

import com.google.common.collect.ImmutableList;
import net.caffeinemc.mods.sodium.client.gui.ColorTheme;

import java.util.List;

public record ModOptions(String namespace, String name, String version, ColorTheme theme, ImmutableList<Page> pages, List<OptionOverride> overrides) {
}

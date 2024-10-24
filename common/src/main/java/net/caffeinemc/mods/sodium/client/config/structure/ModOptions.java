package net.caffeinemc.mods.sodium.client.config.structure;

import com.google.common.collect.ImmutableList;

public record ModOptions(String namespace, String name, String version, ImmutableList<OptionPage> pages) {
}

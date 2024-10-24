package net.caffeinemc.mods.sodium.client.config.structure;

import com.google.common.collect.ImmutableList;
import net.minecraft.network.chat.Component;

public record OptionPage(Component name, ImmutableList<OptionGroup> groups) {
}

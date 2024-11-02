package net.caffeinemc.mods.sodium.client.config.structure;

import com.google.common.collect.ImmutableList;
import net.minecraft.network.chat.Component;

public record OptionGroup(Component name, ImmutableList<Option> options) {
}

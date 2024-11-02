package net.caffeinemc.mods.sodium.client.config.structure;

import net.minecraft.network.chat.Component;

import java.util.List;

public record OptionGroup(Component name, List<Option> options) {
}

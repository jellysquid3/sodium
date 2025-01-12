package net.caffeinemc.mods.sodium.client.config.structure;

import net.minecraft.resources.ResourceLocation;

public record OptionOverride(ResourceLocation target, String source, Option replacement) {
}

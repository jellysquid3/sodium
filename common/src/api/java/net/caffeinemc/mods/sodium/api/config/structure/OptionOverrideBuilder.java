package net.caffeinemc.mods.sodium.api.config.structure;

import net.minecraft.resources.ResourceLocation;

public interface OptionOverrideBuilder {
    OptionOverrideBuilder setTarget(ResourceLocation target);

    OptionOverrideBuilder setReplacement(OptionBuilder option);
}

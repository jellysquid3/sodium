package net.caffeinemc.mods.sodium.client.config.builder;

import net.caffeinemc.mods.sodium.api.config.structure.OptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionOverrideBuilder;
import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.config.structure.OptionOverride;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.Validate;

public class OptionOverrideBuilderImpl implements OptionOverrideBuilder {
    private ResourceLocation target;
    private Option replacement;

    OptionOverride build(String source) {
        Validate.notNull(this.target, "Target must be set");
        Validate.notNull(this.replacement, "Replacement must be set");

        return new OptionOverride(this.target, source, this.replacement);
    }

    @Override
    public OptionOverrideBuilder setTarget(ResourceLocation target) {
        this.target = target;
        return this;
    }

    @Override
    public OptionOverrideBuilder setReplacement(OptionBuilder option) {
        this.replacement = ((OptionBuilderImpl) option).build();
        return this;
    }
}

package net.caffeinemc.mods.sodium.client.config.structure;

import com.google.common.collect.ImmutableList;
import net.caffeinemc.mods.sodium.api.config.structure.OptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.Validate;

import java.util.ArrayList;
import java.util.List;

class OptionGroupBuilderImpl implements OptionGroupBuilder {
    private Component name;
    private final List<Option<?>> options = new ArrayList<>();

    OptionGroup build() {
        Validate.notEmpty(this.options, "At least one option must be added");

        return new OptionGroup(this.name, ImmutableList.copyOf(this.options));
    }

    @Override
    public OptionGroupBuilder setName(Component name) {
        this.name = name;
        return this;
    }

    @Override
    public OptionGroupBuilder addOption(OptionBuilder<?> option) {
        this.options.add(((OptionBuilderImpl<?>) option).build());
        return this;
    }
}

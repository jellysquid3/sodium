package net.caffeinemc.mods.sodium.client.config.structure;

import com.google.common.collect.ImmutableList;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.Validate;

import java.util.ArrayList;
import java.util.List;

class OptionPageBuilderImpl implements OptionPageBuilder {
    private Component name;
    private final List<OptionGroup> groups = new ArrayList<>();

    OptionPage build() {
        Validate.notNull(this.name, "Name must not be null");
        Validate.notEmpty(this.groups, "At least one group must be added");

        return new OptionPage(this.name, ImmutableList.copyOf(this.groups));
    }

    @Override
    public OptionPageBuilder setName(Component name) {
        this.name = name;
        return this;
    }

    @Override
    public OptionPageBuilder addOptionGroup(OptionGroupBuilder group) {
        this.groups.add(((OptionGroupBuilderImpl) group).build());
        return this;
    }
}

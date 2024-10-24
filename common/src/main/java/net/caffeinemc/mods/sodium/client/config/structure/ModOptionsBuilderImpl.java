package net.caffeinemc.mods.sodium.client.config.structure;

import com.google.common.collect.ImmutableList;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import org.apache.commons.lang3.Validate;

import java.util.ArrayList;
import java.util.List;

class ModOptionsBuilderImpl implements ModOptionsBuilder {
    private final String namespace;
    private String name;
    private String version;
    private final List<OptionPage> pages = new ArrayList<>();

    ModOptionsBuilderImpl(String namespace, String name, String version) {
        this.namespace = namespace;
        this.name = name;
        this.version = version;
    }

    ModOptions build() {
        Validate.notEmpty(this.name, "Name must not be empty");
        Validate.notEmpty(this.version, "Version must not be empty");
        Validate.notEmpty(this.pages, "At least one page must be added");

        return new ModOptions(this.namespace, this.name, this.version, ImmutableList.copyOf(this.pages));
    }

    @Override
    public ModOptionsBuilder setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public ModOptionsBuilder setVersion(String version) {
        this.version = version;
        return this;
    }

    @Override
    public ModOptionsBuilder addPage(OptionPageBuilder builder) {
        this.pages.add(((OptionPageBuilderImpl) builder).build());
        return this;
    }
}

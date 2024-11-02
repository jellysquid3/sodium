package net.caffeinemc.mods.sodium.client.config.structure;

import com.google.common.collect.ImmutableList;
import net.caffeinemc.mods.sodium.api.config.structure.ColorThemeBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionOverrideBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.PageBuilder;
import net.caffeinemc.mods.sodium.client.gui.ColorTheme;
import org.apache.commons.lang3.Validate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

class ModOptionsBuilderImpl implements ModOptionsBuilder {
    private final String namespace;
    private String name;
    private String version;
    private ColorTheme theme;
    private final List<Page> pages = new ArrayList<>();
    private final List<OptionOverride> optionOverrides = new ArrayList<>(0);

    ModOptionsBuilderImpl(String namespace, String name, String version) {
        this.namespace = namespace;
        this.name = name;
        this.version = version;
    }

    ModOptions build() {
        Validate.notEmpty(this.name, "Name must not be empty");
        Validate.notEmpty(this.version, "Version must not be empty");

        if (this.optionOverrides.isEmpty() && this.pages.isEmpty()) {
            throw new IllegalStateException("At least one page or option override must be added");
        }

        if (this.theme == null) {
            this.theme = ColorTheme.PRESETS[Math.abs(this.namespace.hashCode()) % ColorTheme.PRESETS.length];
        }

        return new ModOptions(this.namespace, this.name, this.version, this.theme, ImmutableList.copyOf(this.pages), ImmutableList.copyOf(this.optionOverrides));
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
    public ModOptionsBuilder formatVersion(Function<String, String> versionFormatter) {
        this.version = versionFormatter.apply(this.version);
        return this;
    }

    @Override
    public ModOptionsBuilder setColorTheme(ColorThemeBuilder theme) {
        this.theme = ((ColorThemeBuilderImpl) theme).build();
        return this;
    }

    @Override
    public ModOptionsBuilder addPage(PageBuilder builder) {
        this.pages.add(((PageBuilderImpl) builder).build());
        return this;
    }

    @Override
    public ModOptionsBuilder registerOptionOverride(OptionOverrideBuilder override) {
        this.optionOverrides.add(((OptionOverrideBuilderImpl) override).build(this.namespace));
        return this;
    }
}

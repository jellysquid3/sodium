package net.caffeinemc.mods.sodium.client.config.structure;

import net.caffeinemc.mods.sodium.api.config.structure.*;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ConfigBuilderImpl implements ConfigBuilder {
    private final List<ModOptionsBuilderImpl> pendingModConfigBuilders = new ArrayList<>(1);

    private final String defaultNamespace;
    private final String defaultName;
    private final String defaultVersion;

    public ConfigBuilderImpl(String defaultNamespace, String defaultName, String defaultVersion) {
        this.defaultNamespace = defaultNamespace;
        this.defaultName = defaultName;
        this.defaultVersion = defaultVersion;
    }

    public Collection<ModOptions> build() {
        var configs = new ArrayList<ModOptions>(this.pendingModConfigBuilders.size());
        for (var builder : this.pendingModConfigBuilders) {
            configs.add(builder.build());
        }
        return configs;
    }

    @Override
    public ModOptionsBuilder registerModOptions(String namespace, String name, String version) {
        var builder = new ModOptionsBuilderImpl(namespace, name, version);
        this.pendingModConfigBuilders.add(builder);
        return builder;
    }

    @Override
    public ModOptionsBuilder registerOwnModOptions() {
        return this.registerModOptions(this.defaultNamespace, this.defaultName, this.defaultVersion);
    }

    @Override
    public ColorThemeBuilder createColorTheme() {
        return new ColorThemeBuilderImpl();
    }

    @Override
    public OptionPageBuilder createOptionPage() {
        return new OptionPageBuilderImpl();
    }

    @Override
    public ExternalPageBuilder createExternalPage() {
        return new ExternalPageBuilderImpl();
    }

    @Override
    public OptionGroupBuilder createOptionGroup() {
        return new OptionGroupBuilderImpl();
    }

    @Override
    public BooleanOptionBuilder createBooleanOption(ResourceLocation id) {
        return new BooleanOptionBuilderImpl(id);
    }

    @Override
    public IntegerOptionBuilder createIntegerOption(ResourceLocation id) {
        return new IntegerOptionBuilderImpl(id);
    }

    @Override
    public <E extends Enum<E>> EnumOptionBuilder<E> createEnumOption(ResourceLocation id, Class<E> enumClass) {
        return new EnumOptionBuilderImpl<>(id, enumClass);
    }

    @Override
    public ExternalButtonOptionBuilder createExternalButtonOption(ResourceLocation id) {
        return new ExternalButtonOptionBuilderImpl(id);
    }
}

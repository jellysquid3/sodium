package net.caffeinemc.mods.sodium.client.config.builder;

import net.caffeinemc.mods.sodium.api.config.structure.*;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.config.structure.ModOptions;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public class ConfigBuilderImpl implements ConfigBuilder {
    private final List<ModOptionsBuilderImpl> pendingModConfigBuilders = new ArrayList<>(1);

    private final Function<String, ConfigManager.ModMetadata> modInfoFunction;
    private final String defaultNamespace;

    public ConfigBuilderImpl(Function<String, ConfigManager.ModMetadata> modInfoFunction, String defaultNamespace) {
        this.modInfoFunction = modInfoFunction;
        this.defaultNamespace = defaultNamespace;
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
    public ModOptionsBuilder registerModOptions(String namespace) {
        var metadata = this.modInfoFunction.apply(namespace);
        return this.registerModOptions(namespace, metadata.modName(), metadata.modVersion());
    }

    @Override
    public ModOptionsBuilder registerOwnModOptions() {
        return this.registerModOptions(this.defaultNamespace);
    }

    @Override
    public OptionOverrideBuilder createOptionOverride() {
        return new OptionOverrideBuilderImpl();
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

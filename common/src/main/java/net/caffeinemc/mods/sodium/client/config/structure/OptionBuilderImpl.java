package net.caffeinemc.mods.sodium.client.config.structure;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.caffeinemc.mods.sodium.api.config.ConfigState;
import net.caffeinemc.mods.sodium.api.config.structure.OptionBuilder;
import net.caffeinemc.mods.sodium.client.config.value.ConstantValue;
import net.caffeinemc.mods.sodium.client.config.value.DependentValue;
import net.caffeinemc.mods.sodium.client.config.value.DynamicValue;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.Validate;

import java.util.Collection;
import java.util.function.Function;

public abstract class OptionBuilderImpl implements OptionBuilder {
    final ResourceLocation id;

    Component name;
    DependentValue<Boolean> enabled;

    OptionBuilderImpl(ResourceLocation id) {
        this.id = id;
    }

    abstract Option build();

    void prepareBuild() {
        Validate.notNull(this.name, "Name must be set");

        if (this.enabled == null) {
            this.enabled = new ConstantValue<>(true);
        }
    }

    Collection<ResourceLocation> getDependencies() {
        var dependencies = new ObjectLinkedOpenHashSet<ResourceLocation>();
        dependencies.addAll(this.enabled.getDependencies());
        return dependencies;
    }

    @Override
    public OptionBuilder setName(Component name) {
        Validate.notNull(name, "Argument must not be null");

        this.name = name;
        return this;
    }

    @Override
    public OptionBuilder setEnabled(boolean available) {
        this.enabled = new ConstantValue<>(available);
        return this;
    }

    @Override
    public OptionBuilder setEnabledProvider(Function<ConfigState, Boolean> provider, ResourceLocation... dependencies) {
        Validate.notNull(provider, "Argument must not be null");

        this.enabled = new DynamicValue<>(provider, dependencies);
        return this;
    }
}

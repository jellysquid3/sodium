package net.caffeinemc.mods.sodium.client.config.structure;

import net.caffeinemc.mods.sodium.api.config.*;
import net.caffeinemc.mods.sodium.api.config.structure.BooleanOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.option.OptionBinding;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

class BooleanOptionBuilderImpl extends OptionBuilderImpl<Boolean> implements BooleanOptionBuilder {
    BooleanOptionBuilderImpl(ResourceLocation id) {
        super(id);
    }

    @Override
    BooleanOption build() {
        this.prepareBuild();
        return new BooleanOption(this.id, this.getDependencies(), this.name, this.storage, this.tooltipProvider, this.impact, this.flags, this.defaultValue, this.enabled, this.binding);
    }

    @Override
    public BooleanOptionBuilder setName(Component name) {
        super.setName(name);
        return this;
    }

    @Override
    public BooleanOptionBuilder setStorageHandler(StorageEventHandler storage) {
        super.setStorageHandler(storage);
        return this;
    }

    @Override
    public BooleanOptionBuilder setTooltip(Component tooltip) {
        super.setTooltip(tooltip);
        return this;
    }

    @Override
    public BooleanOptionBuilder setTooltip(Function<Boolean, Component> tooltip) {
        super.setTooltip(tooltip);
        return this;
    }

    @Override
    public BooleanOptionBuilder setImpact(OptionImpact impact) {
        super.setImpact(impact);
        return this;
    }

    @Override
    public BooleanOptionBuilder setFlags(OptionFlag... flags) {
        super.setFlags(flags);
        return this;
    }

    @Override
    public BooleanOptionBuilder setDefaultValue(Boolean value) {
        super.setDefaultValue(value);
        return this;
    }

    @Override
    public BooleanOptionBuilder setDefaultProvider(Function<ConfigState, Boolean> provider, ResourceLocation... dependencies) {
        super.setDefaultProvider(provider, dependencies);
        return this;
    }

    @Override
    public BooleanOptionBuilder setEnabled(boolean available) {
        super.setEnabled(available);
        return this;
    }

    @Override
    public BooleanOptionBuilder setEnabledProvider(Function<ConfigState, Boolean> provider, ResourceLocation... dependencies) {
        super.setEnabledProvider(provider, dependencies);
        return this;
    }

    @Override
    public BooleanOptionBuilder setBinding(Consumer<Boolean> save, Supplier<Boolean> load) {
        super.setBinding(save, load);
        return this;
    }

    @Override
    public BooleanOptionBuilder setBinding(OptionBinding<Boolean> binding) {
        super.setBinding(binding);
        return this;
    }
}

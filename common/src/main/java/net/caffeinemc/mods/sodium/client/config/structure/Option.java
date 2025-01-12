package net.caffeinemc.mods.sodium.client.config.structure;

import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.client.config.search.*;
import net.caffeinemc.mods.sodium.client.config.value.DependentValue;
import net.caffeinemc.mods.sodium.client.gui.options.control.Control;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.EnumSet;
import java.util.function.Consumer;

public abstract class Option {
    final ResourceLocation id;
    final Collection<ResourceLocation> dependencies;

    final Component name;
    final DependentValue<Boolean> enabled;

    Config state;
    Control control;

    Option(ResourceLocation id, Collection<ResourceLocation> dependencies, Component name, DependentValue<Boolean> enabled) {
        if (dependencies.contains(id)) {
            throw new IllegalArgumentException("Option cannot depend on itself");
        }

        this.id = id;
        this.dependencies = dependencies;

        this.name = name;
        this.enabled = enabled;
    }

    abstract Control createControl();

    public Control getControl() {
        if (this.control == null) {
            this.control = this.createControl();
        }
        return this.control;
    }

    void setParentConfig(Config state) {
        this.state = state;
    }

    void visitDependentValues(Consumer<DependentValue<?>> visitor) {
        visitor.accept(this.enabled);
    }

    void loadValueInitial() {
        // no-op
    }

    void resetFromBinding() {
        // no-op
    }

    public boolean isEnabled() {
        return this.enabled.get(this.state);
    }

    public boolean hasChanged() {
        return false;
    }

    boolean applyChanges() {
        return false;
    }

    public Component getName() {
        return this.name;
    }

    public OptionImpact getImpact() {
        return null;
    }

    public abstract Component getTooltip();

    public Collection<OptionFlag> getFlags() {
        return EnumSet.noneOf(OptionFlag.class);
    }

    public void registerTextSources(SearchIndex index, ModOptions modOptions, OptionPage page, OptionGroup optionGroup) {
        index.register(new OptionNameSource(modOptions, page, optionGroup));
    }

    public class OptionNameSource extends TextSource {
        private final ModOptions modOptions;
        private final OptionPage page;
        private final OptionGroup optionGroup;

        OptionNameSource(ModOptions modOptions, OptionPage page, OptionGroup optionGroup) {
            this.modOptions = modOptions;
            this.page = page;
            this.optionGroup = optionGroup;
        }

        public ModOptions getModOptions() {
            return this.modOptions;
        }

        public OptionPage getPage() {
            return this.page;
        }

        public OptionGroup getOptionGroup() {
            return this.optionGroup;
        }

        public Option getOption() {
            return Option.this;
        }

        @Override
        protected String getTextFromSource() {
            return Option.this.getName().getString();
        }
    }
}


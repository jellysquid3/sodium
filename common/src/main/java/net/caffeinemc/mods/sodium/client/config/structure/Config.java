package net.caffeinemc.mods.sodium.client.config.structure;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2ReferenceLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.caffeinemc.mods.sodium.api.config.ConfigState;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;

// TODO: notify storage handlers after update is completed
public class Config implements ConfigState {
    private final Map<ResourceLocation, Option<?>> options = new Object2ReferenceLinkedOpenHashMap<>();
    private final ObjectOpenHashSet<StorageEventHandler> pendingStorageHandlers = new ObjectOpenHashSet<>();
    private final ImmutableList<ModOptions> modOptions;

    public Config(ImmutableList<ModOptions> modOptions) {
        this.modOptions = modOptions;

        this.validateDependencyGraph();

        // load options initially from their bindings
        resetAllOptions();
    }

    private void validateDependencyGraph() {
        for (var modConfig : this.modOptions) {
            for (var page : modConfig.pages()) {
                for (var group : page.groups()) {
                    for (var option : group.options()) {
                        this.options.put(option.id, option);
                        option.setParentConfig(this);
                    }
                }
            }
        }

        for (var option : this.options.values()) {
            for (var dependency : option.dependencies) {
                if (!this.options.containsKey(dependency)) {
                    throw new IllegalArgumentException("Option " + option.id + " depends on non-existent option " + dependency);
                }
            }
        }

        // make sure there are no cycles
        var stack = new ObjectOpenHashSet<ResourceLocation>();
        var finished = new ObjectOpenHashSet<ResourceLocation>();
        for (var option : this.options.values()) {
            this.checkDependencyCycles(option, stack, finished);
        }
    }

    private void checkDependencyCycles(Option<?> option, ObjectOpenHashSet<ResourceLocation> stack, ObjectOpenHashSet<ResourceLocation> finished) {
        if (!stack.add(option.id)) {
            throw new IllegalArgumentException("Cycle detected in dependency graph starting from option " + option.id);
        }

        for (var dependency : option.dependencies) {
            if (finished.contains(dependency)) {
                continue;
            }
            this.checkDependencyCycles(this.options.get(dependency), stack, finished);
        }

        stack.remove(option.id);
        finished.add(option.id);
    }

    public void resetAllOptions() {
        for (var option : this.options.values()) {
            option.resetFromBinding();
        }
    }

    public Collection<OptionFlag> applyAllOptions() {
        var flags = EnumSet.noneOf(OptionFlag.class);

        for (var option : this.options.values()) {
            if (option.applyChanges()) {
                flags.addAll(option.getFlags());
            }
        }

        this.flushStorageHandlers();

        return flags;
    }

    public boolean anyOptionChanged() {
        for (var option : this.options.values()) {
            if (option.hasChanged()) {
                return true;
            }
        }

        return false;
    }

    void notifyStorageWrite(StorageEventHandler handler) {
        this.pendingStorageHandlers.add(handler);
    }

    void flushStorageHandlers() {
        for (var handler : this.pendingStorageHandlers) {
            handler.afterSave();
        }
        this.pendingStorageHandlers.clear();
    }

    public ImmutableList<ModOptions> getModOptions() {
        return this.modOptions;
    }

    @Override
    public boolean readBooleanOption(ResourceLocation id) {
        var option = this.options.get(id);
        if (option instanceof BooleanOption booleanOption) {
            return booleanOption.getValidatedValue();
        }

        throw new IllegalArgumentException("Can't read boolean value from option with id " + id);
    }

    @Override
    public int readIntOption(ResourceLocation id) {
        var option = this.options.get(id);
        if (option instanceof IntegerOption intOption) {
            return intOption.getValidatedValue();
        }

        throw new IllegalArgumentException("Can't read int value from option with id " + id);
    }

    @Override
    public <E extends Enum<E>> E readEnumOption(ResourceLocation id, Class<E> enumClass) {
        var option = this.options.get(id);
        if (option instanceof EnumOption<?> enumOption) {
            if (enumOption.enumClass != enumClass) {
                throw new IllegalArgumentException("Enum class mismatch for option with id " + id + ": requested " + enumClass + ", option has " + enumOption.enumClass);
            }

            return enumClass.cast(enumOption.getValidatedValue());
        }

        throw new IllegalArgumentException("Can't read enum value from option with id " + id);
    }
}

package net.caffeinemc.mods.sodium.client.config.structure;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2ReferenceLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.caffeinemc.mods.sodium.api.config.ConfigState;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.client.console.Console;
import net.caffeinemc.mods.sodium.client.console.message.MessageLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;

public class Config implements ConfigState {
    private final Map<ResourceLocation, Option> options = new Object2ReferenceLinkedOpenHashMap<>();
    private final ObjectOpenHashSet<StorageEventHandler> pendingStorageHandlers = new ObjectOpenHashSet<>();
    private final ImmutableList<ModOptions> modOptions;

    public Config(ImmutableList<ModOptions> modOptions) {
        this.modOptions = modOptions;

        this.collectOptions();
        this.applyOverrides();
        this.validateDependencies();

        // load options initially from their bindings
        resetAllOptions();
    }

    private void collectOptions() {
        for (var modConfig : this.modOptions) {
            for (var page : modConfig.pages()) {
                for (var group : page.groups()) {
                    for (var option : group.options()) {
                        if (!option.id.getNamespace().equals(modConfig.namespace())) {
                            throw new IllegalArgumentException("Namespace of option id '" + option.id + "' does not match the namespace '" + modConfig.namespace() + "' of the enclosing mod config");
                        }

                        this.options.put(option.id, option);
                        option.setParentConfig(this);
                    }
                }
            }
        }
    }

    private void applyOverrides() {
        // collect overrides and validate them
        var overrides = new Object2ReferenceOpenHashMap<ResourceLocation, OptionOverride>();
        for (var modConfig : this.modOptions) {
            for (var override : modConfig.overrides()) {
                if (override.target().getNamespace().equals(modConfig.namespace())) {
                    throw new IllegalArgumentException("Override by mod '" + modConfig.namespace() + "' targets its own option '" + override.target() + "'");
                }

                if (overrides.put(override.target(), override) != null) {
                    throw new IllegalArgumentException("Multiple overrides for option '" + override.target() + "'");
                }
            }
        }

        // apply overrides
        for (var modConfig : this.modOptions) {
            for (var page : modConfig.pages()) {
                for (var group : page.groups()) {
                    var options = group.options();
                    for (int i = 0; i < options.size(); i++) {
                        var option = options.get(i);
                        var override = overrides.get(option.id);
                        if (override != null) {
                            var replacement = override.replacement();
                            options.set(i, replacement);
                            this.options.remove(option.id);
                            this.options.put(replacement.id, replacement);
                            replacement.setParentConfig(this);
                            option.setParentConfig(null);
                        }
                    }
                }
            }
        }
    }

    private void validateDependencies() {
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


    private void checkDependencyCycles(Option option, ObjectOpenHashSet<ResourceLocation> stack, ObjectOpenHashSet<ResourceLocation> finished) {
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

    public void applyAllOptions() {
        var flags = EnumSet.noneOf(OptionFlag.class);

        for (var option : this.options.values()) {
            if (option.applyChanges()) {
                flags.addAll(option.getFlags());
            }
        }

        this.flushStorageHandlers();

        processFlags(flags);
    }

    public void applyOption(ResourceLocation id) {
        var flags = EnumSet.noneOf(OptionFlag.class);

        var option = this.options.get(id);
        if (option != null && option.applyChanges()) {
            flags.addAll(option.getFlags());
        }

        this.flushStorageHandlers();

        processFlags(flags);
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

    public Option getOption(ResourceLocation id) {
        return this.options.get(id);
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

    private static void processFlags(Collection<OptionFlag> flags) {
        Minecraft client = Minecraft.getInstance();

        if (client.level != null) {
            if (flags.contains(OptionFlag.REQUIRES_RENDERER_RELOAD)) {
                client.levelRenderer.allChanged();
            } else if (flags.contains(OptionFlag.REQUIRES_RENDERER_UPDATE)) {
                client.levelRenderer.needsUpdate();
            }
        }

        if (flags.contains(OptionFlag.REQUIRES_ASSET_RELOAD)) {
            client.updateMaxMipLevel(client.options.mipmapLevels().get());
            client.delayTextureReload();
        }

        if (flags.contains(OptionFlag.REQUIRES_VIDEOMODE_RELOAD)) {
            client.getWindow().changeFullscreenVideoMode();
        }

        if (flags.contains(OptionFlag.REQUIRES_GAME_RESTART)) {
            Console.instance().logMessage(MessageLevel.WARN,
                    "sodium.console.game_restart", true, 10.0);
        }
    }
}

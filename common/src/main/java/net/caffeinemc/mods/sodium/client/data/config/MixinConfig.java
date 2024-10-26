package net.caffeinemc.mods.sodium.client.data.config;

import net.caffeinemc.mods.sodium.client.services.PlatformMixinOverrides;
import net.caffeinemc.mods.sodium.client.services.Services;
import net.caffeinemc.mods.sodium.mixin.MixinOption;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * <a href="https://github.com/CaffeineMC/sodium/wiki/Configuration-File">Documentation of these options...</a>
 */
@SuppressWarnings("CanBeFinal")
public class MixinConfig {
    protected static final Logger LOGGER = LogManager.getLogger("SodiumConfig");

    protected static final String JSON_KEY_SODIUM_OPTIONS = "sodium:options";

    private final Map<String, MixinOption> options = new HashMap<>();

    protected MixinConfig() {
        // Defines the default rules which can be configured by the user or other mods.
        // You must manually add a rule for any new mixins not covered by an existing package rule.
        this.addMixinRule("core", true); // TODO: Don't actually allow the user to disable this

        this.addMixinRule("debug.checks", false);

        this.addMixinRule("features", true);

        this.addMixinRule("features.gui", true);

        this.addMixinRule("features.gui.hooks", true);
        this.addMixinRule("features.gui.hooks.console", true);
        this.addMixinRule("features.gui.hooks.debug", true);
        this.addMixinRule("features.gui.hooks.settings", true);

        this.addMixinRule("features.gui.screen", true);

        this.addMixinRule("features.model", true);

        this.addMixinRule("features.options", true);
        this.addMixinRule("features.options.overlays", true);
        this.addMixinRule("features.options.render_layers", true);
        this.addMixinRule("features.options.weather", true);

        this.addMixinRule("features.render", true);

        this.addMixinRule("features.render.compositing", true);

        this.addMixinRule("features.render.entity", true);
        this.addMixinRule("features.render.entity.cull", true);
        this.addMixinRule("features.render.entity.shadow", true);

        this.addMixinRule("features.render.frapi", true);

        this.addMixinRule("features.render.gui", true);
        this.addMixinRule("features.render.gui.font", true);
        this.addMixinRule("features.render.gui.outlines", true);

        this.addMixinRule("features.render.immediate", true);
        this.addMixinRule("features.render.immediate.buffer_builder", true);
        this.addMixinRule("features.render.immediate.matrix_stack", true);

        this.addMixinRule("features.render.model", true);
        this.addMixinRule("features.render.model.block", true);
        this.addMixinRule("features.render.model.item", true);

        this.addMixinRule("features.render.particle", true);

        this.addMixinRule("features.render.world", true);
        this.addMixinRule("features.render.world.clouds", true);
        this.addMixinRule("features.render.world.sky", true);

        this.addMixinRule("features.shader", true);
        this.addMixinRule("features.shader.uniform", true);

        this.addMixinRule("features.textures", true);
        this.addMixinRule("features.textures.animations", true);
        this.addMixinRule("features.textures.mipmaps", true);

        this.addMixinRule("features.world", true);
        this.addMixinRule("features.world.biome", true);

        this.addMixinRule("workarounds", true);
        this.addMixinRule("workarounds.context_creation", true);
        this.addMixinRule("workarounds.event_loop", true);
    }

    /**
     * Defines a Mixin rule which can be configured by users and other mods.
     * @throws IllegalStateException If a rule with that name already exists
     * @param mixin The name of the mixin package which will be controlled by this rule
     * @param enabled True if the rule will be enabled by default, otherwise false
     */
    private void addMixinRule(String mixin, boolean enabled) {
        String name = getMixinRuleName(mixin);

        if (this.options.putIfAbsent(name, new MixinOption(name, enabled, false)) != null) {
            throw new IllegalStateException("Mixin rule already defined: " + mixin);
        }
    }

    private void readProperties(Properties props) {
        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();

            MixinOption option = this.options.get(key);

            if (option == null) {
                LOGGER.warn("No configuration key exists with name '{}', ignoring", key);
                continue;
            }

            boolean enabled;

            if (value.equalsIgnoreCase("true")) {
                enabled = true;
            } else if (value.equalsIgnoreCase("false")) {
                enabled = false;
            } else {
                LOGGER.warn("Invalid value '{}' encountered for configuration key '{}', ignoring", value, key);
                continue;
            }

            option.setEnabled(enabled, true);
        }
    }

    protected void applyModOverride(PlatformMixinOverrides.MixinOverride override) {
        MixinOption option = this.options.get(override.option());

        if (option == null) {
            LOGGER.warn("Mod '{}' attempted to override option '{}', which doesn't exist, ignoring", override.modId(), override.option());
            return;
        }

        // disabling the option takes precedence over enabling
        if (!override.enabled() && option.isEnabled()) {
            option.clearModsDefiningValue();
        }

        if (!override.enabled() || option.isEnabled() || option.getDefiningMods().isEmpty()) {
            option.addModOverride(override.enabled(), override.modId());
        }
    }

    /**
     * Returns the effective option for the specified class name. This traverses the package path of the given mixin
     * and checks each root for configuration rules. If a configuration rule disables a package, all mixins located in
     * that package and its children will be disabled. The effective option is that of the highest-priority rule, either
     * a enable rule at the end of the chain or a disable rule at the earliest point in the chain.
     *
     * @return Null if no options matched the given mixin name, otherwise the effective option for this Mixin
     */
    public MixinOption getEffectiveOptionForMixin(String mixinClassName) {
        int lastSplit = 0;
        int nextSplit;

        MixinOption rule = null;

        while ((nextSplit = mixinClassName.indexOf('.', lastSplit)) != -1) {
            String key = getMixinRuleName(mixinClassName.substring(0, nextSplit));

            MixinOption candidate = this.options.get(key);

            if (candidate != null) {
                rule = candidate;

                if (!rule.isEnabled()) {
                    return rule;
                }
            }

            lastSplit = nextSplit + 1;
        }

        return rule;
    }

    /**
     * Loads the configuration file from the specified location. If it does not exist, a new configuration file will be
     * created. The file on disk will then be updated to include any new options.
     */
    public static MixinConfig load(File file) {
        if (!file.exists()) {
            try {
                writeDefaultConfig(file);
            } catch (IOException e) {
                LOGGER.warn("Could not write default configuration file", e);
            }

            MixinConfig config = new MixinConfig();
            PlatformMixinOverrides.getInstance().applyModOverrides().forEach(config::applyModOverride);

            return config;
        }

        Properties props = new Properties();

        try (FileInputStream fin = new FileInputStream(file)){
            props.load(fin);
        } catch (IOException e) {
            throw new RuntimeException("Could not load config file", e);
        }

        MixinConfig config = new MixinConfig();
        config.readProperties(props);
        PlatformMixinOverrides.getInstance().applyModOverrides().forEach(config::applyModOverride);

        return config;
    }

    private static void writeDefaultConfig(File file) throws IOException {
        File dir = file.getParentFile();

        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("Could not create parent directories");
            }
        } else if (!dir.isDirectory()) {
            throw new IOException("The parent file is not a directory");
        }

        try (Writer writer = new FileWriter(file)) {
            writer.write("# This is the configuration file for Sodium.\n");
            writer.write("#\n");
            writer.write("# You can find information on editing this file and all the available options here:\n");
            writer.write("# https://github.com/CaffeineMC/sodium/wiki/Configuration-File\n");
            writer.write("#\n");
            writer.write("# By default, this file will be empty except for this notice.\n");
        }
    }

    private static String getMixinRuleName(String name) {
        return "mixin." + name;
    }

    public int getOptionCount() {
        return this.options.size();
    }

    public int getOptionOverrideCount() {
        return (int) this.options.values()
                .stream()
                .filter(MixinOption::isOverridden)
                .count();
    }
}

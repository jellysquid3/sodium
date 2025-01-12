package net.caffeinemc.mods.sodium.client;

import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.data.fingerprint.FingerprintMeasure;
import net.caffeinemc.mods.sodium.client.data.fingerprint.HashedFingerprint;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptions;
import net.caffeinemc.mods.sodium.client.console.Console;
import net.caffeinemc.mods.sodium.client.console.message.MessageLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class SodiumClientMod {
    private static SodiumOptions OPTIONS;
    private static final Logger LOGGER = LoggerFactory.getLogger("Sodium");

    private static String MOD_VERSION;

    public static void onInitialization(String version) {
        MOD_VERSION = version;

        OPTIONS = loadConfig();

        ConfigManager.registerConfigsEarly();

        try {
            updateFingerprint();
        } catch (Throwable t) {
            LOGGER.error("Failed to update fingerprint", t);
        }
    }

    public static SodiumOptions options() {
        if (OPTIONS == null) {
            throw new IllegalStateException("Config not yet available");
        }

        return OPTIONS;
    }

    public static Logger logger() {
        if (LOGGER == null) {
            throw new IllegalStateException("Logger not yet available");
        }

        return LOGGER;
    }

    private static SodiumOptions loadConfig() {
        try {
            return SodiumOptions.loadFromDisk();
        } catch (Exception e) {
            LOGGER.error("Failed to load configuration file", e);
            LOGGER.error("Using default configuration file in read-only mode");

            Console.instance().logMessage(MessageLevel.SEVERE, "sodium.console.config_not_loaded", true, 12.5);

            var config = SodiumOptions.defaults();
            config.setReadOnly();

            return config;
        }
    }

    public static void restoreDefaultOptions() {
        OPTIONS = SodiumOptions.defaults();

        try {
            SodiumOptions.writeToDisk(OPTIONS);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write config file", e);
        }
    }

    public static String getVersion() {
        if (MOD_VERSION == null) {
            throw new NullPointerException("Mod version hasn't been populated yet");
        }

        return MOD_VERSION;
    }

    private static void updateFingerprint() {
        var current = FingerprintMeasure.create();

        if (current == null) {
            return;
        }

        HashedFingerprint saved = null;

        try {
            saved = HashedFingerprint.loadFromDisk();
        } catch (Throwable t) {
            LOGGER.error("Failed to load existing fingerprint",  t);
        }

        if (saved == null || !current.looselyMatches(saved)) {
            HashedFingerprint.writeToDisk(current.hashed());

            OPTIONS.notifications.hasSeenDonationPrompt = false;
            OPTIONS.notifications.hasClearedDonationButton = false;

            try {
                SodiumOptions.writeToDisk(OPTIONS);
            } catch (IOException e) {
                LOGGER.error("Failed to update config file", e);
            }
        }
    }
}

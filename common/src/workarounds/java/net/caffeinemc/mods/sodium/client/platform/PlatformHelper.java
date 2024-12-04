package net.caffeinemc.mods.sodium.client.platform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlatformHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger("Sodium-EarlyDriverScanner");

    public static void showCriticalErrorAndClose(
            @Nullable NativeWindowHandle window,
            @NotNull String messageTitle,
            @NotNull String messageBody,
            @NotNull String helpUrl)
    {
        // Always print the information to the log file first, just in case we can't show the message box.
        LOGGER.error(""" 
                ###ERROR_DESCRIPTION###
                
                For more information, please see: ###HELP_URL###"""
                .replace("###ERROR_DESCRIPTION###", messageBody)
                .replace("###HELP_URL###", helpUrl));

        // Try to show a graphical message box (if the platform supports it) and shut down the game.
        MessageBox.showMessageBox(window, MessageBox.IconType.ERROR, messageTitle, messageBody, helpUrl);
        System.exit(1 /* failure code */);
    }
}

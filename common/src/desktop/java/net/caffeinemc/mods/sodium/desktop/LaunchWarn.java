package net.caffeinemc.mods.sodium.desktop;

import net.caffeinemc.mods.sodium.desktop.utils.browse.BrowseUrlHandler;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class LaunchWarn {
    private static final String HELP_URL = "https://link.caffeinemc.net/guides/sodium/installation";

    private static final String RICH_MESSAGE =
                    "<html>" +
                    "<body>" +
                    "<p style='width: 600px; padding: 0 0 8px 0;'>" +
                    "You have tried to launch Sodium (a Minecraft mod) directly, but it is not an executable program or mod installer. Instead, " +
                    "you must install Fabric Loader for Minecraft, and then place this file in your mods directory." +
                    "</p>" +
                    "<p style='width: 600px; padding: 0 0 8px 0;'>" +
                    "If this is your first time installing mods with Fabric Loader, then click the \"Help\" button for an installation guide." +
                    "</p>" +
                    "</body>" +
                    "</html>";

    private static final String FALLBACK_MESSAGE =
                    "<html>" +
                    "<body>" +
                    "<p style='width: 600px; padding: 0 0 8px 0;'>" +
                    "You have tried to launch Sodium (a Minecraft mod) directly, but it is not an executable program or mod installer. Instead, " +
                    "you must install Fabric Loader for Minecraft, and then place this file in your mods directory." +
                    "</p>" +
                    "<p style='width: 600px; padding: 0 0 8px 0;'>" +
                    "If this is your first time installing mods with Fabric Loader, then visit <i>" + HELP_URL + "</i> for an installation guide." +
                    "</p>" +
                    "</body>" +
                    "</html>";

    private static final String FAILED_TO_BROWSE_MESSAGE =
            "<html>" +
                    "<body>" +
                    "<p style='width: 400px; padding: 0 0 8px 0;'>" +
                    "Failed to open the default browser! Your system may be misconfigured. Please open the URL <i>" + HELP_URL + "</i> manually." +
                    "</p>" +
                    "</body>" +
                    "</html>";
    public static final String WINDOW_TITLE = "Sodium";

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            showHeadlessError();
        } else {
            showGraphicalError();
        }
    }

    private static void showHeadlessError() {
        System.err.println(FALLBACK_MESSAGE);
    }

    private static void showGraphicalError() {
        trySetSystemLookAndFeel();
        trySetSystemFontPreferences();

        BrowseUrlHandler browseUrlHandler = BrowseUrlHandler.createImplementation();

        if (browseUrlHandler != null) {
            showRichGraphicalDialog(browseUrlHandler);
        } else {
            showFallbackGraphicalDialog();
        }

        System.exit(0);
    }

    private static void showRichGraphicalDialog(BrowseUrlHandler browseUrlHandler) {
        int selectedOption = showDialogBox(RICH_MESSAGE, WINDOW_TITLE, JOptionPane.YES_NO_OPTION,
                JOptionPane.INFORMATION_MESSAGE, new String[] { "Help", "Close" }, JOptionPane.YES_OPTION);

        if (selectedOption == JOptionPane.YES_OPTION) {
            log("Opening URL: " + HELP_URL);

            try {
                browseUrlHandler.browseTo(HELP_URL);
            } catch (IOException e) {
                log("Failed to open default web browser!", e);

                showDialogBox(FAILED_TO_BROWSE_MESSAGE, WINDOW_TITLE, JOptionPane.DEFAULT_OPTION,
                        JOptionPane.WARNING_MESSAGE, null, JOptionPane.DEFAULT_OPTION);
            }
        }
    }

    private static void showFallbackGraphicalDialog() {
        // Fallback for Linux, etc users with no "default" browser
        showDialogBox(FALLBACK_MESSAGE, WINDOW_TITLE, JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, null);
    }

    private static int showDialogBox(String message,
                                     String title,
                                     int optionType,
                                     int messageType,
                                     String[] options,
                                     Object initialValue) {
        JOptionPane pane = new JOptionPane(message, messageType, optionType, null, options, initialValue);

        JDialog dialog = pane.createDialog(title);
        dialog.setVisible(true);

        Object selectedValue = pane.getValue();

        if (selectedValue == null) {
            return JOptionPane.CLOSED_OPTION;
        }

        // If there is not an array of option buttons:
        if (options == null) {
            if (selectedValue instanceof Integer) {
                return (Integer) selectedValue;
            }

            return JOptionPane.CLOSED_OPTION;
        }

        // If there is an array of option buttons:
        for (int counter = 0; counter < options.length; counter++) {
            String option = options[counter];

            if (option.equals(selectedValue)) {
                return counter;
            }
        }

        return JOptionPane.CLOSED_OPTION;
    }

    private static void trySetSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException ignored) {
            // Ignored
        }
    }

    private static void trySetSystemFontPreferences() {
        System.setProperty("awt.useSystemAAFontSettings", "on"); // Why is this not a default?
    }

    private static void log(String message) {
        System.err.println(message);
    }

    private static void log(String message, Throwable exception) {
        System.err.println(message);
        exception.printStackTrace(System.err);
    }
}

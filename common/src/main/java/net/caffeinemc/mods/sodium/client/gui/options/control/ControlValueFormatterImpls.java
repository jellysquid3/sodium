package net.caffeinemc.mods.sodium.client.gui.options.control;

import com.mojang.blaze3d.platform.Monitor;
import net.caffeinemc.mods.sodium.api.config.option.ControlValueFormatter;
import net.caffeinemc.mods.sodium.client.compatibility.environment.OsUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ControlValueFormatterImpls {
    private ControlValueFormatterImpls() {
    }

    public static ControlValueFormatter guiScale() {
        return (v) -> (v == 0) ? Component.translatable("options.guiScale.auto") : Component.literal(v + "x");
    }

    public static ControlValueFormatter resolution() {
        return (v) -> {
            Monitor monitor = Minecraft.getInstance().getWindow().findBestMonitor();

            if (OsUtils.getOs() != OsUtils.OperatingSystem.WIN || monitor == null) {
                return Component.empty();
            } else if (0 == v) {
                return Component.translatable("options.fullscreen.current");
            } else {
                return Component.literal(monitor.getMode(v - 1).toString().replace(" (24bit)", ""));
            }
        };
    }

    public static ControlValueFormatter fpsLimit() {
        return (v) -> (v == 260) ? Component.translatable("options.framerateLimit.max") : Component.translatable("options.framerate", v);
    }

    public static ControlValueFormatter brightness() {
        return (v) -> {
            if (v == 0) {
                return Component.translatable("options.gamma.min");
            } else if (v == 100) {
                return Component.translatable("options.gamma.max");
            } else {
                return Component.literal(v + "%");
            }
        };
    }

    public static ControlValueFormatter biomeBlend() {
        return (v) -> (v == 0) ? Component.translatable("gui.none") : Component.translatable("sodium.options.biome_blend.value", v);
    }

    public static ControlValueFormatter translateVariable(String key) {
        return (v) -> Component.translatable(key, v);
    }

    public static ControlValueFormatter percentage() {
        return (v) -> Component.literal(v + "%");
    }

    public static ControlValueFormatter multiplier() {
        return (v) -> Component.literal(v + "x");
    }

    public static ControlValueFormatter quantityOrDisabled(String name, String disableText) {
        return (v) -> Component.literal(v == 0 ? disableText : v + " " + name);
    }

    public static ControlValueFormatter number() {
        return (v) -> Component.literal(String.valueOf(v));
    }
}

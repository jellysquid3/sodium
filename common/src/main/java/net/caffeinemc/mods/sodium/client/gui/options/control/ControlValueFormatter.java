package net.caffeinemc.mods.sodium.client.gui.options.control;

import com.mojang.blaze3d.platform.Monitor;
import net.caffeinemc.mods.sodium.client.compatibility.environment.OsUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public interface ControlValueFormatter {
    static ControlValueFormatter guiScale() {
        return (v) -> (v == 0) ? Component.translatable("options.guiScale.auto") : Component.literal(v + "x");
    }

    static ControlValueFormatter resolution() {
        return (v) -> {
            Monitor monitor = Minecraft.getInstance().getWindow().findBestMonitor();

            if (OsUtils.getOs() != OsUtils.OperatingSystem.WIN || monitor == null) {
                return Component.translatable("options.fullscreen.unavailable");
            } else if (0 == v) {
                return Component.translatable("options.fullscreen.current");
            } else {
                return Component.literal(monitor.getMode(v - 1).toString().replace(" (24bit)",""));
            }
        };
    }
    static ControlValueFormatter fpsLimit() {
        return (v) -> (v == 260) ? Component.translatable("options.framerateLimit.max") : Component.translatable("options.framerate", v);
    }

    static ControlValueFormatter brightness() {
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

    static ControlValueFormatter biomeBlend() {
        return (v) -> {
            if (v < 0 || v > 7) return Component.translatable("parsing.int.invalid", v);
            if ( 0 == v) return Component.translatable("gui.none");
            int sv = 2 * v + 1;
            return Component.translatable("sodium.options.biome_blend.value", sv, sv);
        };
    }

    Component format(int value);

    static ControlValueFormatter translateVariable(String key) {
        return (v) -> Component.translatable(key, v);
    }

    static ControlValueFormatter percentage() {
        return (v) -> Component.literal(v + "%");
    }

    static ControlValueFormatter multiplier() {
        return (v) -> Component.literal(v + "x");
    }

    static ControlValueFormatter quantityOrDisabled(String name, String disableText) {
        return (v) -> Component.literal(v == 0 ? disableText : v + " " + name);
    }

    static ControlValueFormatter number() {
        return (v) -> Component.literal(String.valueOf(v));
    }
}

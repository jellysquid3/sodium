package me.jellysquid.mods.sodium.client.gui.options.control;

import net.minecraft.text.TranslatableText;

public interface ControlValueFormatter {
    static ControlValueFormatter guiScale() {
        return (v) -> (v == 0) ? new TranslatableText("options.guiScale.auto").getString() : v + "x";
    }

    static ControlValueFormatter fpsLimit() {
        return (v) -> (v == 260) ? new TranslatableText("options.framerateLimit.max").getString() : new TranslatableText("options.framerate", v).getString();
    }

    static ControlValueFormatter brightness() {
        return (v) -> {
            if (v == 0) {
                return new TranslatableText("options.gamma.min").getString();
            } else if (v == 100) {
                return new TranslatableText("options.gamma.max").getString();
            } else {
                return v + "%";
            }
        };
    }

    static ControlValueFormatter biomeBlend() {
        return (v) -> (v == 0) ? new TranslatableText("gui.none").getString() : new TranslatableText("sodium.options.biome_blend.value", v).getString();
    }

    String format(int value);

    static ControlValueFormatter translateVariable(String key) {
        return (v) -> new TranslatableText(key, v).getString();
    }

    static ControlValueFormatter percentage() {
        return (v) -> v + "%";
    }

    static ControlValueFormatter multiplier() {
        return (v) -> v + "x";
    }

    static ControlValueFormatter number() {
        return String::valueOf;
    }
}

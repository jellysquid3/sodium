package me.jellysquid.mods.sodium.client.gui.options;

import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;

public enum OptionImpact implements TextProvider{
    LOW(Formatting.GREEN, "sodium.option_impact.low"),
    MEDIUM(Formatting.YELLOW, "sodium.option_impact.medium"),
    HIGH(Formatting.GOLD, "sodium.option_impact.high"),
    EXTREME(Formatting.RED, "sodium.option_impact.extreme"),
    VARIES(Formatting.WHITE, "sodium.option_impact.varies");

    private final Formatting color;
    private final String text;

    OptionImpact(Formatting color, String text) {
        this.color = color;
        this.text = text;
    }

    @Override
    public Text getText() {
        return new TranslatableText(this.text).formatted(this.color);
    }
}

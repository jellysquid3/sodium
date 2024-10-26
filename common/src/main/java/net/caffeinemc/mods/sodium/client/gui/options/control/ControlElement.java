package net.caffeinemc.mods.sodium.client.gui.options.control;

import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.gui.Colors;
import net.caffeinemc.mods.sodium.client.gui.widgets.AbstractWidget;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import org.jetbrains.annotations.Nullable;

public class ControlElement<T> extends AbstractWidget {
    protected final Option<T> option;

    public ControlElement(Option<T> option, Dim2i dim) {
        super(dim);
        this.option = option;
    }

    public int getContentWidth() {
        return this.option.getControl().getMaxWidth();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        String name = this.option.getName().getString();

        // add the star suffix before truncation to prevent it from overlapping with the label text
        if (this.option.isEnabled() && this.option.hasChanged()) {
            name = name + " *";
        }

        name = truncateLabelToFit(name);

        String label;
        if (this.option.isEnabled()) {
            if (this.option.hasChanged()) {
                label = ChatFormatting.ITALIC + name;
            } else {
                label = ChatFormatting.WHITE + name;
            }
        } else {
            label = String.valueOf(ChatFormatting.GRAY) + ChatFormatting.STRIKETHROUGH + name;
        }

        this.hovered = this.isMouseOver(mouseX, mouseY);

        this.drawRect(graphics, this.getX(), this.getY(), this.getLimitX(), this.getLimitY(), this.hovered ? 0xE0000000 : 0x90000000);
        this.drawString(graphics, label, this.getX() + 6, this.getCenterY() - 4, Colors.FOREGROUND);

        if (this.isFocused()) {
            this.drawBorder(graphics, this.getX(), this.getY(), this.getLimitX(), this.getLimitY(), -1);
        }
    }

    private String truncateLabelToFit(String name) {
        return truncateTextToFit(name, this.getWidth() - this.getContentWidth() - 20);
    }

    public Option<T> getOption() {
        return this.option;
    }

    @Override
    public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent event) {
        if (!this.option.isEnabled()) {
            return null;
        }
        return super.nextFocusPath(event);
    }
}

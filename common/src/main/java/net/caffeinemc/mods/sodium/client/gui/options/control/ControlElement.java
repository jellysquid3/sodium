package net.caffeinemc.mods.sodium.client.gui.options.control;

import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.gui.widgets.AbstractWidget;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ControlElement<T> extends AbstractWidget {
    protected final Option<T> option;

    protected final Dim2i dim;

    public ControlElement(Option<T> option, Dim2i dim) {
        this.option = option;
        this.dim = dim;
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

        // on focus or hover truncate the label to never overlap with the control's content
        if (this.hovered || this.isFocused()) {
            name = truncateLabelToFit(name);
        }

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

        this.hovered = this.dim.containsCursor(mouseX, mouseY);

        this.drawRect(graphics, this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), this.hovered ? 0xE0000000 : 0x90000000);
        this.drawString(graphics, label, this.dim.x() + 6, this.dim.getCenterY() - 4, 0xFFFFFFFF);

        if (this.isFocused()) {
            this.drawBorder(graphics, this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), -1);
        }
    }

    private @NotNull String truncateLabelToFit(String name) {
        var suffix = "...";
        var suffixWidth = this.font.width(suffix);
        var nameFontWidth = this.font.width(name);
        var targetWidth = this.dim.width() - this.getContentWidth() - 20;
        if (nameFontWidth > targetWidth) {
            targetWidth -= suffixWidth;
            int maxLabelChars = name.length() - 3;
            int minLabelChars = 1;

            // binary search on how many chars fit
            while (maxLabelChars - minLabelChars > 1) {
                var mid = (maxLabelChars + minLabelChars) / 2;
                var midName = name.substring(0, mid);
                var midWidth = this.font.width(midName);
                if (midWidth > targetWidth) {
                    maxLabelChars = mid;
                } else {
                    minLabelChars = mid;
                }
            }

            name = name.substring(0, minLabelChars).trim() + suffix;
        }
        return name;
    }

    public Option<T> getOption() {
        return this.option;
    }

    public Dim2i getDimensions() {
        return this.dim;
    }

    @Override
    public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent event) {
        if (!this.option.isEnabled()) {
            return null;
        }
        return super.nextFocusPath(event);
    }

    @Override
    public @NotNull ScreenRectangle getRectangle() {
        return new ScreenRectangle(this.dim.x(), this.dim.y(), this.dim.width(), this.dim.height());
    }

    @Override
    public boolean isMouseOver(double x, double y) {
        return this.dim.containsCursor(x, y);
    }
}

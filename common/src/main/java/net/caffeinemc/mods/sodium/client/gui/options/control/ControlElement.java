package net.caffeinemc.mods.sodium.client.gui.options.control;

import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.gui.Colors;
import net.caffeinemc.mods.sodium.client.gui.widgets.AbstractWidget;
import net.caffeinemc.mods.sodium.client.gui.widgets.OptionListWidget;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

public abstract class ControlElement extends AbstractWidget {
    protected final OptionListWidget list;

    public ControlElement(OptionListWidget list, Dim2i dim) {
        super(dim);
        this.list = list;
    }

    public abstract Option getOption();

    public int getContentWidth() {
        return this.getOption().getControl().getMaxWidth();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        String name = this.getOption().getName().getString();

        // add the star suffix before truncation to prevent it from overlapping with the label text
        if (this.getOption().isEnabled() && this.getOption().hasChanged()) {
            name = name + " *";
        }

        name = truncateLabelToFit(name);

        String label;
        if (this.getOption().isEnabled()) {
            if (this.getOption().hasChanged()) {
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

    protected MutableComponent formatDisabledControlValue(Component value) {
        return value.copy().withStyle(Style.EMPTY
                .withColor(ChatFormatting.GRAY)
                .withItalic(true));
    }

    private String truncateLabelToFit(String name) {
        return truncateTextToFit(name, this.getWidth() - this.getContentWidth() - 20);
    }


    @Override
    public int getY() {
        return super.getY() - this.list.getScrollAmount();
    }

    @Override
    public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent event) {
        if (!this.getOption().isEnabled()) {
            return null;
        }
        return super.nextFocusPath(event);
    }
}

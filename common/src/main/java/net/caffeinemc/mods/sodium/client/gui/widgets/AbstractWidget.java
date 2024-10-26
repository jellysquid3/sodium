package net.caffeinemc.mods.sodium.client.gui.widgets;

import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractWidget implements Renderable, GuiEventListener, NarratableEntry {
    protected final Font font = Minecraft.getInstance().font;
    private final Dim2i dim;
    protected boolean focused;
    protected boolean hovered;

    protected AbstractWidget(Dim2i dim) {
        this.dim = dim;
    }

    protected void drawString(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.drawString(this.font, text, x, y, color);
    }

    protected void drawString(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.drawString(this.font, text, x, y, color);
    }

    protected void drawCenteredString(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.drawCenteredString(this.font, text, x, y, color);
    }

    public boolean isHovered() {
        return this.hovered;
    }

    protected void drawRect(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        graphics.fill(x1, y1, x2, y2, color);
    }

    protected void playClickSound() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
    }

    public int getX() {
        return this.dim.x();
    }

    public int getY() {
        return this.dim.y();
    }

    public int getWidth() {
        return this.dim.width();
    }

    public int getHeight() {
        return this.dim.height();
    }

    public final int getLimitX() {
        return this.getX() + this.getWidth();
    }

    public final int getLimitY() {
        return this.getY() + this.getHeight();
    }

    public final int getCenterX() {
        return this.getX() + this.getWidth() / 2;
    }

    public final int getCenterY() {
        return this.getY() + this.getHeight() / 2;
    }

    @Override
    public @NotNull ScreenRectangle getRectangle() {
        return new ScreenRectangle(this.getX(), this.getY(), this.getWidth(), this.getHeight());
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.getX() && mouseX < this.getLimitX() && mouseY >= this.getY() && mouseY < this.getLimitY();
    }

    protected int getStringWidth(FormattedText text) {
        return this.font.width(text);
    }

    @Override
    public NarratableEntry.@NotNull NarrationPriority narrationPriority() {
        if (this.focused) {
            return NarratableEntry.NarrationPriority.FOCUSED;
        }
        if (this.hovered) {
            return NarratableEntry.NarrationPriority.HOVERED;
        }
        return NarratableEntry.NarrationPriority.NONE;
    }

    @Override
    public void updateNarration(NarrationElementOutput builder) {
        if (this.focused) {
            builder.add(NarratedElementType.USAGE, Component.translatable("narration.button.usage.focused"));
        } else if (this.hovered) {
            builder.add(NarratedElementType.USAGE, Component.translatable("narration.button.usage.hovered"));
        }
    }

    @Nullable
    public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        return !this.isFocused() ? ComponentPath.leaf(this) : null;
    }

    @Override
    public boolean isFocused() {
        return this.focused;
    }

    @Override
    public void setFocused(boolean focused) {
        if (!focused) {
            this.focused = false;
        } else {
            InputType inputType = Minecraft.getInstance()
                    .getLastInputType();

            if (inputType == InputType.KEYBOARD_TAB || inputType == InputType.KEYBOARD_ARROW) {
                this.focused = true;
            }
        }
    }

    protected String truncateTextToFit(String name, int targetWidth) {
        var suffix = "...";
        var suffixWidth = this.font.width(suffix);
        var nameFontWidth = this.font.width(name);
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

    protected void drawBorder(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        graphics.fill(x1, y1, x2, y1 + 1, color);
        graphics.fill(x1, y2 - 1, x2, y2, color);
        graphics.fill(x1, y1, x1 + 1, y2, color);
        graphics.fill(x2 - 1, y1, x2, y2, color);
    }

    @Override
    public abstract boolean isMouseOver(double x, double y);
}

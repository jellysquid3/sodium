package net.caffeinemc.mods.sodium.client.gui.widgets;

import net.caffeinemc.mods.sodium.client.gui.ButtonTheme;
import net.caffeinemc.mods.sodium.client.gui.ColorTheme;
import net.caffeinemc.mods.sodium.client.gui.Colors;
import net.caffeinemc.mods.sodium.client.gui.Layout;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public abstract class CenteredFlatWidget extends AbstractWidget implements Renderable {
    private final boolean isSelectable;
    private final ButtonTheme theme;

    private boolean selected;
    private boolean enabled = true;
    private boolean visible = true;

    private final Component label;
    private final Component subtitle;

    public CenteredFlatWidget(Dim2i dim, Component label, Component subtitle, boolean isSelectable, ColorTheme theme) {
        super(dim);
        this.label = label;
        this.subtitle = subtitle;
        this.isSelectable = isSelectable;
        this.theme = new ButtonTheme(theme, Colors.BACKGROUND_HIGHLIGHT, Colors.BACKGROUND_DEFAULT, Colors.BACKGROUND_LIGHT);
    }

    public CenteredFlatWidget(Dim2i dim, Component label, boolean isSelectable, ColorTheme theme) {
        this(dim, label, null, isSelectable, theme);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (!this.visible) {
            return;
        }

        this.hovered = this.isMouseOver(mouseX, mouseY);

        int backgroundColor = this.hovered ? this.theme.bgHighlight : (this.selected ? this.theme.bgDefault : this.theme.bgInactive);
        int textColor = this.selected || !this.isSelectable ? this.theme.themeLighter : this.theme.themeDarker;

        int x1 = this.getX();
        int y1 = this.getY();
        int x2 = this.getLimitX();
        int y2 = this.getLimitY();

        if (this.isSelectable) {
            this.drawRect(graphics, x1, y1, x2, y2, backgroundColor);
        }

        if (this.selected) {
            this.drawRect(graphics, x2 - 3, y1, x2, y2, this.theme.themeLighter);
        }

        if (this.subtitle == null) {
            this.drawString(graphics, this.truncateToFitWidth(this.label), x1 + Layout.TEXT_LEFT_PADDING, (int) Math.ceil(((y1 + (this.getHeight() - this.font.lineHeight) * 0.5f))), textColor);
        } else {
            var center = y1 + this.getHeight() * 0.5f;
            this.drawString(graphics, this.truncateToFitWidth(this.label), x1 + Layout.TEXT_LEFT_PADDING, (int) Math.ceil(center - (this.font.lineHeight + Layout.TEXT_LINE_SPACING * 0.5f)), textColor);
            this.drawString(graphics, this.truncateToFitWidth(this.subtitle), x1 + Layout.TEXT_LEFT_PADDING, (int) Math.ceil(center + Layout.TEXT_LINE_SPACING * 0.5f), textColor);
        }

        if (this.enabled && this.isFocused()) {
            this.drawBorder(graphics, x1, y1, x2, y2, Colors.BUTTON_BORDER);
        }
    }

    private String truncateToFitWidth(Component text) {
        return this.truncateTextToFit(text.getString(), this.getWidth() - 14);
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.enabled || !this.visible) {
            return false;
        }

        if (button == 0 && this.isMouseOver(mouseX, mouseY)) {
            doAction();

            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.isFocused())
            return false;

        if (CommonInputs.selected(keyCode)) {
            doAction();
            return true;
        }

        return false;
    }

    abstract void onAction();

    private void doAction() {
        this.onAction();
        this.playClickSound();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent event) {
        if (!this.enabled || !this.visible)
            return null;
        return super.nextFocusPath(event);
    }
}

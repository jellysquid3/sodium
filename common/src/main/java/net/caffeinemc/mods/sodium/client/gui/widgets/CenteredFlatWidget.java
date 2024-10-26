package net.caffeinemc.mods.sodium.client.gui.widgets;

import net.caffeinemc.mods.sodium.client.gui.ButtonTheme;
import net.caffeinemc.mods.sodium.client.gui.ColorTheme;
import net.caffeinemc.mods.sodium.client.gui.Colors;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CenteredFlatWidget extends AbstractWidget implements Renderable {
    private final Runnable action;
    private final boolean isSelectable;
    private final ButtonTheme theme;

    private boolean selected;
    private boolean enabled = true;
    private boolean visible = true;

    private final Component label;

    public CenteredFlatWidget(Dim2i dim, Component label, Runnable action, boolean isSelectable, ColorTheme theme) {
        super(dim);
        this.label = label;
        this.action = action;
        this.isSelectable = isSelectable;
        this.theme = new ButtonTheme(theme, 0x05FFFFFF, 0x90000000, 0x40000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (!this.visible) {
            return;
        }

        this.hovered = this.isMouseOver(mouseX, mouseY);

        int backgroundColor = this.hovered ? this.theme.bgHighlight : (this.selected ? this.theme.bgDefault : this.theme.bgInactive);
        int textColor = this.selected || !this.isSelectable ? this.theme.themeLighter : this.theme.themeDarker;

        var text = this.label.getString();
        text = this.truncateTextToFit(text, this.dim.width() - 16);

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

        this.drawString(graphics, text, x1 + 8, (int) Math.ceil(((y1 + (this.getHeight() - this.font.lineHeight) * 0.5f))), textColor);

        if (this.enabled && this.isFocused()) {
            this.drawBorder(graphics, x1, y1, x2, y2, Colors.FOREGROUND);
        }
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

    private void doAction() {
        this.action.run();
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

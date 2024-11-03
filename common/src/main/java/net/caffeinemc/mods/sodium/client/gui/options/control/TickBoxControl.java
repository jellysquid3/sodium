package net.caffeinemc.mods.sodium.client.gui.options.control;

import net.caffeinemc.mods.sodium.client.config.structure.BooleanOption;
import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.config.structure.StatefulOption;
import net.caffeinemc.mods.sodium.client.gui.ColorTheme;
import net.caffeinemc.mods.sodium.client.gui.Colors;
import net.caffeinemc.mods.sodium.client.gui.widgets.OptionListWidget;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.gui.screens.Screen;

public class TickBoxControl implements Control {
    private final BooleanOption option;

    public TickBoxControl(BooleanOption option) {
        this.option = option;
    }

    @Override
    public ControlElement createElement(Screen screen, OptionListWidget list, Dim2i dim, ColorTheme theme) {
        return new TickBoxControlElement(list, this.option, dim, theme);
    }

    @Override
    public int getMaxWidth() {
        return 30;
    }

    @Override
    public StatefulOption<Boolean> getOption() {
        return this.option;
    }

    private static class TickBoxControlElement extends ControlElement {
        private final BooleanOption option;
        private final ColorTheme theme;

        public TickBoxControlElement(OptionListWidget list, BooleanOption option, Dim2i dim, ColorTheme theme) {
            super(list, dim);

            this.option = option;
            this.theme = theme;
        }

        @Override
        public Option getOption() {
            return this.option;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            super.render(graphics, mouseX, mouseY, delta);

            final int x = this.getLimitX() - 16;
            final int y = this.getCenterY() - 5;
            final int xEnd = x + 10;
            final int yEnd = y + 10;

            final boolean enabled = this.option.isEnabled();
            final boolean ticked = enabled && this.option.getValidatedValue();

            final int color;

            if (enabled) {
                color = ticked ? this.theme.theme : Colors.FOREGROUND;
            } else {
                color = Colors.FOREGROUND_DISABLED;
            }

            if (ticked) {
                this.drawRect(graphics, x + 2, y + 2, xEnd - 2, yEnd - 2, color);
            }

            this.drawBorder(graphics, x, y, xEnd, yEnd, color);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.option.isEnabled() && button == 0 && this.isMouseOver(mouseX, mouseY)) {
                toggleControl();
                this.playClickSound();

                return true;
            }

            return false;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!isFocused()) return false;

            if (CommonInputs.selected(keyCode)) {
                toggleControl();
                this.playClickSound();

                return true;
            }

            return false;
        }

        public void toggleControl() {
            this.option.modifyValue(!this.option.getValidatedValue());
        }
    }
}

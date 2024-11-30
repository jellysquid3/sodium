package net.caffeinemc.mods.sodium.client.gui.options.control;

import net.caffeinemc.mods.sodium.client.config.structure.EnumOption;
import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.gui.ColorTheme;
import net.caffeinemc.mods.sodium.client.gui.Colors;
import net.caffeinemc.mods.sodium.client.gui.widgets.OptionListWidget;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.Validate;

public class CyclingControl<T extends Enum<T>> implements Control {
    private final EnumOption<T> option;

    public CyclingControl(EnumOption<T> option, Class<T> enumType) {
        T[] universe = enumType.getEnumConstants();

        Validate.notEmpty(universe, "The enum universe must contain at least one item");

        this.option = option;
    }

    @Override
    public Option getOption() {
        return this.option;
    }

    @Override
    public ControlElement createElement(Screen screen, AbstractOptionList list, Dim2i dim, ColorTheme theme) {
        return new CyclingControlElement<>(list, this.option, dim);
    }

    @Override
    public int getMaxWidth() {
        return 70;
    }

    private static class CyclingControlElement<T extends Enum<T>> extends ControlElement {
        private final EnumOption<T> option;
        private final T[] baseValues;

        public CyclingControlElement(AbstractOptionList list, EnumOption<T> option, Dim2i dim) {
            super(list, dim);

            this.option = option;
            this.baseValues = option.enumClass.getEnumConstants();
        }

        @Override
        public Option getOption() {
            return this.option;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            super.render(graphics, mouseX, mouseY, delta);

            var value = this.option.getValidatedValue();
            Component name = this.option.getElementName(value);

            int strWidth = this.getStringWidth(name);
            this.drawString(graphics, name, this.getLimitX() - strWidth - 6, this.getCenterY() - 4, Colors.FOREGROUND);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.option.isEnabled() && button == 0 && this.isMouseOver(mouseX, mouseY)) {
                cycleControl(Screen.hasShiftDown());
                return true;
            }

            return false;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!isFocused()) return false;

            if (CommonInputs.selected(keyCode)) {
                cycleControl(Screen.hasShiftDown());
                return true;
            }

            return false;
        }

        private void cycleControl(boolean reverse) {
            this.playClickSound();

            var currentValue = this.option.getValidatedValue();
            int startIndex = 0;
            for (; startIndex < this.baseValues.length; startIndex++) {
                if (this.baseValues[startIndex] == currentValue) {
                    break;
                }
            }

            // step through values in the specified direction until a valid one is found
            var currentIndex = startIndex;
            do {
                if (reverse) {
                    currentIndex = (currentIndex + this.baseValues.length - 1) % this.baseValues.length;
                } else {
                    currentIndex = (currentIndex + 1) % this.baseValues.length;
                }

                currentValue = this.baseValues[currentIndex];
            } while (!this.option.isValueAllowed(currentValue));
            this.option.modifyValue(currentValue);
        }
    }
}

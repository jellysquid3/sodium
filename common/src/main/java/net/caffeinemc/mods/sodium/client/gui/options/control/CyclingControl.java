package net.caffeinemc.mods.sodium.client.gui.options.control;

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

import java.util.function.Function;

public class CyclingControl<T extends Enum<T>> implements Control<T> {
    private final Option<T> option;
    private final T[] allowedValues;
    private final Function<T, Component> elementNameProvider;

    public CyclingControl(Option<T> option, Class<T> enumType, Function<T, Component> elementNameProvider, T[] allowedValues) {
        T[] universe = enumType.getEnumConstants();

        Validate.notEmpty(universe, "The enum universe must contain at least one item");

        this.option = option;
        this.allowedValues = allowedValues;
        this.elementNameProvider = elementNameProvider;
    }

    @Override
    public Option<T> getOption() {
        return this.option;
    }

    @Override
    public ControlElement<T> createElement(OptionListWidget list, Dim2i dim, ColorTheme theme) {
        return new CyclingControlElement<>(list, this.option, dim, this.allowedValues, this.elementNameProvider);
    }

    @Override
    public int getMaxWidth() {
        return 70;
    }

    private static class CyclingControlElement<T extends Enum<T>> extends ControlElement<T> {
        private final T[] allowedValues;
        private final Function<T, Component> elementNameProvider;
        private int currentIndex;

        public CyclingControlElement(OptionListWidget list, Option<T> option, Dim2i dim, T[] allowedValues, Function<T, Component> elementNameProvider) {
            super(list, option, dim);

            this.allowedValues = allowedValues;
            this.elementNameProvider = elementNameProvider;
            this.currentIndex = 0;

            var optionValue = option.getValidatedValue();
            for (int i = 0; i < allowedValues.length; i++) {
                if (allowedValues[i] == optionValue) {
                    this.currentIndex = i;
                    break;
                }
            }
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            super.render(graphics, mouseX, mouseY, delta);

            var value = this.option.getValidatedValue();
            Component name = this.elementNameProvider.apply(value);

            int strWidth = this.getStringWidth(name);
            this.drawString(graphics, name, this.getLimitX() - strWidth - 6, this.getCenterY() - 4, Colors.FOREGROUND);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.option.isEnabled() && button == 0 && this.isMouseOver(mouseX, mouseY)) {
                cycleControl(Screen.hasShiftDown());
                this.playClickSound();

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

        public void cycleControl(boolean reverse) {
            if (reverse) {
                this.currentIndex = (this.currentIndex + this.allowedValues.length - 1) % this.allowedValues.length;
            } else {
                this.currentIndex = (this.currentIndex + 1) % this.allowedValues.length;
            }
            this.option.modifyValue(this.allowedValues[this.currentIndex]);
        }
    }
}

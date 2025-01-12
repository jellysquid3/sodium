package net.caffeinemc.mods.sodium.client.gui.options.control;

import com.mojang.blaze3d.platform.InputConstants;
import net.caffeinemc.mods.sodium.client.config.structure.IntegerOption;
import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.config.structure.StatefulOption;
import net.caffeinemc.mods.sodium.client.gui.ColorTheme;
import net.caffeinemc.mods.sodium.client.gui.Colors;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import org.apache.commons.lang3.Validate;

public class SliderControl implements Control {
    private final IntegerOption option;

    public SliderControl(IntegerOption option, int min, int max, int interval) {
        Validate.isTrue(max > min, "The maximum value must be greater than the minimum value");
        Validate.isTrue(interval > 0, "The slider interval must be greater than zero");
        Validate.isTrue(((max - min) % interval) == 0, "The maximum value must be divisible by the interval");

        this.option = option;
    }

    @Override
    public ControlElement createElement(Screen screen, AbstractOptionList list, Dim2i dim, ColorTheme theme) {
        return new SliderControlElement(list, this.option, dim, theme);
    }

    @Override
    public StatefulOption<Integer> getOption() {
        return this.option;
    }

    @Override
    public int getMaxWidth() {
        throw new UnsupportedOperationException("Not implemented");
    }

    private static class SliderControlElement extends ControlElement {
        private static final int THUMB_WIDTH = 2, TRACK_HEIGHT = 1;

        private final IntegerOption option;
        private final ColorTheme theme;

        private double thumbPosition;
        private boolean sliderHeld;
        private int contentWidth;

        public SliderControlElement(AbstractOptionList list, IntegerOption option, Dim2i dim, ColorTheme theme) {
            super(list, dim);

            this.option = option;
            this.theme = theme;

            this.thumbPosition = this.getThumbPositionForValue(option.getValidatedValue());
            this.sliderHeld = false;
        }

        @Override
        public Option getOption() {
            return this.option;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            int sliderX = this.getSliderX();
            int sliderY = this.getSliderY();
            int sliderWidth = this.getSliderWidth();
            int sliderHeight = this.getSliderHeight();

            var value = this.option.getValidatedValue();
            var isEnabled = this.option.isEnabled();

            var label = this.option.formatValue(value);

            if (!isEnabled) {
                label = this.formatDisabledControlValue(label);
            }

            int labelWidth = this.font.width(label);

            boolean drawSlider = isEnabled && (this.hovered || this.isFocused());
            if (drawSlider) {
                this.contentWidth = sliderWidth + labelWidth;
            } else {
                this.contentWidth = labelWidth;
            }

            // render the label first and then the slider to prevent the highlight rect from darkening the slider
            super.render(graphics, mouseX, mouseY, delta);

            if (drawSlider) {
                this.thumbPosition = this.getThumbPositionForValue(value);

                var range = this.option.getRange();
                double thumbOffset = Mth.clamp((double) (this.getIntValue() - range.min()) / range.getSpread() * sliderWidth, 0, sliderWidth);

                int thumbX = (int) (sliderX + thumbOffset - THUMB_WIDTH);
                int trackY = (int) (sliderY + (sliderHeight / 2f) - ((double) TRACK_HEIGHT / 2));

                this.drawRect(graphics, sliderX, trackY, sliderX + sliderWidth, trackY + TRACK_HEIGHT, this.theme.themeLighter);
                this.drawRect(graphics, thumbX, sliderY, thumbX + (THUMB_WIDTH * 2), sliderY + sliderHeight, Colors.FOREGROUND);

                this.drawString(graphics, label, sliderX - labelWidth - 6, sliderY + (sliderHeight / 2) - 4, Colors.FOREGROUND);
            } else {
                this.drawString(graphics, label, sliderX + sliderWidth - labelWidth, sliderY + (sliderHeight / 2) - 4, Colors.FOREGROUND);
            }
        }

        public int getSliderX() {
            return this.getLimitX() - 96;
        }

        public int getSliderY() {
            return this.getCenterY() - 5;
        }

        public int getSliderWidth() {
            return 90;
        }

        public int getSliderHeight() {
            return 10;
        }

        public boolean isMouseOverSlider(double mouseX, double mouseY) {
            return mouseX >= this.getSliderX() && mouseX < this.getSliderX() + this.getSliderWidth() && mouseY >= this.getSliderY() && mouseY < this.getSliderY() + this.getSliderHeight();
        }

        @Override
        public int getContentWidth() {
            return this.contentWidth;
        }

        public int getIntValue() {
            var range = this.option.getRange();
            return range.min() + (range.step() * (int) Math.round((this.thumbPosition / (1.0D / range.getSpread())) / range.step()));
        }

        public double getThumbPositionForValue(int value) {
            var range = this.option.getRange();
            return (value - range.min()) * (1.0D / range.getSpread());
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            this.sliderHeld = false;

            if (this.option.isEnabled() && button == 0 && this.isMouseOver(mouseX, mouseY)) {
                if (this.isMouseOverSlider(mouseX, mouseY)) {
                    this.setValueFromMouse(mouseX);
                    this.sliderHeld = true;
                }

                return true;
            }

            return false;
        }

        private void setValueFromMouse(double d) {
            this.setValue((d - (double) this.getSliderX()) / (double) this.getSliderWidth());
        }

        public void setValue(double d) {
            this.thumbPosition = Mth.clamp(d, 0.0D, 1.0D);

            int value = this.getIntValue();

            if (this.option.getValidatedValue() != value) {
                this.option.modifyValue(value);
            }
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!isFocused()) return false;

            var range = this.option.getRange();
            if (keyCode == InputConstants.KEY_LEFT) {
                this.option.modifyValue(Mth.clamp(this.option.getValidatedValue() - range.step(), range.max(), range.max()));
                return true;
            } else if (keyCode == InputConstants.KEY_RIGHT) {
                this.option.modifyValue(Mth.clamp(this.option.getValidatedValue() + range.step(), range.min(), range.max()));
                return true;
            }

            return false;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (this.option.isEnabled() && button == 0) {
                if (this.sliderHeld) {
                    this.setValueFromMouse(mouseX);
                }

                return true;
            }

            return false;
        }
    }

}

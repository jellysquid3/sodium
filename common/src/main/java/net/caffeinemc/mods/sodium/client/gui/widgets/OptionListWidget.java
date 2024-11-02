package net.caffeinemc.mods.sodium.client.gui.widgets;

import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.config.structure.OptionGroup;
import net.caffeinemc.mods.sodium.client.config.structure.OptionPage;
import net.caffeinemc.mods.sodium.client.gui.ColorTheme;
import net.caffeinemc.mods.sodium.client.gui.Layout;
import net.caffeinemc.mods.sodium.client.gui.options.control.ControlElement;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class OptionListWidget extends AbstractParentWidget {
    private final OptionPage page;
    private final ColorTheme theme;
    private final List<ControlElement> controls;
    private ScrollbarWidget scrollbar;

    public OptionListWidget(Screen screen, Dim2i dim, OptionPage page, ColorTheme theme) {
        super(dim);
        this.page = page;
        this.theme = theme;
        this.controls = new ArrayList<>();
        this.rebuild(screen);
    }

    private void rebuild(Screen screen) {
        int x = this.getX();
        int y = this.getY();
        int width = this.getWidth();
        int height = this.getHeight();

        int maxWidth = 0;

        this.clearChildren();
        this.scrollbar = this.addRenderableChild(new ScrollbarWidget(new Dim2i(x + width - Layout.SCROLLBAR_WIDTH, y, Layout.SCROLLBAR_WIDTH, height)));

        int entryHeight = 18;
        int listHeight = 0;
        for (OptionGroup group : this.page.groups()) {
            // Add each option's control element
            for (Option option : group.options()) {
                var control = option.getControl();
                var element = control.createElement(screen,this, new Dim2i(x, y + listHeight, width - 10, entryHeight), this.theme);

                this.addRenderableChild(element);
                this.controls.add(element);

                maxWidth = Math.max(maxWidth, element.getContentWidth());

                // Move down to the next option
                listHeight += entryHeight;
            }

            // Add padding beneath each option group
            listHeight += Layout.INNER_MARGIN;
        }

        this.scrollbar.setScrollbarContext(listHeight - Layout.INNER_MARGIN);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.enableScissor(this.getX(), this.getY(), this.getLimitX(), this.getLimitY());
        super.render(graphics, mouseX, mouseY, delta);

//        this.verticalScrollScissorGradient(graphics, this.getLimitY());

        graphics.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.scrollbar.scroll((int) (-verticalAmount * 10));
        return true;
    }

    public List<ControlElement> getControls() {
        return this.controls;
    }

    public int getScrollAmount() {
        return this.scrollbar.getScrollAmount();
    }
}

package net.caffeinemc.mods.sodium.client.gui.options.control;

import net.caffeinemc.mods.sodium.client.gui.widgets.AbstractParentWidget;
import net.caffeinemc.mods.sodium.client.gui.widgets.ScrollbarWidget;
import net.caffeinemc.mods.sodium.client.util.Dim2i;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractOptionList extends AbstractParentWidget {
    protected final List<ControlElement> controls = new ArrayList<>();
    protected ScrollbarWidget scrollbar;

    protected AbstractOptionList(Dim2i dim) {
        super(dim);
    }

    public List<ControlElement> getControls() {
        return this.controls;
    }

    public int getScrollAmount() {
        return this.scrollbar.getScrollAmount();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.scrollbar.scroll((int) (-verticalAmount * 10));
        return true;
    }
}

package net.caffeinemc.mods.sodium.client.gui.widgets;

import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractParentWidget extends AbstractWidget implements ContainerEventHandler {
    private final List<GuiEventListener> children = new ArrayList<>();
    private final List<Renderable> renderableChildren = new ArrayList<>();

    private GuiEventListener focusedElement;
    private boolean dragging;

    protected AbstractParentWidget(Dim2i dim) {
        super(dim);
    }

    protected <T extends GuiEventListener> T addChild(T element) {
        this.children.add(element);
        return element;
    }

    protected <T extends GuiEventListener & Renderable> T addRenderableChild(T element) {
        this.children.add(element);
        this.renderableChildren.add(element);
        return element;
    }

    protected void removeChild(GuiEventListener element) {
        this.children.remove(element);
        this.renderableChildren.remove(element);
    }

    protected void clearChildren() {
        this.children.clear();
        this.renderableChildren.clear();
    }

    protected void verticalScrollScissorGradient(GuiGraphics graphics, int y) {
        var gradientHeight = 10;
        graphics.fillGradient(this.getX(), y - gradientHeight, this.getLimitX(), y, 0xFFFF0000, 0xFF00FF00);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        for (Renderable element : this.renderableChildren) {
            element.render(graphics, mouseX, mouseY, delta);
        }
    }

    @Override
    public @NotNull List<? extends GuiEventListener> children() {
        return this.children;
    }

    @Override
    public boolean isDragging() {
        return this.dragging;
    }

    @Override
    public void setDragging(boolean dragging) {
        this.dragging = dragging;
    }

    @Nullable
    @Override
    public GuiEventListener getFocused() {
        return this.focusedElement;
    }

    @Override
    public void setFocused(@Nullable GuiEventListener guiEventListener) {
        this.focusedElement = guiEventListener;
    }

    @Override
    public @Nullable ComponentPath nextFocusPath(@NotNull FocusNavigationEvent event) {
        return ContainerEventHandler.super.nextFocusPath(event);
    }
}

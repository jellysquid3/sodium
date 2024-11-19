package net.caffeinemc.mods.sodium.client.gui.widgets;

import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import org.jetbrains.annotations.NotNull;

// TODO: override some methods to make it not focusable?
public class ScrollbarWidget extends AbstractWidget {
    private static final int COLOR = ColorABGR.pack(50, 50, 50, 150);
    private static final int HIGHLIGHT_COLOR = ColorABGR.pack(100, 100, 100, 150);

    private final boolean horizontal;

    private int visible;
    private int total;

    private int scrollAmount;
    private long lastScrollTime;
    private boolean dragging;

    public ScrollbarWidget(Dim2i dim2i) {
        this(dim2i, false);
    }

    public ScrollbarWidget(Dim2i dim2i, boolean horizontal) {
        super(dim2i);
        this.horizontal = horizontal;
    }

    public void setScrollbarContext(int visible, int total) {
        this.visible = visible;
        this.total = total;
        this.scrollAmount = Math.max(0, Math.min(total - visible, this.scrollAmount));
    }

    public void setScrollbarContext(int total) {
        this.setScrollbarContext(this.horizontal ? this.getWidth() : this.getHeight(), total);
    }

    public boolean canScroll() {
        return this.total > this.visible;
    }

    public void scroll(int amount) {
        this.scrollAmount = Math.max(0, Math.min(this.total - this.visible, this.scrollAmount + amount));
        this.lastScrollTime = System.currentTimeMillis();
    }

    public int getScrollAmount() {
        return this.scrollAmount;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (!this.canScroll()) {
            return;
        }
        boolean isMouseOver = this.isMouseOver(mouseX, mouseY);
        if (isMouseOver) {
            this.lastScrollTime = Math.max(this.lastScrollTime, System.currentTimeMillis() - 500);
        }
        long time = System.currentTimeMillis();
        long scrollTimeDiff = time - this.lastScrollTime;
        if (isMouseOver || this.dragging || scrollTimeDiff < 1000) {
            graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), COLOR);
            int x1, y1, x2, y2;
            if (this.horizontal) {
                x1 = this.getX() + this.getHighlightStart(this.getWidth());
                y1 = this.getY();
                x2 = x1 + this.getHighlightLength(this.getWidth());
                y2 = y1 + this.getHeight();
            } else {
                x1 = this.getX();
                y1 = this.getY() + this.getHighlightStart(this.getHeight());
                x2 = x1 + this.getWidth();
                y2 = y1 + this.getHighlightLength(this.getHeight());
            }
            graphics.fill(x1, y1, x2, y2, HIGHLIGHT_COLOR);
        }
    }

    private boolean isMouseOverHighlight(double mouseX, double mouseY) {
        int x1, y1, x2, y2;
        if (this.horizontal) {
            x1 = this.getX() + this.getHighlightStart(this.getWidth());
            y1 = this.getY();
            x2 = x1 + this.getHighlightLength(this.getWidth());
            y2 = y1 + this.getHeight();
        } else {
            x1 = this.getX();
            y1 = this.getY() + this.getHighlightStart(this.getHeight());
            x2 = x1 + this.getWidth();
            y2 = y1 + this.getHighlightLength(this.getHeight());
        }
        return mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2;
    }

    private int getHighlightStart(int length) {
        return (int) Math.round(((double) this.scrollAmount / this.total) * length);
    }

    private int getHighlightLength(int length) {
        return (int) Math.round(((double) this.visible / this.total) * length);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isMouseOver(mouseX, mouseY) || !this.canScroll()) {
            return false;
        }
        if (this.isMouseOverHighlight(mouseX, mouseY)) {
            this.dragging = true;
        } else {
            if (this.horizontal) {
                this.scroll(mouseX > this.getHighlightStart(this.getWidth()) ? this.getWidth() : -this.getWidth());
            } else {
                this.scroll(mouseY > this.getHighlightStart(this.getHeight()) ? this.getHeight() : -this.getHeight());
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.dragging = false;
        this.lastScrollTime = Math.max(this.lastScrollTime, System.currentTimeMillis() - 500);
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.dragging) {
            this.scroll((int) Math.round(this.horizontal ? deltaX : deltaY * ((double) this.total / this.visible)));
            return true;
        }
        return false;
    }

    @Override
    public void updateNarration(NarrationElementOutput builder) {
    }
}

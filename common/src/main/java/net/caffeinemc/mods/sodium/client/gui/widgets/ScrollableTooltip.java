package net.caffeinemc.mods.sodium.client.gui.widgets;

import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.client.gui.Colors;
import net.caffeinemc.mods.sodium.client.gui.Layout;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.caffeinemc.mods.sodium.client.gui.options.control.ControlElement;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.List;

// TODO: is narration of the tooltip already handled by the screen or is there no narration at all?
public class ScrollableTooltip {
    private static final ResourceLocation ARROW_TEXTURE = ResourceLocation.fromNamespaceAndPath("sodium", "textures/gui/tooltip_arrows.png");
    private static final int ARROW_WIDTH = 5;
    private static final int SPRITE_WIDTH = 10;
    private static final int ARROW_HEIGHT = 9;

    private static final int MIN_TOOLTIP_WIDTH = 20;
    private static final int MAX_TOOLTIP_WIDTH = 200;
    private static final int TEXT_HORIZONTAL_PADDING = Layout.INNER_MARGIN - 1;
    private static final int TEXT_VERTICAL_PADDING = TEXT_HORIZONTAL_PADDING;
    private static final int INNER_BOX_MARGIN = ARROW_WIDTH; // arrow includes one pixel of margin
    private static final int OUTER_BOX_MARGIN = 3;
    private static final int BOTTOM_BOX_MARGIN = OUTER_BOX_MARGIN;
    private static final int UPPER_BOX_MARGIN = Layout.BUTTON_SHORT + Layout.INNER_MARGIN * 2;

    private final Font font = Minecraft.getInstance().font;
    private ControlElement hoveredElement;
    private ScrollbarWidget scrollbar;
    private Vector2i contentSize;
    private Dim2i visibleDim;
    private boolean needsScrolling;
    private final List<FormattedCharSequence> content = new ArrayList<>();
    private final VideoSettingsScreen parent;

    public ScrollableTooltip(VideoSettingsScreen parent) {
        this.parent = parent;
    }

    public void onControlHover(ControlElement hovered, int mouseX, int mouseY) {
        if (this.hoveredElement == hovered) {
            return;
        }

        if (hovered != null) {
            this.hoveredElement = hovered;

            if (this.scrollbar != null) {
                this.parent.removeWidget(this.scrollbar);
                this.scrollbar = null;
            }

            this.updateTooltip();

            if (this.needsScrolling) {
                this.scrollbar = this.parent.addRenderableWidget(new ScrollbarWidget(new Dim2i(
                        this.visibleDim.getLimitX() - Layout.SCROLLBAR_WIDTH,
                        this.visibleDim.y(),
                        Layout.SCROLLBAR_WIDTH,
                        this.visibleDim.height()
                )));
                this.scrollbar.setScrollbarContext(this.visibleDim.height(), this.contentSize.y());
            }
        } else {
            this.updateTooltip();

            // handle the space between options and their tooltip
            if ((mouseX < this.hoveredElement.getLimitX() || mouseX >= this.visibleDim.x() ||
                    mouseY < this.hoveredElement.getY() || mouseY >= this.hoveredElement.getLimitY()) &&
                    !this.visibleDim.containsCursor(mouseX, mouseY)) {
                this.hoveredElement = null;

                if (this.scrollbar != null) {
                    this.parent.removeWidget(this.scrollbar);
                    this.scrollbar = null;
                }
            }
        }
    }

    private int getLineHeight() {
        return this.font.lineHeight + Layout.TEXT_LINE_SPACING;
    }

    private void updateTooltip() {
        var option = this.hoveredElement.getOption();

        int boxWidth = Mth.clamp(this.parent.width - this.hoveredElement.getLimitX() - INNER_BOX_MARGIN - OUTER_BOX_MARGIN,
                MIN_TOOLTIP_WIDTH, MAX_TOOLTIP_WIDTH);
        var textWidth = boxWidth - TEXT_HORIZONTAL_PADDING * 2;
        int boxY = this.hoveredElement.getY();
        int boxX = this.hoveredElement.getLimitX() + INNER_BOX_MARGIN;

        this.content.clear();
        this.content.addAll(this.font.split(option.getTooltip(), textWidth));
        OptionImpact impact = option.getImpact();

        if (impact != null) {
            var impactText = Component.translatable("sodium.options.performance_impact_string", impact.getName());
            this.content.addAll(this.font.split(impactText.withStyle(ChatFormatting.GRAY), textWidth));
        }

        int contentHeight = this.content.size() * this.getLineHeight() - Layout.TEXT_LINE_SPACING + TEXT_VERTICAL_PADDING * 2;
        int boxYLimit = boxY + contentHeight;
        int boxYCutoff = this.parent.height - BOTTOM_BOX_MARGIN;

        // If the box is going to be cut off on the Y-axis, move it back up the difference
        if (boxYLimit > boxYCutoff) {
            boxY -= boxYLimit - boxYCutoff;
        }

        // prevent it from moving up further than the tooltip safe area
        if (boxY < UPPER_BOX_MARGIN) {
            boxY = UPPER_BOX_MARGIN;
        }

        this.contentSize = new Vector2i(boxWidth, contentHeight);

        var visibleMaxHeight = this.parent.height - UPPER_BOX_MARGIN - BOTTOM_BOX_MARGIN;
        var visibleHeight = Math.min(contentHeight, visibleMaxHeight);
        this.visibleDim = new Dim2i(boxX, boxY, boxWidth, visibleHeight);

        this.needsScrolling = contentHeight > visibleMaxHeight;
    }

    public void render(@NotNull GuiGraphics graphics) {
        if (this.hoveredElement == null) {
            return;
        }

        // draw small triangular arrow attached to the side of the tooltip box pointing at the hovered element, in the margin between the hovered element and the tooltip box
        int arrowX = this.visibleDim.x() - ARROW_WIDTH;
        int arrowY = this.hoveredElement.getCenterY() - (ARROW_HEIGHT / 2);

        // parameters are: render type, sprite, x, y, u offset, v offset, render width, render height, u size, v size, color
        graphics.blit(RenderType::guiTextured, ARROW_TEXTURE, arrowX, arrowY, ARROW_WIDTH, 0, ARROW_WIDTH, ARROW_HEIGHT, SPRITE_WIDTH, ARROW_HEIGHT, Colors.BACKGROUND_LIGHT);
        graphics.blit(RenderType::guiTextured, ARROW_TEXTURE, arrowX, arrowY, 0, 0, ARROW_WIDTH, ARROW_HEIGHT, SPRITE_WIDTH, ARROW_HEIGHT, Colors.BACKGROUND_DEFAULT);

        int lineHeight = this.getLineHeight();

        int scrollAmount = 0;
        if (this.scrollbar != null) {
            scrollAmount = this.scrollbar.getScrollAmount();
        }

        graphics.enableScissor(this.visibleDim.x(), this.visibleDim.y(), this.visibleDim.getLimitX(), this.visibleDim.getLimitY());
        graphics.fill(this.visibleDim.x(), this.visibleDim.y(), this.visibleDim.getLimitX(), this.visibleDim.getLimitY(), Colors.BACKGROUND_LIGHT);
        for (int i = 0; i < this.content.size(); i++) {
            graphics.drawString(this.font, this.content.get(i),
                    this.visibleDim.x() + TEXT_HORIZONTAL_PADDING, this.visibleDim.y() + TEXT_VERTICAL_PADDING + (i * lineHeight) - scrollAmount,
                    Colors.FOREGROUND);
        }
        graphics.disableScissor();
    }

    public boolean mouseScrolled(double d, double e, double amount) {
        if (this.visibleDim != null && this.visibleDim.containsCursor(d, e) && this.scrollbar != null) {
            this.scrollbar.scroll((int) (-amount * 10));
            return true;
        }
        return false;
    }
}

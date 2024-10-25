package net.caffeinemc.mods.sodium.client.gui.widgets;

import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.config.structure.OptionPage;
import net.caffeinemc.mods.sodium.client.gui.ColorTheme;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class PageListWidget extends AbstractParentWidget {
    private final VideoSettingsScreen parent;
    private final Dim2i dim;
    private ScrollbarWidget scrollbar;
    private FlatButtonWidget search;

    public PageListWidget(VideoSettingsScreen parent, Dim2i dim) {
        this.parent = parent;
        this.dim = dim;
        this.rebuild();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(this.dim.x(), this.dim.y(), this.dim.width(), this.dim.height(), 0x40000000, 0x90000000);
        graphics.enableScissor(this.dim.x(), this.dim.y(), this.dim.x() + this.dim.width(), this.dim.y() + this.dim.height() - 30);
        super.render(graphics, mouseX, mouseY, delta);
        graphics.disableScissor();
        this.search.render(graphics, mouseX, mouseY, delta);
    }

    public void rebuild() {
        int x = this.dim.x();
        int y = this.dim.y();
        int width = this.dim.width();
        int height = this.dim.height();

        this.clearChildren();
        this.scrollbar = this.addRenderableChild(new ScrollbarWidget(new Dim2i(x + width - 5, y, 5, height - 30)));
        this.search = this.addChild(new FlatButtonWidget(new Dim2i(x, y + height - 30, width, 20), Component.literal("Search...").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY), () -> {
            // TODO: implement search
        }, true, true));

        int listHeight = 5;
        int entryHeight = this.font.lineHeight * 2;
        for (var modOptions : ConfigManager.CONFIG.getModOptions()) {
            var theme = modOptions.theme();

            CenteredFlatWidget header = new EntryWidget(new Dim2i(x, y + listHeight, width, entryHeight), Component.literal(modOptions.name()), () -> {
            }, false, theme);

            listHeight += entryHeight;

            this.addRenderableChild(header);

            for (OptionPage page : modOptions.pages()) {
                CenteredFlatWidget button = new EntryWidget(new Dim2i(x, y + listHeight, width, entryHeight), page.name(), () -> this.parent.setPage(modOptions, page), true, theme);
                button.setSelected(this.parent.getPage() == page);

                listHeight += entryHeight;

                this.addRenderableChild(button);
            }
        }

        this.scrollbar.setScrollbarContext(height - 30, listHeight + 5);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.scrollbar.scroll((int) (-verticalAmount * 10));
        return true;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.dim.containsCursor(mouseX, mouseY);
    }

    public class EntryWidget extends CenteredFlatWidget {
        public EntryWidget(Dim2i dim, Component label, Runnable action, boolean isSelectable, ColorTheme theme) {
            super(dim, label, action, isSelectable, theme);
        }

        @Override
        public int getY() {
            return super.getY() - PageListWidget.this.scrollbar.getScrollAmount();
        }

        @Override
        public int getLimitY() {
            return super.getLimitY() - PageListWidget.this.scrollbar.getScrollAmount();
        }
    }
}

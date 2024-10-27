package net.caffeinemc.mods.sodium.client.gui.widgets;

import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.config.structure.ExternalPage;
import net.caffeinemc.mods.sodium.client.config.structure.ModOptions;
import net.caffeinemc.mods.sodium.client.config.structure.OptionPage;
import net.caffeinemc.mods.sodium.client.config.structure.Page;
import net.caffeinemc.mods.sodium.client.gui.ColorTheme;
import net.caffeinemc.mods.sodium.client.gui.Layout;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class PageListWidget extends AbstractParentWidget {
    private final VideoSettingsScreen parent;
    private CenteredFlatWidget selected;
    private ScrollbarWidget scrollbar;
    private FlatButtonWidget search;

    public PageListWidget(VideoSettingsScreen parent, Dim2i dim) {
        super(dim);
        this.parent = parent;
        this.rebuild();
    }

    public void rebuild() {
        int x = this.getX();
        int y = this.getY();
        int width = this.getWidth();
        int height = this.getHeight();

        this.clearChildren();
        this.scrollbar = this.addRenderableChild(new ScrollbarWidget(new Dim2i(x + width - Layout.SCROLLBAR_WIDTH, y, Layout.SCROLLBAR_WIDTH, height - Layout.BUTTON_SHORT_BOTTOM_Y)));
        this.search = this.addChild(new FlatButtonWidget(new Dim2i(x, y + height - Layout.BUTTON_SHORT_BOTTOM_Y, width, Layout.BUTTON_SHORT), Component.literal("Search...").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY), () -> {
            // TODO: implement search
        }, true, true));

        int entryHeight = this.font.lineHeight * 2;
        var headerHeight = this.font.lineHeight * 3;
        int listHeight = Layout.BUTTON_SHORT + Layout.INNER_MARGIN * 2 - headerHeight;
        for (var modOptions : ConfigManager.CONFIG.getModOptions()) {
            var theme = modOptions.theme();

            CenteredFlatWidget header = new HeaderEntryWidget(new Dim2i(x, y + listHeight, width, headerHeight), modOptions, theme);

            listHeight += headerHeight;

            this.addRenderableChild(header);

            for (Page page : modOptions.pages()) {
                CenteredFlatWidget button;
                if (page instanceof OptionPage optionPage) {
                    button = new PageEntryWidget(new Dim2i(x, y + listHeight, width, entryHeight), optionPage, modOptions, theme);
                    if (this.parent.getPage() == page) {
                        this.switchSelected(button);
                    }
                } else if (page instanceof ExternalPage externalPage) {
                    button = new ExternalPageEntryWidget(new Dim2i(x, y + listHeight, width, entryHeight), externalPage, theme);
                } else {
                    throw new IllegalStateException("Unknown page type: " + page.getClass());
                }

                listHeight += entryHeight;

                this.addRenderableChild(button);
            }
        }

        this.scrollbar.setScrollbarContext(listHeight + Layout.INNER_MARGIN);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(this.getX(), this.getY(), this.getLimitX(), this.getLimitY(), 0x40000000, 0x90000000);
        var scissorEnd = this.getLimitY() - Layout.BUTTON_SHORT_BOTTOM_Y;
        graphics.enableScissor(this.getX(), this.getY(), this.getLimitX(), scissorEnd);
        super.render(graphics, mouseX, mouseY, delta);
        graphics.disableScissor();

//        this.verticalScrollScissorGradient(graphics, scissorEnd);

        this.search.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.scrollbar.scroll((int) (-verticalAmount * 10));
        return true;
    }

    private void switchSelected(CenteredFlatWidget widget) {
        if (this.selected != null) {
            this.selected.setSelected(false);
        }
        this.selected = widget;
        this.selected.setSelected(true);
    }

    private class EntryWidget extends CenteredFlatWidget {
        EntryWidget(Dim2i dim, Component label, boolean isSelectable, ColorTheme theme) {
            super(dim, label, isSelectable, theme);
        }

        EntryWidget(Dim2i dim, Component label, Component subtitle, boolean isSelectable, ColorTheme theme) {
            super(dim, label, subtitle, isSelectable, theme);
        }

        @Override
        void onAction() {
        }

        @Override
        public int getY() {
            return super.getY() - PageListWidget.this.scrollbar.getScrollAmount();
        }
    }

    private class HeaderEntryWidget extends EntryWidget {
        HeaderEntryWidget(Dim2i dim, ModOptions modOptions, ColorTheme theme) {
            super(dim, Component.literal(modOptions.name()), Component.literal(modOptions.version()), false, theme);
        }
    }

    private class PageEntryWidget extends EntryWidget {
        private final OptionPage page;
        private final ModOptions modOptions;

        PageEntryWidget(Dim2i dim, OptionPage page, ModOptions modOptions, ColorTheme theme) {
            super(dim, page.name(), true, theme);
            this.page = page;
            this.modOptions = modOptions;
        }

        @Override
        void onAction() {
            PageListWidget.this.switchSelected(this);
            PageListWidget.this.parent.setPage(this.modOptions, this.page);
        }
    }

    private class ExternalPageEntryWidget extends EntryWidget {
        private final ExternalPage page;

        ExternalPageEntryWidget(Dim2i dim, ExternalPage page, ColorTheme theme) {
            super(dim, page.name(), true, theme);
            this.page = page;
        }

        @Override
        void onAction() {
            this.page.currentScreenConsumer().accept(PageListWidget.this.parent);
        }
    }
}

package net.caffeinemc.mods.sodium.client.gui.widgets;

import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.config.search.SearchQuerySession;
import net.caffeinemc.mods.sodium.client.config.structure.ModOptions;
import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.config.structure.OptionPage;
import net.caffeinemc.mods.sodium.client.gui.ColorTheme;
import net.caffeinemc.mods.sodium.client.gui.Layout;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.caffeinemc.mods.sodium.client.gui.options.control.AbstractOptionList;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class SearchWidget extends AbstractOptionList {
    // maximum distance from its original position that a search result can be moved to improve grouping
    private static final int MAX_ORDER_DIST_ERROR = 2;

    private final VideoSettingsScreen parent;
    private final Runnable closeSearch;
    private final SearchQuerySession searchQuerySession;
    private String query = "";
    private List<Option.OptionNameSource> searchResults = List.of();

    private EditBox searchBox;
    private FlatButtonWidget closeButton;

    public SearchWidget(VideoSettingsScreen parent, Runnable closeSearch, @Nullable SearchWidget old, Dim2i dim) {
        super(dim);
        this.closeSearch = closeSearch;
        this.parent = parent;
        this.searchQuerySession = ConfigManager.CONFIG.startSearchQuery();

        if (old != null) {
            this.query = old.query;
        }

        this.search();
    }

    private void rebuild() {
        int x = this.getX();
        int y = this.getY();
        int width = this.getWidth();
        int height = this.getHeight();

        if (this.closeButton == null) {
            this.closeButton = new FlatButtonWidget(new Dim2i(Layout.PAGE_LIST_WIDTH - Layout.BUTTON_SHORT, Layout.INNER_MARGIN, Layout.BUTTON_SHORT, Layout.BUTTON_SHORT), Component.literal("x"), this.closeSearch, false, false, false);
        }

        if (this.searchBox == null) {
            this.searchBox = new EditBox(this.font, x + Layout.INNER_MARGIN, y + Layout.INNER_MARGIN + Layout.BUTTON_SHORT / 2 - this.font.lineHeight / 2, Layout.PAGE_LIST_WIDTH - Layout.BUTTON_SHORT - Layout.INNER_MARGIN * 2, this.font.lineHeight, Component.empty());

            this.searchBox.setMaxLength(50);
            this.searchBox.setBordered(false);
            this.searchBox.setResponder(this::triggerSearch);
        }

        // populate with initial query if it's copying from an old search
        if (!this.searchBox.getValue().equals(this.query)) {
            this.searchBox.setValue(this.query);
        }

        this.setFocused(this.searchBox);

        var headerHeight = this.searchBox.getBottom() + Layout.INNER_MARGIN;
        if (this.scrollbar == null) {
            this.scrollbar = this.addRenderableChild(new ScrollbarWidget(new Dim2i(width + Layout.OPTION_LIST_SCROLLBAR_OFFSET, headerHeight, Layout.SCROLLBAR_WIDTH, height - headerHeight)));
        }

        this.clearChildren();
        this.controls.clear();

        // only add child and not renderable child for scrollable elements to prevent them from getting clipped
        this.addChild(this.closeButton);
        this.addChild(this.searchBox);
        this.addChild(this.scrollbar);

        // present the search results
        var optionBaseY = y + headerHeight + Layout.INNER_MARGIN;
        var optionX = x + Layout.PAGE_LIST_WIDTH + Layout.INNER_MARGIN;
        var entryHeight = this.font.lineHeight * 2;
        var listHeight = 0;
        var pageListHeight = 0;
        PageListEntryWidget lastPageItem = null;
        HeaderEntryWidget lastHeader = null;
        var trailingSpace = true;

        Option.OptionNameSource lastSource = null;
        for (var source : this.searchResults) {
            var option = source.getOption();
            var control = option.getControl();
            var modOptions = source.getModOptions();
            var page = source.getPage();

            // add header elements if necessary
            if (lastSource == null || lastSource.getPage() != page) {
                if (lastSource != null && !trailingSpace) {
                    listHeight += Layout.INNER_MARGIN;
                }

                pageListHeight = listHeight = Math.max(pageListHeight, listHeight);

                if (lastSource == null || lastSource.getModOptions() != modOptions) {
                    var header = new HeaderEntryWidget(new Dim2i(x, optionBaseY + pageListHeight, Layout.PAGE_LIST_WIDTH, entryHeight), entryHeight, modOptions, modOptions.theme());
                    this.addRenderableChild(header);
                    lastHeader = header;

                    pageListHeight += entryHeight;
                    listHeight += entryHeight;
                }

                var pageEntry = new PageEntryWidget(new Dim2i(x, optionBaseY + pageListHeight, Layout.PAGE_LIST_WIDTH, entryHeight * 2), entryHeight, page, modOptions, modOptions.theme());
                this.addRenderableChild(pageEntry);
                lastPageItem = pageEntry;

                pageListHeight += entryHeight;
            }

            var element = control.createElement(this.parent, this, new Dim2i(optionX, optionBaseY + listHeight, Layout.OPTION_WIDTH, entryHeight), modOptions.theme());

            this.addRenderableChild(element);
            this.controls.add(element);

            listHeight += entryHeight;
            trailingSpace = false;

            lastPageItem.setLimitY(optionBaseY + listHeight);
            lastHeader.setLimitY(optionBaseY + listHeight);

            if (lastSource != null && lastSource.getOptionGroup() != source.getOptionGroup()) {
                listHeight += Layout.INNER_MARGIN;
                trailingSpace = true;
            }

            lastSource = source;
        }

        this.scrollbar.setScrollbarContext(listHeight + Layout.INNER_MARGIN);
    }

    private void setPage(ModOptions modOptions, OptionPage page) {
        this.closeSearch.run();
        this.parent.setPage(modOptions, page);
    }

    private abstract static class TopCenteredFlatWidget extends CenteredFlatWidget {
        private final int textBoxHeight;
        private int height;

        public TopCenteredFlatWidget(Dim2i dim, int textBoxHeight, Component label, boolean isSelectable, ColorTheme theme) {
            super(dim, label, isSelectable, theme);
            this.textBoxHeight = textBoxHeight;
        }

        @Override
        protected int getTextBoxHeight() {
            return this.textBoxHeight;
        }

        @Override
        public int getHeight() {
            return this.height;
        }

        public void setLimitY(int limitY) {
            this.height = limitY - this.getY();
        }
    }

    private class PageListEntryWidget extends TopCenteredFlatWidget {
        PageListEntryWidget(Dim2i dim, int textBoxHeight, Component label, boolean isSelectable, ColorTheme theme) {
            super(dim, textBoxHeight, label, isSelectable, theme);
        }

        @Override
        void onAction() {
        }

        @Override
        public int getY() {
            return super.getY() - SearchWidget.this.getScrollAmount();
        }
    }

    private class HeaderEntryWidget extends PageListEntryWidget {
        HeaderEntryWidget(Dim2i dim, int textBoxHeight, ModOptions modOptions, ColorTheme theme) {
            super(dim, textBoxHeight, Component.literal(modOptions.name()), false, theme);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            // render only the part of the page background gradient that fits within the header area
            graphics.enableScissor(this.getX(), this.getY(), this.getLimitX(), this.getLimitY());
            PageListWidget.renderBackgroundGradient(graphics, this.getX(), this.getY(), this.getLimitX(), this.getLimitY());
            graphics.disableScissor();

            super.render(graphics, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false;
        }
    }

    private class PageEntryWidget extends PageListEntryWidget {
        private final OptionPage page;
        private final ModOptions modOptions;

        PageEntryWidget(Dim2i dim, int textBoxHeight, OptionPage page, ModOptions modOptions, ColorTheme theme) {
            super(dim, textBoxHeight, page.name(), true, theme);
            this.page = page;
            this.modOptions = modOptions;
        }

        @Override
        void onAction() {
            SearchWidget.this.setPage(this.modOptions, this.page);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(this.getX(), this.getY() + Layout.INNER_MARGIN, Layout.PAGE_LIST_WIDTH, Layout.INNER_MARGIN + Layout.BUTTON_SHORT, FlatButtonWidget.DEFAULT_THEME.bgDefault);

        this.closeButton.render(graphics, mouseX, mouseY, delta);
        this.searchBox.render(graphics, mouseX, mouseY, delta);
        this.scrollbar.render(graphics, mouseX, mouseY, delta);

        // render only the controls within the scissor area and not the other gui elements
        graphics.enableScissor(this.getX(), this.searchBox.getBottom() + Layout.INNER_MARGIN, this.getLimitX() + Layout.OPTION_LIST_SCROLLBAR_OFFSET + Layout.SCROLLBAR_WIDTH, this.getLimitY());
        super.render(graphics, mouseX, mouseY, delta);
        graphics.disableScissor();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.closeSearch.run();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void triggerSearch(String text) {
        if (text.equals(this.query)) {
            return;
        }

        this.query = text.stripLeading();
        this.search();
    }

    @SuppressWarnings("unchecked") // we manually check the elements
    private void search() {
        var results = this.searchQuerySession.getSearchResults(this.query);

        // assert assumption of the result type
        var length = results.size();
        for (int i = 0; i < length; i++) {
            var result = results.get(i);
            result.setResultIndex(i);

            if (!(result instanceof Option.OptionNameSource)) {
                throw new UnsupportedOperationException("Unsupported search text source type: " + result.getClass().getName());
            }
        }

        this.searchResults = (List<Option.OptionNameSource>) results;

        this.improveGrouping();

        this.rebuild();
    }

    void improveGrouping() {
        // move search results around a little to group them better
        var length = this.searchResults.size();
        for (int i = 1; i < length - 1; i++) {
            // if the next result would fit better to the previous one than this one, swap current and next
            var prev = this.searchResults.get(i - 1);
            var curr = this.searchResults.get(i);
            var next = this.searchResults.get(i + 1);

            // check that switching current and next doesn't introduce too much of an ordering error
            if (Math.abs(i - prev.getResultIndex()) > MAX_ORDER_DIST_ERROR ||
                    Math.abs(i + 1 - next.getResultIndex()) > MAX_ORDER_DIST_ERROR) {
                continue;
            }

            var prevCurrScore = this.getGroupScore(prev, curr);
            var prevNextScore = this.getGroupScore(prev, next);

            if (prevNextScore > prevCurrScore) {
                this.searchResults.set(i, next);
                this.searchResults.set(i + 1, curr);
            }
        }
    }

    private int getGroupScore(Option.OptionNameSource a, Option.OptionNameSource b) {
        if (a.getModOptions() != b.getModOptions()) {
            return 0;
        }
        if (a.getPage() != b.getPage()) {
            return 1;
        }
        if (a.getOptionGroup() != b.getOptionGroup()) {
            return 2;
        }
        return 3;
    }
}

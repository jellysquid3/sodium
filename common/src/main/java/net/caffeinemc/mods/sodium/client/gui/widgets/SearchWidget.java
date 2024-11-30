package net.caffeinemc.mods.sodium.client.gui.widgets;

import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.config.search.SearchQuerySession;
import net.caffeinemc.mods.sodium.client.config.search.TextSource;
import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.gui.Layout;
import net.caffeinemc.mods.sodium.client.gui.options.control.AbstractOptionList;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.Collection;
import java.util.List;

public class SearchWidget extends AbstractOptionList {
    private final Runnable closeSearch;
    private final SearchQuerySession searchQuerySession;
    private Collection<TextSource> searchResults = List.of();

    private EditBox searchBox;
    private FlatButtonWidget closeButton;

    public SearchWidget(Runnable closeSearch, Dim2i dim) {
        super(dim);
        this.closeSearch = closeSearch;
        this.searchQuerySession = ConfigManager.CONFIG.startSearchQuery();

        this.rebuild();
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
            this.searchBox.setFocused(true);
            this.searchBox.setBordered(false);
            this.searchBox.setResponder(this::search);
        }

        var headerHeight = this.searchBox.getBottom() + Layout.INNER_MARGIN;
        if (this.scrollbar == null) {
            this.scrollbar = this.addRenderableChild(new ScrollbarWidget(new Dim2i(width - Layout.SCROLLBAR_WIDTH - Layout.OPTION_LIST_SCROLLBAR_OFFSET, headerHeight, Layout.SCROLLBAR_WIDTH, height - headerHeight)));
        }

        this.clearChildren();

        this.addRenderableChild(this.closeButton);
        this.addRenderableChild(this.searchBox);
        this.addRenderableChild(this.scrollbar);

        // present the search results
        for (var result : this.searchResults) {
            if (!(result instanceof Option.OptionNameSource)) {
                continue;
            }

            var optionSource = (Option.OptionNameSource) result;
        }
    }

    private void search(String text) {
        this.searchResults = this.searchQuerySession.getSearchResults(text);
        this.rebuild();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(this.getX(), this.getY() + Layout.INNER_MARGIN, Layout.PAGE_LIST_WIDTH, Layout.INNER_MARGIN + Layout.BUTTON_SHORT, FlatButtonWidget.DEFAULT_THEME.bgDefault);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.closeSearch.run();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

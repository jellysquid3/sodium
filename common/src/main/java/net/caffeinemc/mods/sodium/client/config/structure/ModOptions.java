package net.caffeinemc.mods.sodium.client.config.structure;

import com.google.common.collect.ImmutableList;
import net.caffeinemc.mods.sodium.client.config.search.SearchIndex;
import net.caffeinemc.mods.sodium.client.config.search.Searchable;
import net.caffeinemc.mods.sodium.client.gui.ColorTheme;

import java.util.List;

public record ModOptions(String namespace, String name, String version, ColorTheme theme, ImmutableList<Page> pages, List<OptionOverride> overrides) implements Searchable {

    @Override
    public void registerTextSources(SearchIndex index) {
        for (Page page : this.pages) {
            page.registerTextSources(index, this);
        }
    }
}

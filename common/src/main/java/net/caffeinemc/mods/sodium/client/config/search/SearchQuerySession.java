package net.caffeinemc.mods.sodium.client.config.search;

import java.util.List;

public interface SearchQuerySession {
    List<? extends TextSource> getSearchResults(String query);
}

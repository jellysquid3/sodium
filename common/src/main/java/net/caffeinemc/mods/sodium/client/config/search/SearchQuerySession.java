package net.caffeinemc.mods.sodium.client.config.search;

import java.util.Collection;

public interface SearchQuerySession {
    Collection<SearchResult> getSearchResults(String query);
}

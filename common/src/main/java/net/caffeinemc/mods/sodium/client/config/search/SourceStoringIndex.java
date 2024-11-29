package net.caffeinemc.mods.sodium.client.config.search;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

import java.util.List;

public abstract class SourceStoringIndex extends SearchIndex {
    protected final List<TextSource> sources = new ReferenceArrayList<>();

    SourceStoringIndex(Runnable registerCallback) {
        super(registerCallback);
    }

    @Override
    public void register(TextSource source) {
        this.sources.add(source);
    }

    @Override
    public void buildIndexInitial() {
        this.rebuildIndex();
    }
}

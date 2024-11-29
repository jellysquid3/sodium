package net.caffeinemc.mods.sodium.client.config.search;

public class SearchResult {
    private final TextSource source;
    private float score;

    public SearchResult(TextSource source, float score) {
        this.source = source;
        this.score = score;
    }

    public TextSource getSource() {
        return this.source;
    }

    public float getScore() {
        return this.score;
    }

    void setScore(float score) {
        this.score = score;
    }
}

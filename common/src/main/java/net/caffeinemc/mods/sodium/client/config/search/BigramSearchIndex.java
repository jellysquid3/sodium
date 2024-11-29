package net.caffeinemc.mods.sodium.client.config.search;

import it.unimi.dsi.fastutil.objects.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public class BigramSearchIndex extends SourceStoringIndex {
    private final Object2ReferenceMap<String, BigramSources> bigramIndex = new Object2ReferenceOpenHashMap<>();

    private record SourceBigramCount(TextSource source, int count) {
    }

    private static class BigramSources {
        public final List<SourceBigramCount> counts = new ObjectArrayList<>();
        public float prevalenceInv;
    }

    public BigramSearchIndex(Runnable registerCallback) {
        super(registerCallback);
    }

    private static final Pattern NON_WORD = Pattern.compile("[\\W_]+", Pattern.UNICODE_CHARACTER_CLASS);

    @Override
    public void rebuildIndex() {
        this.bigramIndex.clear();

        for (TextSource source : this.sources) {
            var text = source.getText();
            if (text == null) {
                continue;
            }

            text = conditionText(text).trim();

            for (var entry : countBigrams(text).object2IntEntrySet()) {
                var bigram = entry.getKey();
                var count = entry.getIntValue();

                var list = this.bigramIndex.computeIfAbsent(bigram, k -> new BigramSources());

                // calculate the density of this bigram in the source string
                list.counts.add(new SourceBigramCount(source, count));
            }
        }

        // calculate weight factor for each bigram
        for (var bigramSources : this.bigramIndex.values()) {
            bigramSources.prevalenceInv = (float) this.sources.size() / bigramSources.counts.size();
        }
    }

    private static String conditionText(String text) {
        text = text.toLowerCase();
        text = NON_WORD.matcher(text).replaceAll(" ");
        return text;
    }

    private static @NotNull Object2IntMap<String> countBigrams(String text) {
        int length = text.length();
        var bigramCounts = new Object2IntLinkedOpenHashMap<String>(length + 1);

        addLeadingBigram(text, bigramCounts);

        addInnerBigrams(text, bigramCounts);

        addTrailingBigram(text, length, bigramCounts);
        return bigramCounts;
    }

    private static void addInnerBigrams(String text, Object2IntMap<String> bigramCounts) {
        int length = text.length();

        for (int i = 0; i < length - 1; i++) {
            var bigram = text.substring(i, i + 2);
            bigramCounts.put(bigram, bigramCounts.getInt(bigram) + 1);
        }
    }

    private static void addTrailingBigram(String text, int length, Object2IntLinkedOpenHashMap<String> bigramCounts) {
        var trailingBigram = text.charAt(length - 1) + " ";
        bigramCounts.put(trailingBigram, bigramCounts.getInt(trailingBigram) + 1);
    }

    private static void addLeadingBigram(String text, Object2IntLinkedOpenHashMap<String> bigramCounts) {
        var leadingBigram = " " + text.charAt(0);
        bigramCounts.put(leadingBigram, bigramCounts.getInt(leadingBigram) + 1);
    }

    @Override
    protected SearchQuerySession createQuery() {
        return new BigramSearchQuerySession();
    }

    private class BigramSearchQuerySession implements SearchQuerySession {
        @Override
        public Collection<SearchResult> getSearchResults(String query) {
            query = conditionText(query);

            // count leading and inner bigrams, trailing bigram isn't useful
            var queryBigrams = new Object2IntLinkedOpenHashMap<String>(query.length());
            addLeadingBigram(query, queryBigrams);
            addInnerBigrams(query, queryBigrams);

            var queryBigramTotalInv = 1.0f / (query.length() + 1);

            var sourceScores = new Reference2FloatLinkedOpenHashMap<TextSource>();
            var maxScore = 0.0f;

            // score the sources that contain the bigrams in the query
            for (var entry : queryBigrams.object2IntEntrySet()) {
                var bigram = entry.getKey();
                var queryCount = entry.getIntValue();
                var queryBigramDensity = queryCount * queryBigramTotalInv;

                var bigramSources = BigramSearchIndex.this.bigramIndex.get(bigram);
                if (bigramSources == null) {
                    continue;
                }

                var prevalenceInv = bigramSources.prevalenceInv;
                for (var sourceBigramCount : bigramSources.counts) {
                    var source = sourceBigramCount.source;
                    var sourceCount = sourceBigramCount.count;

                    // TODO: is this a good score function?
                    var score = queryBigramDensity * ((float) Math.log(sourceCount) + 1) * prevalenceInv;

                    // reduce score if there are more of this bigram in the query than in the source
                    if (queryCount > sourceCount) {
                        score *= (float) sourceCount / (sourceCount + 2 * (queryCount - sourceCount));
                    }

                    var newScore = sourceScores.getFloat(source) + score;
                    sourceScores.put(source, newScore);

                    maxScore = Math.max(maxScore, newScore);
                }
            }

            // sort by descending relevance and filter out irrelevant results
            var scoreCutoff = maxScore * 0.2f;

            ObjectList<SearchResult> results = new ObjectArrayList<>(sourceScores.size());
            for (var entry : sourceScores.reference2FloatEntrySet()) {
                var score = entry.getFloatValue();

                if (score >= scoreCutoff) {
                    results.add(new SearchResult(entry.getKey(), score));
                }
            }

            results.sort(Comparator.comparing(SearchResult::getScore).reversed());

            // show at most 10
            if (results.size() > 10) {
                results = results.subList(0, 10);
            }

            return results;
        }
    }
}

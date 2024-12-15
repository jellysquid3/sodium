package net.caffeinemc.mods.sodium.client.util.collections;

import net.caffeinemc.mods.sodium.client.util.MathUtil;

import java.util.Arrays;

/**
 * Originally authored here: https://github.com/CaffeineMC/sodium/blob/ddfb9f21a54bfb30aa876678204371e94d8001db/src/main/java/net/caffeinemc/sodium/util/collections/BitArray.java
 * @author burgerindividual
 */
public class BitArray {
    private static final int ADDRESS_BITS_PER_WORD = 6;
    private static final int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD;
    private static final int BIT_INDEX_MASK = BITS_PER_WORD - 1;
    private static final long WORD_MASK = -1L;

    private final long[] words;
    private final int capacity;

    public BitArray(int capacity) {
        this.words = new long[(MathUtil.align(capacity, BITS_PER_WORD) >> ADDRESS_BITS_PER_WORD)];
        this.capacity = capacity;
    }

    public boolean get(int index) {
        return (this.words[wordIndex(index)] & 1L << bitIndex(index)) != 0;
    }

    public void set(int index) {
        this.words[wordIndex(index)] |= 1L << bitIndex(index);
    }

    public void unset(int index) {
        this.words[wordIndex(index)] &= ~(1L << bitIndex(index));
    }

    public void put(int index, boolean value) {
        int wordIndex = wordIndex(index);
        int bitIndex = bitIndex(index);
        long intValue = value ? 1 : 0;
        this.words[wordIndex] = (this.words[wordIndex] & ~(1L << bitIndex)) | (intValue << bitIndex);
    }

    /**
     * Sets the bits from startIdx (inclusive) to endIdx (exclusive) to 1
     */
    public void set(int startIdx, int endIdx) {
        int startWordIndex = wordIndex(startIdx);
        int endWordIndex = wordIndex(endIdx - 1);

        long firstWordMask = WORD_MASK << startIdx;
        long lastWordMask = WORD_MASK >>> -endIdx;

        if (startWordIndex == endWordIndex) {
            this.words[startWordIndex] |= (firstWordMask & lastWordMask);
        } else {
            this.words[startWordIndex] |= firstWordMask;

            for (int i = startWordIndex + 1; i < endWordIndex; i++) {
                this.words[i] = -1L;
            }

            this.words[endWordIndex] |= lastWordMask;
        }
    }

    /**
     * Sets the bits from startIdx (inclusive) to endIdx (exclusive) to 0
     */
    public void unset(int startIdx, int endIdx) {
        int startWordIndex = wordIndex(startIdx);
        int endWordIndex = wordIndex(endIdx - 1);

        long firstWordMask = ~(WORD_MASK << startIdx);
        long lastWordMask = ~(WORD_MASK >>> -endIdx);

        if (startWordIndex == endWordIndex) {
            this.words[startWordIndex] &= (firstWordMask & lastWordMask);
        } else {
            this.words[startWordIndex] &= firstWordMask;

            for (int i = startWordIndex + 1; i < endWordIndex; i++) {
                this.words[i] = 0L;
            }

            this.words[endWordIndex] &= lastWordMask;
        }
    }

    public void fill(boolean value) {
        Arrays.fill(this.words, value ? -1L : 0L);
    }

    public void unsetAll() {
        this.fill(false);
    }

    public void setAll() {
        this.fill(true);
    }

    public int countSetBits() {
        int sum = 0;

        for (long word : this.words) {
            sum += Long.bitCount(word);
        }

        return sum;
    }

    public int capacity() {
        return this.capacity;
    }

    public boolean getAndSet(int index) {
        int wordIndex = wordIndex(index);
        long bit = 1L << bitIndex(index);

        long word = this.words[wordIndex];
        this.words[wordIndex] = word | bit;

        return (word & bit) != 0;
    }

    public boolean getAndUnset(int index) {
        var wordIndex = wordIndex(index);
        var bit = 1L << bitIndex(index);

        var word = this.words[wordIndex];
        this.words[wordIndex] = word & ~bit;

        return (word & bit) != 0;
    }

    public int nextSetBit(int fromIndex) {
        int u = wordIndex(fromIndex);

        if (u >= this.words.length) {
            return -1;
        }
        long word = this.words[u] & (WORD_MASK << fromIndex);

        while (true) {
            if (word != 0) {
                return (u * BITS_PER_WORD) + Long.numberOfTrailingZeros(word);
            }

            if (++u == this.words.length) {
                return -1;
            }

            word = this.words[u];
        }
    }

    private static int wordIndex(int index) {
        return index >> ADDRESS_BITS_PER_WORD;
    }

    private static int bitIndex(int index) {
        return index & BIT_INDEX_MASK;
    }
}

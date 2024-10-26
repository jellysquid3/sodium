package net.caffeinemc.mods.sodium.client.util.collections;

import java.util.Arrays;

/**
 * Originally authored here: https://github.com/CaffeineMC/sodium/blob/ddfb9f21a54bfb30aa876678204371e94d8001db/src/main/java/net/caffeinemc/sodium/util/collections/BitArray.java
 * @author burgerindividual
 */
public class BitArray {
    private static final int ADDRESS_BITS_PER_WORD = 6;
    private static final int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD;
    private static final int BIT_INDEX_MASK = BITS_PER_WORD - 1;
    private static final long WORD_MASK = 0xFFFFFFFFFFFFFFFFL;

    private final long[] words;
    private final int count;

    /**
     * Returns {@param num} aligned to the next multiple of {@param alignment}.
     * 
     * Taken from https://github.com/CaffeineMC/sodium/blob/1.19.x/next/components/gfx-utils/src/main/java/net/caffeinemc/gfx/util/misc/MathUtil.java
     * 
     * @param num       The number that will be rounded if needed
     * @param alignment The multiple that the output will be rounded to (must be a
     *                  power-of-two)
     * @return The aligned position, either equal to or greater than {@param num}
     */
    private static int align(int num, int alignment) {
        int additive = alignment - 1;
        int mask = ~additive;
        return (num + additive) & mask;
    }

    public BitArray(int count) {
        this.words = new long[(align(count, BITS_PER_WORD) >> ADDRESS_BITS_PER_WORD)];
        this.count = count;
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
                this.words[i] = 0xFFFFFFFFFFFFFFFFL;
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
                this.words[i] = 0x0000000000000000L;
            }

            this.words[endWordIndex] &= lastWordMask;
        }
    }

    // FIXME
    /* public boolean checkUnset(int startIdx, int endIdx) {
        int startWordIndex = wordIndex(startIdx);
        int endWordIndex = wordIndex(endIdx - 1);

        long firstWordMask = ~(WORD_MASK << startIdx);
        long lastWordMask = ~(WORD_MASK >>> -endIdx);
        if (startWordIndex == endWordIndex) {
            return (this.words[startWordIndex] & firstWordMask & lastWordMask) == 0x0000000000000000L;
        } else {
            if ((this.words[startWordIndex] & firstWordMask) != 0x0000000000000000L) {
                return false;
            }

            for (int i = startWordIndex + 1; i < endWordIndex; i++) {
                if (this.words[i] != 0x0000000000000000L) {
                    return false;
                }
            }

            return (this.words[endWordIndex] & lastWordMask) == 0x0000000000000000L;
        }
    }*/

    public void copy(BitArray src, int startIdx, int endIdx) {
        int startWordIndex = wordIndex(startIdx);
        int endWordIndex = wordIndex(endIdx - 1);

        long firstWordMask = WORD_MASK << startIdx;
        long lastWordMask = WORD_MASK >>> -endIdx;
        if (startWordIndex == endWordIndex) {
            long combinedMask = firstWordMask & lastWordMask;
            long invCombinedMask = ~combinedMask;
            this.words[startWordIndex] = (this.words[startWordIndex] & invCombinedMask)
                    | (src.words[startWordIndex] & combinedMask);
        } else {
            long invFirstWordMask = ~firstWordMask;
            long invLastWordMask = ~lastWordMask;

            this.words[startWordIndex] = (this.words[startWordIndex] & invFirstWordMask)
                    | (src.words[startWordIndex] & firstWordMask);

            int length = endWordIndex - (startWordIndex + 1);
            if (length > 0) {
                System.arraycopy(
                        src.words,
                        startWordIndex + 1,
                        this.words,
                        startWordIndex + 1,
                        length);
            }

            this.words[endWordIndex] = (this.words[endWordIndex] & invLastWordMask)
                    | (src.words[endWordIndex] & lastWordMask);
        }
    }

    public void copy(BitArray src, int index) {
        int wordIndex = wordIndex(index);
        long invBitMask = 1L << bitIndex(index);
        long bitMask = ~invBitMask;
        this.words[wordIndex] = (this.words[wordIndex] & bitMask) | (src.words[wordIndex] & invBitMask);
    }

    public void and(BitArray src, int startIdx, int endIdx) {
        int startWordIndex = wordIndex(startIdx);
        int endWordIndex = wordIndex(endIdx - 1);

        long firstWordMask = WORD_MASK << startIdx;
        long lastWordMask = WORD_MASK >>> -endIdx;
        if (startWordIndex == endWordIndex) {
            long combinedMask = firstWordMask & lastWordMask;
            long invCombinedMask = ~combinedMask;
            this.words[startWordIndex] &= (src.words[startWordIndex] | invCombinedMask);
        } else {
            long invFirstWordMask = ~firstWordMask;
            long invLastWordMask = ~lastWordMask;

            this.words[startWordIndex] &= (src.words[startWordIndex] | invFirstWordMask);

            for (int i = startWordIndex + 1; i < endWordIndex; i++) {
                this.words[i] &= src.words[i];
            }

            this.words[endWordIndex] &= (src.words[endWordIndex] | invLastWordMask);
        }
    }

    private static int wordIndex(int index) {
        return index >> ADDRESS_BITS_PER_WORD;
    }

    private static int bitIndex(int index) {
        return index & BIT_INDEX_MASK;
    }

    public void fill(boolean value) {
        Arrays.fill(this.words, value ? 0xFFFFFFFFFFFFFFFFL : 0x0000000000000000L);
    }

    public void unset() {
        this.fill(false);
    }

    public void set() {
        this.fill(true);
    }

    public int count() {
        int sum = 0;

        for (long word : this.words) {
            sum += Long.bitCount(word);
        }

        return sum;
    }

    public int capacity() {
        return this.count;
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

    public String toBitString() {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < this.count; i++) {
            sb.append(this.get(i) ? '1' : '0');
        }

        return sb.toString();
    }
}

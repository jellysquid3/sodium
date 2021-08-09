package me.jellysquid.mods.sodium.client.level.cloned.palette;

public class ClonedPalleteArray<K> implements ClonedPalette<K> {
    private final K[] array;
    private final K defaultValue;

    public ClonedPalleteArray(K[] array, K defaultValue) {
        this.array = array;
        this.defaultValue = defaultValue;
    }

    @Override
    public K get(int id) {
        K value = this.array[id];

        if (value == null) {
            return this.defaultValue;
        }

        return value;
    }
}

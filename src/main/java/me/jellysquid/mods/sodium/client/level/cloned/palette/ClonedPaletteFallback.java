package me.jellysquid.mods.sodium.client.level.cloned.palette;

import net.minecraft.core.IdMapper;

public class ClonedPaletteFallback<K> implements ClonedPalette<K> {
    private final IdMapper<K> idList;

    public ClonedPaletteFallback(IdMapper<K> idList) {
        this.idList = idList;
    }

    @Override
    public K get(int id) {
        return this.idList.byId(id);
    }
}

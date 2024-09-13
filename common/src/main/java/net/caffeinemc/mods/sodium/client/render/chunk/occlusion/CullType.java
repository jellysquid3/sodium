package net.caffeinemc.mods.sodium.client.render.chunk.occlusion;

public enum CullType {
    WIDE(1, false),
    REGULAR(0, false),
    FRUSTUM(0, true);

    public final int bfsWidth;
    public final boolean isFrustumTested;

    CullType(int bfsWidth, boolean isFrustumTested) {
        this.bfsWidth = bfsWidth;
        this.isFrustumTested = isFrustumTested;
    }
}

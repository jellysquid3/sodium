package net.caffeinemc.mods.sodium.client.render.chunk.occlusion;

public enum CullType {
    WIDE(1, false, false),
    REGULAR(0, false, false),
    FRUSTUM(0, true, true);

    public final int bfsWidth;
    public final boolean isFrustumTested;
    public final boolean isFogCulled;

    CullType(int bfsWidth, boolean isFrustumTested, boolean isFogCulled) {
        this.bfsWidth = bfsWidth;
        this.isFrustumTested = isFrustumTested;
        this.isFogCulled = isFogCulled;
    }
}

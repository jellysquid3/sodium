package net.caffeinemc.mods.sodium.client.render.chunk.occlusion;

public enum CullType {
    WIDE(1, false),
    REGULAR(0, false),
    FRUSTUM(0, true);

    public final int bfsWidth;
    public final boolean isFrustumTested;

    public static final CullType[] WIDE_TO_NARROW = values();
    public static final CullType[] NARROW_TO_WIDE = {FRUSTUM, REGULAR, WIDE};

    CullType(int bfsWidth, boolean isFrustumTested) {
        this.bfsWidth = bfsWidth;
        this.isFrustumTested = isFrustumTested;
    }
}

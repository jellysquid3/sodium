package net.caffeinemc.mods.sodium.client.render.chunk.occlusion;

public enum CullType {
    WIDE("W", 1, false, false),
    REGULAR("R", 0, false, false),
    FRUSTUM("F", 0, true, true);

    public final String abbreviation;
    public final int bfsWidth;
    public final boolean isFrustumTested;
    public final boolean isFogCulled;

    CullType(String abbreviation, int bfsWidth, boolean isFrustumTested, boolean isFogCulled) {
        this.abbreviation = abbreviation;
        this.bfsWidth = bfsWidth;
        this.isFrustumTested = isFrustumTested;
        this.isFogCulled = isFogCulled;
    }
}

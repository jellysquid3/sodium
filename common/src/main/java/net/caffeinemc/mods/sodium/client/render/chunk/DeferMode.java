package net.caffeinemc.mods.sodium.client.render.chunk;

public enum DeferMode {
    ALWAYS, ONE_FRAME, ZERO_FRAMES;

    public boolean allowsUnlimitedUploadSize() {
        return this == ZERO_FRAMES;
    }
}

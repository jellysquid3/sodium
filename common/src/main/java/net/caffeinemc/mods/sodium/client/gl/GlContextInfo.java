package net.caffeinemc.mods.sodium.client.gl;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11C;

public record GlContextInfo(String vendor, String renderer, String version) {
    @Nullable
    public static GlContextInfo create() {
        String vendor = GL11C.glGetString(GL11C.GL_VENDOR);
        String renderer = GL11C.glGetString(GL11C.GL_RENDERER);
        String version = GL11C.glGetString(GL11C.GL_VERSION);

        if (vendor == null || renderer == null || version == null) {
            return null;
        }

        return new GlContextInfo(vendor, renderer, version);
    }
}

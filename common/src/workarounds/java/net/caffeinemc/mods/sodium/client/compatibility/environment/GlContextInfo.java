package net.caffeinemc.mods.sodium.client.compatibility.environment;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11C;

import java.util.Objects;

public record GlContextInfo(String vendor, String renderer, String version) {
    public static GlContextInfo create() {
        String vendor = Objects.requireNonNull(GL11C.glGetString(GL11C.GL_VENDOR),
                "GL_VENDOR is NULL");
        String renderer = Objects.requireNonNull(GL11C.glGetString(GL11C.GL_RENDERER),
                "GL_RENDERER is NULL");
        String version = Objects.requireNonNull(GL11C.glGetString(GL11C.GL_VERSION),
                "GL_VERSION is NULL");

        return new GlContextInfo(vendor, renderer, version);
    }
}

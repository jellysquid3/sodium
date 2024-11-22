package net.caffeinemc.mods.sodium.client.render.chunk.async;

import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.CullType;

public enum AsyncTaskType {
    FRUSTUM_CULL(CullType.FRUSTUM.abbreviation),
    REGULAR_CULL(CullType.REGULAR.abbreviation),
    WIDE_CULL(CullType.WIDE.abbreviation),
    FRUSTUM_TASK_COLLECTION("T");

    public static final AsyncTaskType[] VALUES = values();

    public final String abbreviation;

    AsyncTaskType(String abbreviation) {
        this.abbreviation = abbreviation;
    }
}

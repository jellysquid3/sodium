package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting;

import net.caffeinemc.mods.sodium.client.render.chunk.DeferMode;

public enum SortBehavior {
    OFF("OFF", SortMode.NONE),
    STATIC("S", SortMode.STATIC),
    DYNAMIC_DEFER_ALWAYS("DF", PriorityMode.NONE, DeferMode.ALWAYS),
    DYNAMIC_DEFER_NEARBY_ONE_FRAME("N1", PriorityMode.NEARBY, DeferMode.ONE_FRAME),
    DYNAMIC_DEFER_NEARBY_ZERO_FRAMES("N0", PriorityMode.NEARBY, DeferMode.ZERO_FRAMES),
    DYNAMIC_DEFER_ALL_ONE_FRAME("A1", PriorityMode.ALL, DeferMode.ONE_FRAME),
    DYNAMIC_DEFER_ALL_ZERO_FRAMES("A0", PriorityMode.ALL, DeferMode.ZERO_FRAMES);

    private final String shortName;
    private final SortBehavior.SortMode sortMode;
    private final SortBehavior.PriorityMode priorityMode;
    private final DeferMode deferMode;

    SortBehavior(String shortName, SortBehavior.SortMode sortMode, SortBehavior.PriorityMode priorityMode,
            DeferMode deferMode) {
        this.shortName = shortName;
        this.sortMode = sortMode;
        this.priorityMode = priorityMode;
        this.deferMode = deferMode;
    }

    SortBehavior(String shortName, SortBehavior.SortMode sortMode) {
        this(shortName, sortMode, null, null);
    }

    SortBehavior(String shortName, SortBehavior.PriorityMode priorityMode, DeferMode deferMode) {
        this(shortName, SortMode.DYNAMIC, priorityMode, deferMode);
    }

    public String getShortName() {
        return this.shortName;
    }

    public SortBehavior.SortMode getSortMode() {
        return this.sortMode;
    }

    public SortBehavior.PriorityMode getPriorityMode() {
        return this.priorityMode;
    }

    public DeferMode getDeferMode() {
        return this.deferMode;
    }

    public enum SortMode {
        NONE, STATIC, DYNAMIC
    }

    public enum PriorityMode {
        NONE, NEARBY, ALL
    }
}

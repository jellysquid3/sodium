package net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;

public record MeshResultSize(SectionCategory category, long resultSize) implements Average1DEstimator.Value<MeshResultSize.SectionCategory> {
    public static long NO_DATA = -1;

    public enum SectionCategory {
        LOW,
        UNDERGROUND,
        WATER_LEVEL,
        SURFACE,
        HIGH;

        public static SectionCategory forSection(RenderSection section) {
            var sectionY = section.getChunkY();
            if (sectionY < 0) {
                return LOW;
            } else if (sectionY < 3) {
                return UNDERGROUND;
            } else if (sectionY == 3) {
                return WATER_LEVEL;
            } else if (sectionY < 7) {
                return SURFACE;
            } else {
                return HIGH;
            }
        }
    }

    public static MeshResultSize forSection(RenderSection section, long resultSize) {
        return new MeshResultSize(SectionCategory.forSection(section), resultSize);
    }

    @Override
    public SectionCategory category() {
        return this.category;
    }

    @Override
    public long value() {
        return this.resultSize;
    }
}

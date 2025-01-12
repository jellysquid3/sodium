package net.caffeinemc.mods.sodium.client.render.chunk.lists;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.core.SectionPos;

public class FrustumTaskCollector extends PendingTaskCollector implements SectionTree.VisibleSectionVisitor {
    private final Long2ReferenceMap<RenderSection> sectionByPosition;

    public FrustumTaskCollector(Viewport viewport, float buildDistance, Long2ReferenceMap<RenderSection> sectionByPosition) {
        super(viewport, buildDistance, true);

        this.sectionByPosition = sectionByPosition;
    }

    @Override
    public void visit(int x, int y, int z) {
        var section = this.sectionByPosition.get(SectionPos.asLong(x, y, z));

        if (section == null) {
            return;
        }

        this.checkForTask(section);
    }
}

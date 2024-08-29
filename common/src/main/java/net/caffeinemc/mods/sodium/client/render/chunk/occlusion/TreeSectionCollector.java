package net.caffeinemc.mods.sodium.client.render.chunk.occlusion;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;

public abstract class TreeSectionCollector implements OcclusionCuller.Visitor {
    abstract void add(RenderSection section);

    abstract void traverseVisible(OcclusionCuller.Visitor visitor, Viewport viewport);

    @Override
    public void visit(RenderSection section, boolean visible) {
        this.add(section);
    }
}

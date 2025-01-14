package net.caffeinemc.mods.sodium.client.render.chunk.async;

import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;

public interface FrustumCullResult extends FrustumTaskListsResult {
    SectionTree getTree();
}

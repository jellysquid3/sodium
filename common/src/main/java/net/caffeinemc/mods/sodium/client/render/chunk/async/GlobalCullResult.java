package net.caffeinemc.mods.sodium.client.render.chunk.async;

import net.caffeinemc.mods.sodium.client.render.chunk.lists.TaskListCollection;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.TaskSectionTree;

public interface GlobalCullResult extends FrustumTaskListsResult {
    TaskSectionTree getTaskTree();

    TaskListCollection getGlobalTaskLists();
}

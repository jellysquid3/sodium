package net.caffeinemc.mods.sodium.client.render.chunk.async;

import net.caffeinemc.mods.sodium.client.render.chunk.lists.PendingTaskCollector;

public interface FrustumTaskListsResult {
    PendingTaskCollector.TaskListCollection getFrustumTaskLists();
}

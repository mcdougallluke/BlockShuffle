package org.lukeeirl.blockShuffle.util;

import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public final class TaskRegistry {

    private final List<BukkitTask> tasks = new ArrayList<>();

    public void track(BukkitTask task) {
        if (task != null) {
            tasks.add(task);
        }
    }

    public void cancelAll() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
    }
}

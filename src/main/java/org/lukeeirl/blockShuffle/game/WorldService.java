package org.lukeeirl.blockShuffle.game;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.File;

public class WorldService {
    public World createNewWorld() {
        String worldName = "blockshuffle_" + System.currentTimeMillis();
        return Bukkit.createWorld(new WorldCreator(worldName));
    }

    public void deleteWorld(World world) {
        if (world != null) {
            Bukkit.unloadWorld(world, false);
            deleteWorldFolder(world.getWorldFolder());
        }
    }

    private void deleteWorldFolder(File folder) {
        if (folder.isDirectory()) {
            for (File file : folder.listFiles()) {
                deleteWorldFolder(file);
            }
        }
        folder.delete();
    }
}

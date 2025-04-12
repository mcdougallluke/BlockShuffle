package org.lukeeirl.blockShuffle.game;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.File;
import java.util.Objects;

public class WorldService {
    public World createNewWorld() {
        String worldName = "blockshuffle_" + System.currentTimeMillis();
        World world = Bukkit.createWorld(new WorldCreator(worldName));

        if (world != null) {
            world.setGameRule(GameRule.DO_INSOMNIA, false);
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        }

        return world;
    }

    public void deleteWorld(World world) {
        if (world != null) {
            Bukkit.unloadWorld(world, false);
            deleteWorldFolder(world.getWorldFolder());
        }
    }

    private void deleteWorldFolder(File folder) {
        if (folder.isDirectory()) {
            for (File file : Objects.requireNonNull(folder.listFiles())) {
                deleteWorldFolder(file);
            }
        }
        folder.delete();
    }
}

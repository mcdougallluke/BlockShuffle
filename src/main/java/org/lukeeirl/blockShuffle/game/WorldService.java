package org.lukeeirl.blockShuffle.game;

import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.File;
import java.util.List;
import java.util.Objects;

public class WorldService {

    public World createLinkedWorlds(String baseName) {
        // Create Overworld
        World overworld = new WorldCreator(baseName)
                .environment(World.Environment.NORMAL)
                .createWorld();

        // Create Nether
        new WorldCreator(baseName + "_nether")
                .environment(World.Environment.NETHER)
                .createWorld();

        // Optionally create The End (you can skip if not needed)
        new WorldCreator(baseName + "_the_end")
                .environment(World.Environment.THE_END)
                .createWorld();

        // Apply game rules to all three worlds
        for (World world : List.of(
                Objects.requireNonNull(Bukkit.getWorld(baseName)),
                Objects.requireNonNull(Bukkit.getWorld(baseName + "_nether")),
                Objects.requireNonNull(Bukkit.getWorld(baseName + "_the_end"))
        )) {
            if (world != null) {
                world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
                world.setGameRule(GameRules.SPAWN_PHANTOMS, false);
                world.setViewDistance(32);
                world.setSimulationDistance(32);
            }
        }

        return overworld;
    }

    public void deleteWorld(World world) {
        if (world != null) {
            Bukkit.unloadWorld(world, false);
            deleteWorldFolder(world.getWorldFolder());

            // Also delete _nether and _the_end
            String name = world.getName();
            World nether = Bukkit.getWorld(name + "_nether");
            World theEnd = Bukkit.getWorld(name + "_the_end");

            if (nether != null) {
                Bukkit.unloadWorld(nether, false);
                deleteWorldFolder(nether.getWorldFolder());
            }

            if (theEnd != null) {
                Bukkit.unloadWorld(theEnd, false);
                deleteWorldFolder(theEnd.getWorldFolder());
            }
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

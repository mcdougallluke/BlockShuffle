package org.lukeeirl.blockShuffle.util;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class CreeperManager {
    private final File creeperFile;
    private final YamlConfiguration creeperConfig;
    private final Set<UUID> creeperPlayers;

    public CreeperManager(File creeperFile, YamlConfiguration creeperConfig) {
        this.creeperFile = creeperFile;
        this.creeperConfig = creeperConfig;
        this.creeperPlayers = new HashSet<>();
        loadCreeperPlayers();
    }

    private void loadCreeperPlayers() {
        if (creeperConfig.contains("creeper-players")) {
            for (String uuidStr : creeperConfig.getStringList("creeper-players")) {
                try {
                    creeperPlayers.add(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException e) {
                    // Invalid UUID, skip it
                }
            }
        }
    }

    public boolean isCreeper(UUID uuid) {
        return creeperPlayers.contains(uuid);
    }

    public boolean toggleCreeper(UUID uuid) {
        boolean wasAdded;
        if (creeperPlayers.contains(uuid)) {
            creeperPlayers.remove(uuid);
            wasAdded = false;
        } else {
            creeperPlayers.add(uuid);
            wasAdded = true;
        }
        save();
        return wasAdded;
    }

    public Set<UUID> getCreeperPlayers() {
        return new HashSet<>(creeperPlayers);
    }

    private void save() {
        creeperConfig.set("creeper-players", creeperPlayers.stream().map(UUID::toString).toList());
        try {
            creeperConfig.save(creeperFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

package org.lukeeirl.blockShuffle.game;

import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerTracker {
    private final Set<UUID> readyPlayers = new HashSet<>();
    private final Set<UUID> usersInGame = new HashSet<>();
    private final Set<UUID> completedUsers = new HashSet<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final Set<UUID> skippedPlayers = new HashSet<>();
    private final Map<UUID, Material> userMaterialMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerEndTime = new HashMap<>();
    private final Map<UUID, Integer> playerRounds = new HashMap<>();
    private final Map<UUID, Long> playerRoundStartTime = new HashMap<>();
    private final Map<UUID, Long> playerRoundDuration = new HashMap<>();
    private final Map<UUID, Integer> usedSkipsThisGame = new HashMap<>();

    public boolean isReady(UUID uuid) { return readyPlayers.contains(uuid); }
    public void setReady(UUID uuid) { readyPlayers.add(uuid); }
    public void setNotReady(UUID uuid) { readyPlayers.remove(uuid); }

    public void addInGame(UUID uuid) { usersInGame.add(uuid); }
    public void addCompleted(UUID uuid) { completedUsers.add(uuid); }
    public void addSpectator(UUID uuid) { spectators.add(uuid); }
    public void addSkipped(UUID uuid) { skippedPlayers.add(uuid); }

    public void assignBlock(UUID uuid, Material material) { userMaterialMap.put(uuid, material); }

    public int getUsedSkips(UUID uuid) { return usedSkipsThisGame.getOrDefault(uuid, 0); }
    public void incrementSkips(UUID uuid) { usedSkipsThisGame.put(uuid, getUsedSkips(uuid) + 1); }

    public Set<UUID> getReadyPlayers() { return readyPlayers; }
    public Set<UUID> getUsersInGame() { return usersInGame; }
    public Set<UUID> getCompletedUsers() { return completedUsers; }
    public Set<UUID> getSpectators() { return spectators; }
    public Set<UUID> getSkippedPlayers() { return skippedPlayers; }
    public Map<UUID, Material> getUserMaterialMap() { return userMaterialMap; }
    public Map<UUID, Long> getPlayerEndTime() { return playerEndTime; }
    public Map<UUID, Integer> getPlayerRounds() { return playerRounds; }
    public Map<UUID, Long> getPlayerRoundStartTime() { return playerRoundStartTime; }
    public Map<UUID, Long> getPlayerRoundDuration() { return playerRoundDuration; }

    public void clearAll() {
        readyPlayers.clear();
        usersInGame.clear();
        completedUsers.clear();
        spectators.clear();
        skippedPlayers.clear();
        userMaterialMap.clear();
        playerEndTime.clear();
        playerRounds.clear();
        usedSkipsThisGame.clear();
    }
}

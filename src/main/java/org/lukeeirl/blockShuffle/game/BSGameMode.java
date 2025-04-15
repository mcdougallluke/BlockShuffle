package org.lukeeirl.blockShuffle.game;

import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface BSGameMode {
    void startGame();
    void resetGame();
    void playerStandingOnBlock(Player player);
    void playerJoined(Player player);
    void sendPlayerToLobby(Player player);
    boolean trySkip(UUID uuid);
    void readyAllPlayers();
    boolean isInProgress();
    void setInProgress(boolean value);
    World getCurrentGameWorld();
}

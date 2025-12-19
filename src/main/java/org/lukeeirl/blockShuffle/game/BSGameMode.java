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
    void enterSpectatorMode(Player player);
    boolean trySkip(UUID uuid);
    boolean isInProgress();
    World getCurrentGameWorld();
    long getGameInstanceId();
}

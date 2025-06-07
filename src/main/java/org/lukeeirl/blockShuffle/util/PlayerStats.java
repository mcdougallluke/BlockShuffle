package org.lukeeirl.blockShuffle.util;

public record PlayerStats(
        int gamesPlayed,
        int gamesWon,
        int skipsBought,
        int blocksSteppedOn
) {
    public PlayerStats() { this(0,0,0, 0); }
    public PlayerStats incrementPlayed() { return new PlayerStats(gamesPlayed+1, gamesWon, skipsBought, blocksSteppedOn); }
    public PlayerStats incrementWon()    { return new PlayerStats(gamesPlayed, gamesWon+1, skipsBought, blocksSteppedOn); }
    public PlayerStats addSkips(int n)   { return new PlayerStats(gamesPlayed, gamesWon, skipsBought + n, blocksSteppedOn); }
    public PlayerStats incrementBlocksSteppedOn() { return new PlayerStats(gamesPlayed, gamesWon, skipsBought, blocksSteppedOn + 1); }
}


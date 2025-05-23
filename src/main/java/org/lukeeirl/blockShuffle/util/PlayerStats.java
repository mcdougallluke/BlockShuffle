package org.lukeeirl.blockShuffle.util;

public record PlayerStats(
        int gamesPlayed,
        int gamesWon,
        int skipsBought
) {
    public PlayerStats() { this(0,0,0); }
    public PlayerStats incrementPlayed() { return new PlayerStats(gamesPlayed+1, gamesWon, skipsBought); }
    public PlayerStats incrementWon()    { return new PlayerStats(gamesPlayed, gamesWon+1, skipsBought); }
    public PlayerStats addSkips(int n)   { return new PlayerStats(gamesPlayed, gamesWon, skipsBought + n); }
}


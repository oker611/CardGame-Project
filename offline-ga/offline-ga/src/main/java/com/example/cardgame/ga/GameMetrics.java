package com.example.cardgame.ga;

public class GameMetrics {
    public int[] suppressOpportunities;
    public int[] suppressSuccesses;
    public int[] bombUsage;
    public int[] twoUsage;
    public int[] totalPlays;

    public GameMetrics() {
        this.suppressOpportunities = new int[4];
        this.suppressSuccesses = new int[4];
        this.bombUsage = new int[4];
        this.twoUsage = new int[4];
        this.totalPlays = new int[4];
    }

    public double getSuppressRate(int playerIdx) {
        if (suppressOpportunities[playerIdx] == 0) return 0.5;
        return (double) suppressSuccesses[playerIdx] / suppressOpportunities[playerIdx];
    }

    public double getBombRate(int playerIdx) {
        if (totalPlays[playerIdx] == 0) return 0.0;
        return (double) bombUsage[playerIdx] / totalPlays[playerIdx];
    }

    public double getTwoRate(int playerIdx) {
        if (totalPlays[playerIdx] == 0) return 0.0;
        return (double) twoUsage[playerIdx] / totalPlays[playerIdx];
    }
}

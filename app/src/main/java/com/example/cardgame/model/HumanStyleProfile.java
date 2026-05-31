package com.example.cardgame.model;

import java.io.Serializable;

public class HumanStyleProfile implements Serializable {
    private String playerId;
    private String styleLabel;
    private double aggressivenessScore;
    private double conservativenessScore;
    private double bluffingScore;
    private int gamesAnalyzed;
    private long lastUpdated;

    public static final String STYLE_AGGRESSIVE = "激进";
    public static final String STYLE_CONSERVATIVE = "保守";
    public static final String STYLE_BALANCED = "均衡";
    public static final String STYLE_BLUFFER = "虚张声势";

    public HumanStyleProfile() {
        this.styleLabel = STYLE_BALANCED;
        this.aggressivenessScore = 0.5;
        this.conservativenessScore = 0.5;
        this.bluffingScore = 0.0;
        this.gamesAnalyzed = 0;
        this.lastUpdated = System.currentTimeMillis();
    }

    public HumanStyleProfile(String playerId) {
        this();
        this.playerId = playerId;
    }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getStyleLabel() { return styleLabel; }
    public void setStyleLabel(String styleLabel) { this.styleLabel = styleLabel; }

    public double getAggressivenessScore() { return aggressivenessScore; }
    public void setAggressivenessScore(double aggressivenessScore) { this.aggressivenessScore = aggressivenessScore; }

    public double getConservativenessScore() { return conservativenessScore; }
    public void setConservativenessScore(double conservativenessScore) { this.conservativenessScore = conservativenessScore; }

    public double getBluffingScore() { return bluffingScore; }
    public void setBluffingScore(double bluffingScore) { this.bluffingScore = bluffingScore; }

    public int getGamesAnalyzed() { return gamesAnalyzed; }
    public void setGamesAnalyzed(int gamesAnalyzed) { this.gamesAnalyzed = gamesAnalyzed; }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }

    public void incrementGamesAnalyzed() {
        this.gamesAnalyzed++;
        this.lastUpdated = System.currentTimeMillis();
    }

    public boolean isAggressive() {
        return STYLE_AGGRESSIVE.equals(styleLabel) || aggressivenessScore > 0.7;
    }

    public boolean isConservative() {
        return STYLE_CONSERVATIVE.equals(styleLabel) || conservativenessScore > 0.7;
    }

    public boolean isBluffer() {
        return bluffingScore > 0.6;
    }

    public String getCounterTactic() {
        if (isAggressive()) {
            return "防守反击：保留大牌，等待对手浪费";
        } else if (isConservative()) {
            return "主动进攻：抢夺牌权，压制对手";
        } else if (isBluffer()) {
            return "识破虚张：不轻易被吓退，适度跟牌";
        } else {
            return "均衡策略：随机应变";
        }
    }

    @Override
    public String toString() {
        return "HumanStyleProfile{" +
                "playerId='" + playerId + '\'' +
                ", styleLabel='" + styleLabel + '\'' +
                ", aggressiveness=" + aggressivenessScore +
                ", gamesAnalyzed=" + gamesAnalyzed +
                '}';
    }
}

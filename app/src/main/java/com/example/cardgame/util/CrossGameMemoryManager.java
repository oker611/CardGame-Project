package com.example.cardgame.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.cardgame.model.HumanStyleProfile;
import com.google.gson.Gson;

public class CrossGameMemoryManager {
    private static final String PREFS_NAME = "cardgame_adaptive_ai";
    private static final String KEY_HUMAN_STYLE = "human_style_";
    private static final String KEY_RECOMMENDED_TACTIC = "recommended_tactic";
    
    private final SharedPreferences prefs;
    private final Gson gson;

    public CrossGameMemoryManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    public void saveHumanStyleProfile(String playerId, HumanStyleProfile profile) {
        String json = gson.toJson(profile);
        prefs.edit()
                .putString(KEY_HUMAN_STYLE + playerId, json)
                .putLong(KEY_HUMAN_STYLE + playerId + "_time", System.currentTimeMillis())
                .apply();
    }

    public HumanStyleProfile loadHumanStyleProfile(String playerId) {
        String json = prefs.getString(KEY_HUMAN_STYLE + playerId, null);
        if (json == null) {
            return new HumanStyleProfile(playerId);
        }
        try {
            return gson.fromJson(json, HumanStyleProfile.class);
        } catch (Exception e) {
            e.printStackTrace();
            return new HumanStyleProfile(playerId);
        }
    }

    public void saveRecommendedTactic(String tactic) {
        prefs.edit().putString(KEY_RECOMMENDED_TACTIC, tactic).apply();
    }

    public String getRecommendedTactic() {
        return prefs.getString(KEY_RECOMMENDED_TACTIC, "均衡策略：随机应变");
    }

    public String getRecommendedTacticForStyle(HumanStyleProfile profile) {
        if (profile == null) {
            return "均衡策略：随机应变";
        }
        
        String tactic = profile.getCounterTactic();
        saveRecommendedTactic(tactic);
        return tactic;
    }

    public void clearAllData() {
        prefs.edit().clear().apply();
    }

    public void clearPlayerData(String playerId) {
        prefs.edit()
                .remove(KEY_HUMAN_STYLE + playerId)
                .remove(KEY_HUMAN_STYLE + playerId + "_time")
                .apply();
    }

    public long getLastAnalysisTime(String playerId) {
        return prefs.getLong(KEY_HUMAN_STYLE + playerId + "_time", 0);
    }

    public boolean hasProfileForPlayer(String playerId) {
        return prefs.contains(KEY_HUMAN_STYLE + playerId);
    }
}

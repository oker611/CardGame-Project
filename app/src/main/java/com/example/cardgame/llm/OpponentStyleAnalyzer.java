package com.example.cardgame.llm;

import android.util.Log;

import com.example.cardgame.ai.AIPlayerProfile;
import com.example.cardgame.util.CardTracker;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OpponentStyleAnalyzer {
    private static final String TAG = "CardGame";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final LLMAnalyzer llmAnalyzer;

    public OpponentStyleAnalyzer() {
        Log.d(TAG, "OpponentStyleAnalyzer 初始化");
        this.llmAnalyzer = new LLMAnalyzer();
    }

    public void analyzeAndUpdate(String playerId, CardTracker tracker, AIPlayerProfile profile) {
        Log.d(TAG, "开始分析对手 " + playerId + " 的风格");
        executor.execute(() -> {
            try {
                String history = tracker.getHistorySummary(playerId);
                Log.d(TAG, "对手 " + playerId + " 的出牌历史: " + (history == null ? "null" : history));
                
                if (history == null || history.isEmpty()) {
                    Log.d(TAG, "对手 " + playerId + " 没有出牌历史，跳过分析");
                    return;
                }

                Log.d(TAG, "调用 LLM 分析对手 " + playerId + " 的风格");
                String style = llmAnalyzer.summarizeOpponentStyle(history);
                Log.d(TAG, "对手 " + playerId + " 的风格分析结果: " + style);

                if (style.contains("激进")) {
                    Log.d(TAG, "设置对手 " + playerId + " 为激进风格: aggressiveness=0.8, defensive=false");
                    profile.setOpponentAggressiveness(0.8);
                    profile.setOpponentDefensive(false);
                } else if (style.contains("保守")) {
                    Log.d(TAG, "设置对手 " + playerId + " 为保守风格: aggressiveness=0.3, defensive=true");
                    profile.setOpponentAggressiveness(0.3);
                    profile.setOpponentDefensive(true);
                } else {
                    Log.d(TAG, "设置对手 " + playerId + " 为均衡风格: aggressiveness=0.5, defensive=false");
                    profile.setOpponentAggressiveness(0.5);
                    profile.setOpponentDefensive(false);
                }
                profile.setStyleAnalyzed(true);
                Log.d(TAG, "对手 " + playerId + " 的风格分析完成");
            } catch (IOException e) {
                Log.e(TAG, "分析对手 " + playerId + " 风格时发生错误: " + e.getMessage(), e);
            }
        });
    }
}
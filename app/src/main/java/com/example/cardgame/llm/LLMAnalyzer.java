package com.example.cardgame.llm;

import android.util.Log;

import com.example.cardgame.llm.model.ChatMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LLMAnalyzer {
    private static final String TAG = "LLMAnalyzer";
    private final VivoLLMClient vivoClient;

    public LLMAnalyzer() {
        Log.d(TAG, "LLMAnalyzer 初始化");
        this.vivoClient = new VivoLLMClient();
    }

    public String analyzeGameSituation(String gameStateDescription) throws IOException {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", "You are a helpful assistant for a card game analysis."));
        messages.add(new ChatMessage("user", "Analyze this game situation: " + gameStateDescription));

        return vivoClient.chat(messages);
    }

    public String suggestPlay(String handCards, String lastPlay, String gameProgress) throws IOException {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", "You are an expert card game strategy advisor."));
        messages.add(new ChatMessage("user", buildStrategyPrompt(handCards, lastPlay, gameProgress)));

        return vivoClient.chat(messages);
    }

    public String analyzeHand(String handCards) {
        String prompt = "你是一个锄大地专家。请分析以下手牌并给出简短的战略建议：\n" + handCards;
        try {
            List<ChatMessage> messages = Collections.singletonList(new ChatMessage("user", prompt));
            return vivoClient.chat(messages);
        } catch (IOException e) {
            e.printStackTrace();
            return "分析失败：" + e.getMessage();
        }
    }

    private String buildStrategyPrompt(String handCards, String lastPlay, String gameProgress) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("My hand cards: ").append(handCards).append("\n");
        prompt.append("Last play: ").append(lastPlay).append("\n");
        prompt.append("Game progress: ").append(gameProgress).append("\n");
        prompt.append("What card should I play? Provide your recommendation.");
        return prompt.toString();
    }

    public String summarizeOpponentStyle(String playHistory) throws IOException {
        Log.d(TAG, "开始分析对手风格，历史记录: " + playHistory);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", "You are an expert at analyzing card game player behavior. Analyze the opponent's play style based on their history and respond with ONLY ONE of these labels: 激进 (aggressive), 保守 (defensive), or 均衡 (balanced). Consider: Does the player frequently use big cards to beat others? Do they prefer to pass and wait? Do they play both big and small cards?"));
        messages.add(new ChatMessage("user", "Opponent play history: " + playHistory + "\n\nWhat is this opponent's play style? Respond with just one word: 激进, 保守, or 均衡."));

        Log.d(TAG, "发起 LLM 请求");
        String result = vivoClient.chat(messages);
        Log.d(TAG, "LLM 返回结果: " + result);
        return result;
    }

    /**
     * 分析手牌，返回建议风格：激进、保守、均衡
     * @param hand 当前玩家的手牌列表
     * @return 风格字符串，失败返回 null
     */
    public String analyzeHandStyle(List<com.example.cardgame.model.Card> hand) {
        if (hand == null || hand.isEmpty()) {
            Log.d(TAG, "手牌为空，跳过分析");
            return null;
        }

        String prompt = buildHandStylePrompt(hand);
        try {
            List<ChatMessage> messages = Collections.singletonList(new ChatMessage("user", prompt));
            String response = vivoClient.chat(messages);
            String style = parseStyleFromResponse(response);
            if (style != null) {
                Log.d(TAG, "LLM 返回风格: " + style);
                return style;
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "LLM 请求失败，使用本地分析: " + e.getMessage());
        }

        // LLM 失败时使用本地降级分析
        return analyzeHandStyleLocally(hand);
    }

    /**
     * 本地降级分析（不需要联网）
     * 根据手牌特征判断风格：
     * - 大牌多（2、A、K多）、对子/三张多 → 激进
     * - 小牌多、单张多、大牌少 → 保守
     * - 混合 → 均衡
     */
    private String analyzeHandStyleLocally(List<com.example.cardgame.model.Card> hand) {
        if (hand == null || hand.isEmpty()) {
            return null;
        }

        int highCards = 0;
        int mediumCards = 0;
        int lowCards = 0;
        int pairs = 0;
        int triples = 0;

        Map<com.example.cardgame.model.Rank, Integer> rankCount = new HashMap<>();
        for (com.example.cardgame.model.Card card : hand) {
            com.example.cardgame.model.Rank rank = card.getRank();
            rankCount.put(rank, rankCount.getOrDefault(rank, 0) + 1);
        }

        for (Map.Entry<com.example.cardgame.model.Rank, Integer> entry : rankCount.entrySet()) {
            com.example.cardgame.model.Rank rank = entry.getKey();
            int count = entry.getValue();
            int weight = rank.getWeight();

            if (weight >= 13) {
                highCards += count;
            } else if (weight >= 10) {
                mediumCards += count;
            } else {
                lowCards += count;
            }

            if (count >= 2) pairs++;
            if (count >= 3) triples++;
        }

        double aggressiveScore = highCards * 2.0 + pairs * 1.5 + triples * 2.0;
        double conservativeScore = lowCards * 2.0 + (hand.size() - highCards - mediumCards) * 0.5;

        if (hand.size() <= 5) {
            aggressiveScore += (7 - hand.size()) * 1.5;
        }

        Log.d(TAG, "本地分析 - highCards=" + highCards + ", pairs=" + pairs + ", triples=" + triples +
              ", lowCards=" + lowCards + ", aggressiveScore=" + aggressiveScore +
              ", conservativeScore=" + conservativeScore);

        if (aggressiveScore > conservativeScore * 1.3) {
            return "激进";
        } else if (conservativeScore > aggressiveScore * 1.3) {
            return "保守";
        } else {
            return "均衡";
        }
    }

    private String buildHandStylePrompt(List<com.example.cardgame.model.Card> hand) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个锄大地专家。请分析以下手牌，判断玩家应该采取激进、保守还是均衡的风格。\n");
        sb.append("手牌：");
        for (com.example.cardgame.model.Card card : hand) {
            sb.append(card.getRank().getDisplayName()).append(card.getSuit().getSymbol()).append(" ");
        }
        sb.append("\n\n只回答三个词之一：激进、保守、均衡。不要有其他解释。");
        return sb.toString();
    }

    private String parseStyleFromResponse(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }
        if (response.contains("激进") || response.contains("aggressive") || response.contains("Aggressive")) {
            return "激进";
        }
        if (response.contains("保守") || response.contains("defensive") || response.contains("Defensive")) {
            return "保守";
        }
        if (response.contains("均衡") || response.contains("balanced") || response.contains("Balanced")) {
            return "均衡";
        }
        return null;
    }
}
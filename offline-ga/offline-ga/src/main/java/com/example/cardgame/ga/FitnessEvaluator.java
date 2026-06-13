package com.example.cardgame.ga;

import com.example.cardgame.ai.AIDecisionStrategy;
import com.example.cardgame.ai.OfflineMonteCarloStrategy;
import com.example.cardgame.ai.StrongAIDecisionStrategy;
import com.example.cardgame.model.*;
import java.util.*;

public class FitnessEvaluator {

    private static final int GAMES_PER_EVAL = 5;   // 可以调小以加快速度
    private static final int NUM_STRONG_OPPONENTS = 3;

    public static double evaluate(Chromosome chromosome) {
        double totalScore = 0;
        for (int i = 0; i < GAMES_PER_EVAL; i++) {
            try {
                totalScore += runSingleGame(chromosome);
            } catch (Exception e) {
                System.err.println("Error in game " + i + ": " + e.getMessage());
            }
        }
        return totalScore / GAMES_PER_EVAL;
    }

    private static double runSingleGame(Chromosome chromosome) {
        // 待评估策略
        AIDecisionStrategy candidate = new OfflineMonteCarloStrategy(chromosome);
        // 强对手策略
        AIDecisionStrategy strong = new StrongAIDecisionStrategy();

        // 随机分配哪个玩家是待评估 AI（避免位置偏好）
        int candidatePosition = new Random().nextInt(4);
        AIDecisionStrategy[] strategies = new AIDecisionStrategy[4];
        for (int i = 0; i < 4; i++) {
            strategies[i] = (i == candidatePosition) ? candidate : strong;
        }

        // 发牌
        List<Card> deck = generateDeck();
        Collections.shuffle(deck);
        @SuppressWarnings("unchecked")
        List<Card>[] hands = new List[4];
        for (int i = 0; i < 4; i++) {
            hands[i] = new ArrayList<>(deck.subList(i * 13, (i + 1) * 13));
        }

        int[] scores = Tournament.playGame(strategies, hands);
        // 返回候选 AI 的得分（第一名 3 分，第二名 2 分，第三名 1 分，第四名 0 分）
        return scores[candidatePosition];
    }

    private static List<Card> generateDeck() {
        List<Card> deck = new ArrayList<>();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(new Card(suit.name() + "_" + rank.name(), suit, rank));
            }
        }
        return deck;
    }
}
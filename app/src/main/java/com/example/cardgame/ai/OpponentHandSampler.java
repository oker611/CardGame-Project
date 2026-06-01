package com.example.cardgame.ai;

import com.example.cardgame.model.*;
import com.example.cardgame.util.CardTracker;
import java.util.*;
import java.util.stream.Collectors;

public class OpponentHandSampler {

    public static class World {
        public final List<List<Card>> opponentHands; // 索引0,1,2
        public World(List<Card> opp1, List<Card> opp2, List<Card> opp3) {
            this.opponentHands = Arrays.asList(opp1, opp2, opp3);
        }
    }

    public List<World> sampleWorlds(Player aiPlayer, GameState gameState, CardTracker cardTracker, int numSamples) {
        List<World> worlds = new ArrayList<>();

        // 所有牌
        Set<Card> allCards = getAllCards();
        // 去掉AI手牌
        allCards.removeAll(aiPlayer.getHandCards());
        // 去掉已打出的牌（使用CardTracker）
        Set<Card> playedCards = new HashSet<>(cardTracker.getPlayedCards());
        allCards.removeAll(playedCards);
        List<Card> remaining = new ArrayList<>(allCards);

        // 各对手手牌张数
        int[] opponentCounts = new int[3];
        int idx = 0;
        for (Player p : gameState.getPlayers()) {
            if (!p.getPlayerId().equals(aiPlayer.getPlayerId())) {
                opponentCounts[idx++] = p.getHandCards().size();
            }
        }

        Random rng = new Random();
        for (int s = 0; s < numSamples; s++) {
            // 使用概率加权随机采样
            List<Card> sampled = weightedSampleWithoutReplacement(remaining, 
                    opponentCounts[0] + opponentCounts[1] + opponentCounts[2], cardTracker, rng);
            
            int pos = 0;
            List<Card> opp1 = new ArrayList<>(sampled.subList(pos, pos + opponentCounts[0]));
            pos += opponentCounts[0];
            List<Card> opp2 = new ArrayList<>(sampled.subList(pos, pos + opponentCounts[1]));
            pos += opponentCounts[1];
            List<Card> opp3 = new ArrayList<>(sampled.subList(pos, sampled.size()));
            worlds.add(new World(opp1, opp2, opp3));
        }
        return worlds;
    }

    /**
     * ========== 概率加权采样（不放回）==========
     * 根据剩余概率加权，让采样更符合真实分布
     */
    private List<Card> weightedSampleWithoutReplacement(List<Card> pool, int count, 
                                                        CardTracker cardTracker, Random rng) {
        List<Card> result = new ArrayList<>();
        List<Card> remaining = new ArrayList<>(pool);
        
        for (int i = 0; i < count && !remaining.isEmpty(); i++) {
            // 计算每个牌的权重（基于剩余概率）
            double[] weights = new double[remaining.size()];
            double totalWeight = 0;
            for (int j = 0; j < remaining.size(); j++) {
                double prob = cardTracker.getRemainingProbability(remaining.get(j));
                weights[j] = Math.max(prob, 0.01); // 最低权重避免零概率
                totalWeight += weights[j];
            }
            
            // 轮盘赌选择
            double r = rng.nextDouble() * totalWeight;
            double cumulative = 0;
            int selectedIdx = 0;
            for (int j = 0; j < weights.length; j++) {
                cumulative += weights[j];
                if (r <= cumulative) {
                    selectedIdx = j;
                    break;
                }
            }
            
            result.add(remaining.remove(selectedIdx));
        }
        
        return result;
    }

    private Set<Card> getAllCards() {
        Set<Card> set = new HashSet<>();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                String cardId = suit.name() + "_" + rank.name();
                set.add(new Card(cardId, suit, rank));
            }
        }
        return set;
    }
}
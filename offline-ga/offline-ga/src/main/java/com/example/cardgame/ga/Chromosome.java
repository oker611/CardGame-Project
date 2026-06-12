package com.example.cardgame.ga;

import java.util.Random;

public class Chromosome {
    public int numSamples;
    public int topKCandidates;
    public double earlyBigCardBonus;
    public double fiveCardPenalty;
    public double midSuppressBonus;
    public double lateFastBonus;
    public double aggression;
    public double defense;
    // 新增参数
    public double lateTwoBonus;          // 残局单2奖励
    public double straightKeepBonus;     // 开局保留顺子的倾向
    public double midControlThreshold;   // 中盘争牌权的牌力阈值
    public double simulatedAggression;   // 模拟对手的激进程度

    private static final Random random = new Random();

    public Chromosome() {
        this.numSamples = 6;
        this.topKCandidates = 3;
        this.earlyBigCardBonus = 0.5;
        this.fiveCardPenalty = -1.0;
        this.midSuppressBonus = 0.3;
        this.lateFastBonus = 0.8;
        this.aggression = 0.5;
        this.defense = 0.5;
        this.lateTwoBonus = 1.0;
        this.straightKeepBonus = 0.5;
        this.midControlThreshold = 0.5;
        this.simulatedAggression = 0.5;
    }

    public Chromosome(int numSamples, int topKCandidates, double earlyBigCardBonus,
                     double fiveCardPenalty, double midSuppressBonus, double lateFastBonus,
                     double aggression, double defense, double lateTwoBonus,
                     double straightKeepBonus, double midControlThreshold, double simulatedAggression) {
        this.numSamples = clampInt(numSamples, 0);
        this.topKCandidates = clampInt(topKCandidates, 1);
        this.earlyBigCardBonus = earlyBigCardBonus;
        this.fiveCardPenalty = fiveCardPenalty;
        this.midSuppressBonus = midSuppressBonus;
        this.lateFastBonus = lateFastBonus;
        this.aggression = aggression;
        this.defense = defense;
        this.lateTwoBonus = lateTwoBonus;
        this.straightKeepBonus = straightKeepBonus;
        this.midControlThreshold = midControlThreshold;
        this.simulatedAggression = simulatedAggression;
    }
    
    // 兼容旧代码的8参数构造器
    public Chromosome(int numSamples, int topKCandidates, double earlyBigCardBonus,
                     double fiveCardPenalty, double midSuppressBonus, double lateFastBonus,
                     double aggression, double defense) {
        this(numSamples, topKCandidates, earlyBigCardBonus, fiveCardPenalty,
             midSuppressBonus, lateFastBonus, aggression, defense,
             1.0, 0.5, 0.5, 0.5); // 默认值
    }

    private int clampInt(int value, int index) {
        if (index == 0) {
            return Math.max(5, Math.min(value, 10));  // 5-10
        } else if (index == 1) {
            return Math.max(3, Math.min(value, 5));  // 3-5
        }
        return value;
    }

    public static Chromosome randomChromosome() {
        return new Chromosome(
            5 + random.nextInt(6),   // 5-10
            3 + random.nextInt(3),   // 3-5
            random.nextDouble() * 1.0,        // 0-1
            -0.5 + random.nextDouble() * 1.0, // -0.5 to 0.5
            random.nextDouble() * 0.5,        // 0-0.5
            random.nextDouble() * 1.0,        // 0-1
            random.nextDouble(),              // 0-1
            random.nextDouble(),              // 0-1
            random.nextDouble() * 1.0,        // 0-1 (lateTwoBonus)
            random.nextDouble() * 0.5,        // 0-0.5 (straightKeepBonus)
            random.nextDouble(),              // 0-1 (midControlThreshold)
            random.nextDouble()               // 0-1 (simulatedAggression)
        );
    }

    public double[] toArray() {
        return new double[]{
            numSamples, topKCandidates, earlyBigCardBonus,
            fiveCardPenalty, midSuppressBonus, lateFastBonus,
            aggression, defense, lateTwoBonus,
            straightKeepBonus, midControlThreshold, simulatedAggression
        };
    }

    public static Chromosome fromArray(double[] params) {
        return new Chromosome(
            (int) params[0], (int) params[1], params[2],
            params[3], params[4], params[5], params[6], params[7],
            params[8], params[9], params[10], params[11]
        );
    }

    public void mutate(double rate) {
        for (int i = 0; i < 2; i++) {
            if (random.nextDouble() < rate) {
                int delta = (int)((random.nextDouble() - 0.5) * 4); // 更大的变异范围
                if (i == 0) {
                    numSamples = clampInt(numSamples + delta, 0);
                } else {
                    topKCandidates = clampInt(topKCandidates + delta, 1);
                }
            }
        }

        for (int i = 2; i < 12; i++) {
            if (random.nextDouble() < rate) {
                double delta = (random.nextDouble() - 0.5) * mutationRange(i);
                setParam(i, clamp(getParam(i) + delta, i));
            }
        }
    }

    private double mutationRange(int index) {
        return 0.3;
    }

    private double getParam(int index) {
        switch(index) {
            case 0: return numSamples;
            case 1: return topKCandidates;
            case 2: return earlyBigCardBonus;
            case 3: return fiveCardPenalty;
            case 4: return midSuppressBonus;
            case 5: return lateFastBonus;
            case 6: return aggression;
            case 7: return defense;
            case 8: return lateTwoBonus;
            case 9: return straightKeepBonus;
            case 10: return midControlThreshold;
            case 11: return simulatedAggression;
            default: return 0;
        }
    }

    private void setParam(int index, double value) {
        switch(index) {
            case 0: numSamples = (int) value; break;
            case 1: topKCandidates = (int) value; break;
            case 2: earlyBigCardBonus = value; break;
            case 3: fiveCardPenalty = value; break;
            case 4: midSuppressBonus = value; break;
            case 5: lateFastBonus = value; break;
            case 6: aggression = value; break;
            case 7: defense = value; break;
            case 8: lateTwoBonus = value; break;
            case 9: straightKeepBonus = value; break;
            case 10: midControlThreshold = value; break;
            case 11: simulatedAggression = value; break;
        }
    }

    private double clamp(double value, int index) {
        switch(index) {
            case 2: return Math.max(0.0, Math.min(value, 2.0));         // earlyBigCardBonus
            case 3: return Math.max(-1.0, Math.min(value, 1.0));        // fiveCardPenalty
            case 4: return Math.max(0.0, Math.min(value, 1.0));         // midSuppressBonus
            case 5: return Math.max(0.0, Math.min(value, 3.0));         // lateFastBonus
            case 6: return Math.max(0.0, Math.min(value, 1.0));         // aggression
            case 7: return Math.max(0.0, Math.min(value, 1.0));         // defense
            case 8: return Math.max(0.0, Math.min(value, 2.0));         // lateTwoBonus
            case 9: return Math.max(0.0, Math.min(value, 1.0));         // straightKeepBonus
            case 10: return Math.max(0.0, Math.min(value, 1.0));        // midControlThreshold
            case 11: return Math.max(0.0, Math.min(value, 1.0));        // simulatedAggression
            default: return Math.max(-10.0, Math.min(value, 10.0));
        }
    }

    public static Chromosome crossover(Chromosome p1, Chromosome p2) {
        double[] a1 = p1.toArray();
        double[] a2 = p2.toArray();
        int point = random.nextInt(a1.length);
        double[] child = new double[a1.length];
        for (int i = 0; i < child.length; i++) {
            child[i] = i < point ? a1[i] : a2[i];
        }
        return fromArray(child);
    }

    public Chromosome clone() {
        return new Chromosome(
            numSamples, topKCandidates, earlyBigCardBonus,
            fiveCardPenalty, midSuppressBonus, lateFastBonus,
            aggression, defense, lateTwoBonus,
            straightKeepBonus, midControlThreshold, simulatedAggression
        );
    }
}
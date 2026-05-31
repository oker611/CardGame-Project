package com.example.cardgame.ai;

import com.example.cardgame.model.*;
import com.example.cardgame.rule.PlayValidator;
import com.example.cardgame.rule.RuleEngine;
import com.example.cardgame.util.CardTracker;
import java.util.*;
import java.util.concurrent.*;

public class MonteCarloAIDecisionStrategy implements AIDecisionStrategy {

    // 调试开关（正式版改为 false）
    private static final boolean DEBUG_AI = false;

    // 可调参数（遗传算法优化结果 + 手动调优）
    private static final int NUM_SAMPLES = 20;          // 蒙特卡洛模拟世界数量（提高到20）
    private static final int TOP_K_CANDIDATES = 4;      // 候选动作截断数
    private static final long DECISION_TIMEOUT_MS = 1000; // 决策超时（毫秒，略微增加）

    private final CandidateGenerator candidateGenerator;
    private final OpponentHandSampler opponentHandSampler;
    private final MonteCarloSimulator monteCarloSimulator;
    private final PhaseManager phaseManager;
    private final RuleEngine ruleEngine;
    private final CardTracker cardTracker = new CardTracker();

    // AI玩家配置（默认最强）
    private AIPlayerProfile profile;

    // 对手风格档案（key=playerId, value=对手风格配置）
    private Map<String, AIPlayerProfile> opponentProfiles = new HashMap<>();

    // 出牌失败计数（用于兜底逻辑）
    private int consecutiveFailCount = 0;

    // 自适应因子（由 AdaptiveAIDecisionStrategy 设置）
    private double aggressivenessFactor = 1.0;
    private double defenseFactor = 1.0;

    public MonteCarloAIDecisionStrategy() {
        this.ruleEngine = new RuleEngine();
        // 先创建 phaseManager，因为 candidateGenerator 需要它
        this.phaseManager = new PhaseManager(ruleEngine);
        // 再创建 profile（默认强）
        this.profile = new AIPlayerProfile(AIPlayerProfile.LEVEL_STRONG);
        // 最后创建 candidateGenerator，传入 phaseManager 和 profile
        this.candidateGenerator = new CandidateGenerator(ruleEngine, TOP_K_CANDIDATES, phaseManager, profile);
        this.opponentHandSampler = new OpponentHandSampler();
        this.monteCarloSimulator = new MonteCarloSimulator(ruleEngine, NUM_SAMPLES);
    }

    @Override
    public List<Card> decidePlay(Player aiPlayer, GameState gameState) {
        List<Card> hand = aiPlayer.getHandCards();
        Play lastPlay = gameState.getLastPlay();
        boolean isFirstRound = gameState.isOpeningTurn();
        boolean isFirstTurn = (lastPlay == null || lastPlay.isEmpty());

        // ========== 兜底逻辑：连续失败后强制过牌 ==========
        if (consecutiveFailCount >= 2) {
            System.out.println("[MonteCarloAI] 连续失败2次，强制过牌一次");
            consecutiveFailCount = 0; // 重置计数
            return null; // 过牌
        }

        // ========== 主动压制：对手手牌≤3且AI拥有出牌权 ==========
        if (!isFirstTurn && (lastPlay == null || lastPlay.isEmpty())) {
            // 获取人类玩家
            Player humanPlayer = null;
            for (Player p : gameState.getPlayers()) {
                if (p.getType() == PlayerType.HUMAN) {
                    humanPlayer = p;
                    break;
                }
            }
            if (humanPlayer != null && humanPlayer.getHandCards().size() <= 3) {
                List<Play> allPlays = candidateGenerator.generate(hand, null, true, true);
                if (!allPlays.isEmpty()) {
                    // 优先出对子或三张，其次出大单牌
                    allPlays.sort((p1, p2) -> {
                        // 优先按牌数排序（多张牌 > 单牌）
                        int countCompare = Integer.compare(p2.getCards().size(), p1.getCards().size());
                        if (countCompare != 0) return countCompare;
                        // 同牌数按总点数比较
                        int sum1 = p1.getCards().stream().mapToInt(c -> c.getRank().getWeight()).sum();
                        int sum2 = p2.getCards().stream().mapToInt(c -> c.getRank().getWeight()).sum();
                        return Integer.compare(sum2, sum1);
                    });
                    Play best = allPlays.get(0);
                    System.out.println("[MonteCarloAI] 主动压制：对手剩" + humanPlayer.getHandCards().size() + "张，出" + best.getCards());
                    return best.getCards();
                }
            }
        }

        // ========== 多阶段残局防守（按对手剩余手牌数） ==========
        if (!isFirstTurn && lastPlay != null && !lastPlay.isEmpty() && lastPlay.getPlayerId() != null) {
            Player lastPlayer = gameState.getPlayerById(lastPlay.getPlayerId());
            if (lastPlayer != null) {
                int opponentCards = lastPlayer.getHandCards().size();
                // 剩1张：必须阻止
                // 剩2张：积极阻断（尽可能压牌，优先用中等牌）
                // 剩3张：开始警惕（如果出牌较大则压，否则保留实力）
                if (opponentCards <= 3) {
                    System.out.println("[MonteCarloAI] 残局防守: 对手 " + lastPlayer.getPlayerId() + " 剩 " + opponentCards + " 张牌");
                    
                    List<Play> candidates = candidateGenerator.generate(hand, lastPlay, isFirstRound, isFirstTurn);
                    List<Play> canBeat = new ArrayList<>();
                    for (Play p : candidates) {
                        if (!p.isEmpty()) {
                            PlayValidator.ValidationResult result = ruleEngine.validatePlay(
                                p.getCards(), lastPlay.getCards(), isFirstRound, isFirstTurn);
                            if (result.valid) {
                                canBeat.add(p);
                            }
                        }
                    }
                    
                    if (!canBeat.isEmpty()) {
                        if (opponentCards == 1) {
                            // 强制压：选最小的能压牌（节省大牌）
                            canBeat.sort(Comparator.comparingInt(p ->
                                p.getCards().stream().mapToInt(c -> c.getRank().getWeight()).sum()));
                            Play best = canBeat.get(0);
                            System.out.println("[MonteCarloAI] 1张牌，紧急压牌: " + best.getCards());
                            return best.getCards();
                        } else if (opponentCards == 2) {
                            // 积极压：选最大的能压牌（确保压住）
                            canBeat.sort((p1, p2) ->
                                Integer.compare(p2.getCards().stream().mapToInt(c -> c.getRank().getWeight()).sum(),
                                                p1.getCards().stream().mapToInt(c -> c.getRank().getWeight()).sum()));
                            Play best = canBeat.get(0);
                            System.out.println("[MonteCarloAI] 2张牌，主动阻断: " + best.getCards());
                            return best.getCards();
                        } else { // opponentCards == 3
                            // 警惕：如果上家出牌较大（比如点数 > 10）则压，否则保留
                            int lastValue = lastPlay.getCards().stream()
                                            .mapToInt(c -> c.getRank().getWeight()).max().orElse(0);
                            if (lastValue >= 10) {
                                // 选择最小的能压牌
                                canBeat.sort(Comparator.comparingInt(p ->
                                    p.getCards().stream().mapToInt(c -> c.getRank().getWeight()).sum()));
                                Play best = canBeat.get(0);
                                System.out.println("[MonteCarloAI] 3张牌，警惕性压牌: " + best.getCards());
                                return best.getCards();
                            } else {
                                System.out.println("[MonteCarloAI] 3张牌，上家出小牌(值=" + lastValue + ")，暂不压");
                            }
                        }
                    } else {
                        System.out.println("[MonteCarloAI] 残局防守失败: 无牌能压");
                    }
                }
            }
        }

        // ========== 高级策略层（基于AIProfile）==========
        
        // 1. 首轮优先出多张牌（对子、三带等）
        if (isFirstTurn && hand.size() > 1 && profile.isKeepBigPattern()) {
            List<Play> candidates = candidateGenerator.generate(hand, lastPlay, true, true);
            if (!candidates.isEmpty()) {
                // 按牌数降序排序，优先出多张
                candidates.sort((a, b) -> b.getCards().size() - a.getCards().size());
                Play bestMulti = candidates.get(0);
                if (bestMulti.getCards().size() >= 2) {
                    System.out.println("[MonteCarloAI] 首轮出多张: " + bestMulti.getCards());
                    return bestMulti.getCards();
                }
            }
        }

        // 2. 开局保守策略：手牌多于11张时出最小合法牌（确保开局不出大牌）
        if (hand.size() > 11 && !isFirstTurn) {
            List<Card> smallPlay = getSmallestValidPlay(hand, lastPlay);
            if (smallPlay != null) {
                System.out.println("[MonteCarloAI] 开局保守出小牌: " + smallPlay);
                return smallPlay;
            }
        }

        // 2.5 中盘谨慎策略：手牌6-11张时，优先出小牌
        if (hand.size() >= 6 && hand.size() <= 11 && !isFirstTurn) {
            List<Card> smallPlay = getSmallestValidPlay(hand, lastPlay);
            if (smallPlay != null) {
                System.out.println("[MonteCarloAI] 中盘谨慎出小牌: " + smallPlay);
                return smallPlay;
            }
        }

        // 3. 中后期一锤定音（手牌<=5张且对手大牌耗尽）
        if (hand.size() <= 5 && profile.getMidAggression() > 0.7 && !isFirstTurn) {
            if (areOpponentBigCardsDepleted()) {
                List<Card> bestPattern = getBestBigPattern(hand);
                if (bestPattern != null) {
                    System.out.println("[MonteCarloAI] 一锤定音: " + bestPattern);
                    return bestPattern;
                }
            }
        }

        // 4. 拆牌逼迫策略：对手手牌少且可能蓄力大牌型时，优先出对子/三带
        if (!isFirstTurn && shouldForceBreakOpponentPattern(gameState, aiPlayer)) {
            List<Play> candidates = candidateGenerator.generate(hand, lastPlay, isFirstRound, isFirstTurn);
            List<Play> validMultiCard = candidates.stream()
                    .filter(p -> !p.isEmpty() && p.getCards().size() >= 2)
                    // 确保能压住上家的出牌
                    .filter(p -> lastPlay == null || lastPlay.isEmpty() || 
                            ruleEngine.validatePlay(p.getCards(), lastPlay.getCards(), isFirstRound, isFirstTurn).valid)
                    .collect(java.util.stream.Collectors.toList());
            if (!validMultiCard.isEmpty()) {
                // 选择最小的对子/三带（避免浪费大牌）
                validMultiCard.sort(Comparator.comparingInt(p -> p.getCards().get(0).getRank().getWeight()));
                Play chosen = validMultiCard.get(0);
                System.out.println("[MonteCarloAI] 拆牌逼迫策略: 出 " + chosen.getCards());
                return chosen.getCards();
            }
        }

        // 5. 大牌型终结时机判断：手牌少或对手快赢时直接出
        // 增加检查：只有上家没出牌或上家出的也是五张牌型时才考虑出五张牌型
        if (!isFirstTurn && (lastPlay == null || lastPlay.isEmpty() || lastPlay.getCards().size() == 5)) {
            List<Card> bigPattern = getBestBigPattern(hand);
            if (bigPattern != null && shouldPlayBigPatternNow(gameState, aiPlayer)) {
                // 检查合法性：能压住上家或上家没出牌
                if (lastPlay == null || lastPlay.isEmpty() || canBeatBigPattern(bigPattern, lastPlay.getCards())) {
                    System.out.println("[MonteCarloAI] 时机成熟，出大牌型终结: " + bigPattern);
                    return bigPattern;
                }
            }
        }

        // ========== 原有策略层 ==========

        // 0. 炸弹优先：如果对手手牌数 <= 5 且 AI 有炸弹，直接出炸弹
        if (!isFirstTurn && isEndGamePhase(gameState)) {
            Card bombCard = findBestBomb(hand);
            if (bombCard != null) {
                List<Card> bombPlay = getBombCards(hand, bombCard.getRank());
                System.out.println("[MonteCarloAI] 炸弹优先: " + bombPlay);
                return bombPlay;
            }
        }

        // 1.5 五张牌型压制检测（锄大地专属）
        // 只在上家出五张牌型时才尝试压制
        if (lastPlay != null && !lastPlay.isEmpty() && lastPlay.getCards().size() == 5) {
            List<Card> beatPattern = phaseManager.findBeatForBigTwoPattern(aiPlayer, lastPlay);
            if (beatPattern != null && !beatPattern.isEmpty()) {
                // 使用 canBeatBigPattern 检查合法性
                if (canBeatBigPattern(beatPattern, lastPlay.getCards())) {
                    System.out.println("[MonteCarloAI] 强制压制五张牌型: " + beatPattern);
                    return beatPattern;
                }
            }
        }

        // 1.6 出2后的连贯出牌：利用出牌权出大牌型或对子
        if (lastPlay != null && !lastPlay.isEmpty()) {
            // 检查上一手是否是AI自己出的2
            if (lastPlay.getPlayerId() != null && lastPlay.getPlayerId().equals(aiPlayer.getPlayerId())) {
                List<Card> lastCards = lastPlay.getCards();
                if (lastCards.size() == 1 && lastCards.get(0).getRank() == Rank.TWO) {
                    // 出2后优先出大牌型
                    List<Card> bigPattern = getBestBigPattern(hand);
                    if (bigPattern != null) {
                        System.out.println("[MonteCarloAI] 出2后立即出大牌型: " + bigPattern);
                        return bigPattern;
                    }
                    // 否则出对子
                    List<Play> candidates = candidateGenerator.generate(hand, null, false, true);
                    for (Play p : candidates) {
                        if (p.getCards().size() == 2) {
                            System.out.println("[MonteCarloAI] 出2后出对子: " + p.getCards());
                            return p.getCards();
                        }
                    }
                }
            }
        }

        // 2. 生成候选动作
        List<Play> candidates = candidateGenerator.generate(hand, lastPlay, isFirstRound, isFirstTurn);
        if (candidates.isEmpty()) {
            return null; // 无可出牌，Pass
        }
        if (candidates.size() == 1 && candidates.get(0).isEmpty()) {
            return null; // 唯一合法动作是 Pass
        }

        // 更新记牌器：从历史出牌记录初始化
        updateCardTracker(gameState);

        PhaseManager.GamePhase phase = phaseManager.getCurrentPhase(aiPlayer, gameState);
        List<OpponentHandSampler.World> worlds = opponentHandSampler.sampleWorlds(aiPlayer, gameState, cardTracker, NUM_SAMPLES);
        monteCarloSimulator.setOpponentProfiles(opponentProfiles);

        // 异步评估候选动作，避免阻塞主线程（但策略接口是同步的，我们内部使用超时）
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Map<Play, Double>> future = executor.submit(() -> {
            Map<Play, Double> scores = new HashMap<>();
            for (Play candidate : candidates) {
                double rawScore = monteCarloSimulator.evaluate(candidate, aiPlayer, gameState, worlds);
                // 计算出牌后的手牌
                List<Card> handAfterPlay = new ArrayList<>(hand);
                handAfterPlay.removeAll(candidate.getCards());
                double adjusted = phaseManager.adjustScore(rawScore, candidate, phase, aiPlayer, gameState, handAfterPlay);
                
                // 应用自适应因子
                adjusted = applyAdaptiveFactors(adjusted, candidate, lastPlay, gameState, aiPlayer);
                
                scores.put(candidate, adjusted);
            }
            return scores;
        });

        Play bestPlay = candidates.get(0);
        try {
            Map<Play, Double> scores = future.get(DECISION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            double bestScore = Double.NEGATIVE_INFINITY;
            for (Map.Entry<Play, Double> entry : scores.entrySet()) {
                if (entry.getValue() > bestScore) {
                    bestScore = entry.getValue();
                    bestPlay = entry.getKey();
                }
            }
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            // 超时或异常时使用第一个候选（启发式最佳）
            bestPlay = candidates.get(0);
        } finally {
            executor.shutdownNow();
        }

        // ========== 最终合法性检查（兜底）==========
        // 在返回前验证出牌是否合法，避免无效请求
        if (!bestPlay.isEmpty()) {
            List<Card> finalCards = bestPlay.getCards();
            PlayValidator.ValidationResult validation = ruleEngine.validatePlay(
                    finalCards, 
                    lastPlay != null ? lastPlay.getCards() : null,
                    isFirstRound, 
                    isFirstTurn);
            
            if (!validation.valid) {
                System.out.println("[MonteCarloAI] 最终检查发现非法出牌，改为过牌: " + finalCards + " - " + validation.reason);
                return null; // 改为过牌
            }
        }

        return bestPlay.isEmpty() ? null : bestPlay.getCards();
    }

    /**
     * 更新记牌器：从游戏状态的历史出牌记录中提取已出牌
     */
    private void updateCardTracker(GameState gameState) {
        // 如果无法从 state 获取历史，就先不初始化，依靠事件监听
        // cardTracker 会通过 AIEventListener 自动更新
    }

    /**
     * 获取记牌器实例（供外部事件监听器调用）
     */
    public CardTracker getCardTracker() {
        return cardTracker;
    }

    /**
     * 设置AI玩家配置
     */
    public void setProfile(AIPlayerProfile profile) {
        this.profile = profile;
        // 同步更新 candidateGenerator 的 profile
        if (this.candidateGenerator != null) {
            this.candidateGenerator.setProfile(profile);
        }
    }

    /**
     * 获取当前AI配置
     */
    public AIPlayerProfile getProfile() {
        return profile;
    }

    public AIPlayerProfile getOpponentProfile(String playerId) {
        return opponentProfiles.get(playerId);
    }

    public void setOpponentProfile(String playerId, AIPlayerProfile opponentProfile) {
        opponentProfiles.put(playerId, opponentProfile);
    }

    public Map<String, AIPlayerProfile> getOpponentProfiles() {
        return opponentProfiles;
    }

    public void setAggressivenessFactor(double factor) {
        this.aggressivenessFactor = Math.max(0.0, Math.min(2.0, factor));
    }

    public double getAggressivenessFactor() {
        return aggressivenessFactor;
    }

    public void setDefenseFactor(double factor) {
        this.defenseFactor = Math.max(0.0, Math.min(2.0, factor));
    }

    public double getDefenseFactor() {
        return defenseFactor;
    }

    /**
     * 应用自适应因子调整评分
     * @param score 原始评分
     * @param candidate 候选出牌
     * @param lastPlay 上家出牌
     * @param gameState 游戏状态
     * @param aiPlayer AI玩家
     * @return 调整后的评分
     */
    private double applyAdaptiveFactors(double score, Play candidate, Play lastPlay, 
                                        GameState gameState, Player aiPlayer) {
        if (aggressivenessFactor == 1.0 && defenseFactor == 1.0) {
            return score;
        }

        double adjustedScore = score;
        boolean isInitiativePlay = (lastPlay == null || lastPlay.isEmpty());
        double emergencyFactor = 1.0;  // 用于后续乘算因子

        // 1. 大牌惩罚（主动出牌时，消耗 2/A 扣分）
        if (isInitiativePlay && candidate != null && !candidate.isEmpty()) {
            int bigCount = 0;
            for (Card card : candidate.getCards()) {
                if (card.getRank() == Rank.ACE || card.getRank() == Rank.TWO) {
                    bigCount++;
                }
            }
            if (bigCount > 0) {
                double penalty = bigCount * 1.0;
                int totalCards = candidate.getCards().size();
                if (totalCards >= 3) {
                    penalty += bigCount * 0.8;
                }
                adjustedScore -= penalty * (1.5 - aggressivenessFactor);
            }
        }

        // 2. 胜者优势奖励
        if (isInitiativePlay && candidate != null && !candidate.isEmpty() && gameState != null && aiPlayer != null) {
            String lastWinnerId = gameState.getLastWinnerId();
            if (lastWinnerId != null && lastWinnerId.equals(aiPlayer.getPlayerId())) {
                int totalCards = candidate.getCards().size();
                if (totalCards >= 2) {
                    double bonus = 8.0 * totalCards;
                    adjustedScore += bonus;
                    if (DEBUG_AI) {
                        System.out.println("[MonteCarloAI] 胜者奖励：组合牌 +" + bonus);
                    }
                } else if (totalCards == 1) {
                    int weight = candidate.getCards().get(0).getRank().getWeight();
                    if (weight <= 7) {
                        adjustedScore -= 15.0;
                        if (DEBUG_AI) {
                            System.out.println("[MonteCarloAI] 胜者惩罚：小单张 -15");
                        }
                    }
                }
            }
        }

        // 3. 全局紧急模式（对手手牌 ≤ 3 时，主动出牌禁止单张，鼓励组合牌）
        if (isInitiativePlay && candidate != null && !candidate.isEmpty() && gameState != null && aiPlayer != null) {
            int minOpponentHandSize = getMinOpponentHandSize(gameState, aiPlayer);
            if (minOpponentHandSize <= 3) {
                int totalCards = candidate.getCards().size();
                if (totalCards == 1) {
                    // 任何单张都严重惩罚
                    adjustedScore -= 12.0;
                    if (DEBUG_AI) {
                        System.out.println("[MonteCarloAI] 紧急模式：单张 -12分");
                    }
                } else {
                    // 组合牌重奖：每张牌 +15 分
                    double comboBonus = 15.0 * totalCards;
                    adjustedScore += comboBonus;
                    if (DEBUG_AI) {
                        System.out.println("[MonteCarloAI] 紧急模式：组合牌 +" + comboBonus);
                    }
                }
                emergencyFactor = Math.max(emergencyFactor, 2.0);  // 进攻性大幅提高
            }
        }

        // 4. 压牌紧急奖励（对手手牌 ≤ 2 时，压牌获得高分）
        if (!isInitiativePlay && candidate != null && !candidate.isEmpty() && gameState != null && aiPlayer != null) {
            int minOpponentHandSize = getMinOpponentHandSize(gameState, aiPlayer);
            if (minOpponentHandSize <= 2) {
                double urgencyBonus = 15.0;
                int myHandSize = aiPlayer.getHandCards().size();
                int handAfterPlay = myHandSize - candidate.getCards().size();
                if (handAfterPlay <= 1) {
                    urgencyBonus += 30.0;
                    if (DEBUG_AI) {
                        System.out.println("[MonteCarloAI] 压牌后即将获胜！额外+30分");
                    }
                }
                adjustedScore += urgencyBonus;
                if (DEBUG_AI) {
                    System.out.println("[MonteCarloAI] 紧急压牌奖励 +" + urgencyBonus);
                }
                emergencyFactor = Math.max(emergencyFactor, 1.8);
            }
        }

        // 5. 胜利冲刺（自己手牌 ≤ 2 时，出大牌加分）
        if (isInitiativePlay && candidate != null && !candidate.isEmpty() && gameState != null && aiPlayer != null) {
            int myHandSize = aiPlayer.getHandCards().size();
            if (myHandSize <= 2) {
                int totalCards = candidate.getCards().size();
                if (totalCards == 1) {
                    int weight = candidate.getCards().get(0).getRank().getWeight();
                    if (weight >= 10) {  // 10/J/Q/K/A/2 视为大牌
                        adjustedScore += 20.0;
                        if (DEBUG_AI) {
                            System.out.println("[MonteCarloAI] 胜利冲刺：出大牌 +20");
                        }
                    }
                } else {
                    adjustedScore += 15.0 * totalCards;
                    if (DEBUG_AI) {
                        System.out.println("[MonteCarloAI] 胜利冲刺：出组合牌 +" + (15.0 * totalCards));
                    }
                }
            }
        }

        // 6. 原有因子调整（应用紧急因子）
        boolean isAggressivePlay = isAggressivePlay(candidate, lastPlay);
        boolean isDefensivePlay = isDefensivePlay(candidate, lastPlay);

        if (isAggressivePlay) {
            adjustedScore *= aggressivenessFactor * emergencyFactor;
        } else {
            adjustedScore *= emergencyFactor;
        }
        if (isDefensivePlay) {
            adjustedScore *= defenseFactor;
        }

        return adjustedScore;
    }
    
    private boolean isAggressivePlay(Play candidate, Play lastPlay) {
        if (candidate == null || candidate.isEmpty()) return false;
        
        List<Card> cards = candidate.getCards();
        if (cards.isEmpty()) return false;
        
        double avgWeight = cards.stream()
                .mapToDouble(c -> c.getRank().getWeight())
                .average()
                .orElse(0);
        
        return avgWeight >= 10;
    }
    
    private boolean isDefensivePlay(Play candidate, Play lastPlay) {
        if (candidate == null || candidate.isEmpty()) return true;
        
        if (lastPlay == null || lastPlay.isEmpty()) return false;
        
        List<Card> cards = candidate.getCards();
        if (cards.isEmpty()) return true;
        
        double avgWeight = cards.stream()
                .mapToDouble(c -> c.getRank().getWeight())
                .average()
                .orElse(0);
        
        return avgWeight < 8;
    }

    /**
     * 找到能压制最后出牌的最佳单牌（优先2，其次A）
     * @param hand AI手牌
     * @param lastPlayed 上家出的牌
     * @return 最佳压制牌，如果无法压制则返回null
     */
    private Card findBestBeatCard(List<Card> hand, List<Card> lastPlayed) {
        // 先找2，2可以压任何牌
        Card twoCard = hand.stream()
                .filter(c -> c.getRank() == Rank.TWO)
                .findFirst()
                .orElse(null);
        if (twoCard != null) {
            return twoCard;
        }

        // 再找A
        Card aceCard = hand.stream()
                .filter(c -> c.getRank() == Rank.ACE)
                .findFirst()
                .orElse(null);
        if (aceCard != null) {
            // A可以压除了2以外的单牌
            if (lastPlayed.size() == 1) {
                Card lastCard = lastPlayed.get(0);
                if (lastCard.getRank() != Rank.TWO) {
                    return aceCard;
                }
            }
        }

        return null;
    }

    // ========== 炸弹检测相关方法 ==========

    /**
     * 查找最佳炸弹（四张相同）
     * @param hand AI手牌
     * @return 炸弹中的一张牌，如果没有炸弹返回null
     */
    private Card findBestBomb(List<Card> hand) {
        // 检查四张相同
        for (Card c : hand) {
            long count = hand.stream().filter(card -> card.getRank() == c.getRank()).count();
            if (count == 4) {
                return c;
            }
        }
        return null;
    }

    /**
     * 获取炸弹的所有牌
     * @param hand AI手牌
     * @param rank 炸弹牌的点数
     * @return 炸弹的四张牌
     */
    private List<Card> getBombCards(List<Card> hand, Rank rank) {
        // 四张相同
        return hand.stream()
                .filter(c -> c.getRank() == rank)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 判断是否进入残局阶段（对手平均手牌数 <= 5）
     */
    private boolean isEndGamePhase(GameState gameState) {
        int totalOpponentCards = 0;
        int opponentCount = 0;
        for (Player p : gameState.getPlayers()) {
            if (!p.getPlayerId().equals("AI")) { // 假设AI玩家ID为"AI"
                totalOpponentCards += p.getHandCards().size();
                opponentCount++;
            }
        }
        if (opponentCount == 0) return false;
        return (totalOpponentCards / opponentCount) <= 5;
    }

    // ========== 高级策略辅助方法 ==========

    /**
     * 获取最小的合法出牌（开局保守策略用）
     * 确保选择可压住上家的最小牌
     */
    private List<Card> getSmallestValidPlay(List<Card> hand, Play lastPlay) {
        List<Play> candidates = candidateGenerator.generate(hand, lastPlay, false, false);
        
        // 过滤出非空且能压住上家的候选
        List<Play> valid = candidates.stream()
                .filter(p -> !p.isEmpty())
                .filter(p -> lastPlay == null || lastPlay.isEmpty() || 
                        ruleEngine.validatePlay(p.getCards(), lastPlay.getCards(), false, false).valid)
                .collect(java.util.stream.Collectors.toList());
        
        if (valid.isEmpty()) return null;
        
        // 按牌值总和升序排序，选最小的
        valid.sort(Comparator.comparingInt(p -> 
            p.getCards().stream().mapToInt(c -> c.getRank().getWeight()).sum()));
        
        return valid.get(0).getCards();
    }

    /**
     * 获取最小的合法单牌（强AI开局强制出小牌用）
     */
    private List<Card> getSmallestSingleCard(List<Card> hand, Play lastPlay) {
        // 生成候选动作
        List<Play> candidates = candidateGenerator.generate(hand, lastPlay, false, false);
        if (candidates.isEmpty()) return null;
        
        // 过滤出单牌且能压住上家的候选
        List<Play> validSingles = candidates.stream()
                .filter(p -> !p.isEmpty() && p.getCards().size() == 1)
                .collect(java.util.stream.Collectors.toList());
        if (validSingles.isEmpty()) return null;
        
        // 按牌值升序排序，选择最小的
        validSingles.sort(Comparator.comparingInt(p -> p.getCards().get(0).getRank().getWeight()));
        
        return validSingles.get(0).getCards();
    }

    /**
     * 检测对手大牌是否已耗尽（利用记牌器）
     */
    private boolean areOpponentBigCardsDepleted() {
        Set<Card> playedCards = cardTracker.getPlayedCards();
        int playedTwos = 0;
        int playedAces = 0;
        
        for (Card c : playedCards) {
            if (c.getRank() == Rank.TWO) playedTwos++;
            else if (c.getRank() == Rank.ACE) playedAces++;
        }
        
        int remainingTwos = 4 - playedTwos;
        int remainingAces = 4 - playedAces;
        
        // 如果剩余2和A都<=1，则风险较小
        return remainingTwos <= 1 && remainingAces <= 1;
    }

    /**
     * 判断是否应该使用"拆牌逼迫"策略
     */
    private boolean shouldForceBreakOpponentPattern(GameState gameState, Player aiPlayer) {
        int opponentMinHandSize = gameState.getPlayers().stream()
                .filter(p -> !p.getPlayerId().equals(aiPlayer.getPlayerId()))
                .mapToInt(p -> p.getHandCards().size())
                .min().orElse(99);
        // 对手手牌数 <= 8，且 AI 手牌数 >= 8（说明还有资本）
        return opponentMinHandSize <= 8 && aiPlayer.getHandCards().size() >= 8;
    }

    /**
     * 判断是否应该现在出大牌型
     */
    private boolean shouldPlayBigPatternNow(GameState gameState, Player aiPlayer) {
        List<Card> hand = aiPlayer.getHandCards();
        int minOpponentHandSize = getMinOpponentHandSize(gameState, aiPlayer);
        
        // 条件1：对手手牌数 <= 3（快赢了）
        if (minOpponentHandSize <= 3) return true;
        
        // 条件2：自己的手牌数 <= 5（可以一次走完）
        if (hand.size() <= 5) return true;
        
        // 条件3：判断该牌型无人能压
        List<Card> bigPattern = getBestBigPattern(hand);
        if (bigPattern != null && isMyBigPatternUnbeatable(bigPattern)) return true;
        
        // 条件4：手牌较少时（<=7张）也可以考虑出
        return hand.size() <= 7;
    }

    /**
     * 获取对手最小手牌数
     */
    private int getMinOpponentHandSize(GameState gameState, Player aiPlayer) {
        return gameState.getPlayers().stream()
                .filter(p -> !p.getPlayerId().equals(aiPlayer.getPlayerId()))
                .mapToInt(p -> p.getHandCards().size())
                .min().orElse(99);
    }

    /**
     * 判断自己的大牌型是否无敌
     * 
     * NOTE: 当前实现为简化版本，基于已出牌数量判断。
     * 后续可增强为真正检查剩余牌堆中是否存在更大的同类型牌。
     */
    private boolean isMyBigPatternUnbeatable(List<Card> bigPattern) {
        if (bigPattern == null || bigPattern.size() != 5) return false;
        
        // 获取牌型类型
        int patternType = getPatternType(bigPattern);
        
        // 获取已出牌数
        Set<Card> playedCards = cardTracker.getPlayedCards();
        int playedCount = playedCards.size();
        
        // 策略：
        // 1. 同花顺(5)或铁支(4)：已出超过一半牌(26张)时认为无敌
        // 2. 葫芦(3)：已出超过35张牌时认为无敌
        // 3. 同花(2)或顺子(1)：已出超过40张牌时认为无敌
        switch (patternType) {
            case 5: // 同花顺
            case 4: // 铁支
                return playedCount > 26;
            case 3: // 葫芦
                return playedCount > 35;
            case 2: // 同花
            case 1: // 顺子
                return playedCount > 40;
            default:
                return false;
        }
    }

    /**
     * 检查大牌型是否能压住上家出牌（锄大地规则）
     * 
     * 牌型优先级（数值越大越强）：
     * 5 - 同花顺 > 4 - 铁支 > 3 - 葫芦 > 2 - 同花 > 1 - 顺子
     * 
     * 规则：
     * - 炸弹（四张相同）或同花顺可以压任何牌型
     * - 非五张牌型（单张、对子、三张等）只能被炸弹或同花顺压制
     * - 五张牌型必须同类型且更大才能压制
     */
    private boolean canBeatBigPattern(List<Card> myCards, List<Card> lastCards) {
        if (myCards == null || myCards.isEmpty()) return false;
        if (lastCards == null || lastCards.isEmpty()) return true;

        int mySize = myCards.size();
        int lastSize = lastCards.size();

        // 炸弹（四张相同）或同花顺可以压任何牌型
        if (isFourOfAKindSimple(myCards) || isStraightFlush(myCards)) {
            return true;
        }

        // 如果上家出的是非五张牌型（单张、对子、三张等），
        // 自己必须出炸弹或同花顺才能压，否则不能压
        if (lastSize != 5) {
            return false;
        }

        // 双方都是五张牌型，必须类型相同且更大
        if (mySize != 5) return false;
        int myType = getPatternType(myCards);
        int lastType = getPatternType(lastCards);
        if (myType != lastType) return false;

        // 同类型比较关键值
        int myKey = getPatternKeyValue(myCards);
        int lastKey = getPatternKeyValue(lastCards);
        return myKey > lastKey;
    }

    /**
     * 记录出牌失败（由外部调用）
     */
    public void recordPlayFailure() {
        consecutiveFailCount++;
        System.out.println("[MonteCarloAI] 出牌失败，连续失败次数: " + consecutiveFailCount);
    }

    /**
     * 重置失败计数（成功出牌后调用）
     */
    public void resetFailCount() {
        consecutiveFailCount = 0;
    }

    /**
     * 获取牌型类型（锄大地规则）
     * 1-顺子 2-同花 3-葫芦 4-铁支 5-同花顺
     */
    private int getPatternType(List<Card> cards) {
        if (isStraightFlush(cards)) return 5;
        if (isFourOfAKindSimple(cards)) return 4;
        if (isFullHouseSimple(cards)) return 3;
        if (isFlushSimple(cards)) return 2;
        if (isStraightSimple(cards)) return 1;
        return 0;
    }

    /**
     * 获取牌型关键比较值
     */
    private int getPatternKeyValue(List<Card> cards) {
        int type = getPatternType(cards);
        switch (type) {
            case 5: // 同花顺
            case 1: // 顺子
                return getStraightMaxKey(cards);
            case 4: // 铁支
                return getFourRankKey(cards);
            case 3: // 葫芦
                return getThreeRankKey(cards);
            case 2: // 同花
                return getFlushMaxKey(cards);
            default:
                return 0;
        }
    }

    // 简化的牌型判断方法
    private boolean isStraightSimple(List<Card> cards) {
        if (cards.size() != 5) return false;
        List<Integer> weights = cards.stream()
                .map(c -> c.getRank().getWeight())
                .sorted()
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        if (weights.size() != 5) return false;
        for (int i = 1; i < weights.size(); i++) {
            if (weights.get(i) != weights.get(i-1) + 1) {
                if (!(weights.get(0) == 2 && weights.get(1) == 3 && 
                      weights.get(2) == 4 && weights.get(3) == 5 && weights.get(4) == 14)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isFlushSimple(List<Card> cards) {
        if (cards.size() != 5) return false;
        Suit suit = cards.get(0).getSuit();
        return cards.stream().allMatch(c -> c.getSuit() == suit);
    }

    private boolean isStraightFlush(List<Card> cards) {
        return isStraightSimple(cards) && isFlushSimple(cards);
    }

    private boolean isFourOfAKindSimple(List<Card> cards) {
        if (cards.size() != 5) return false;
        Map<Rank, Long> freq = cards.stream()
                .collect(java.util.stream.Collectors.groupingBy(Card::getRank, java.util.stream.Collectors.counting()));
        return freq.containsValue(4L);
    }

    private boolean isFullHouseSimple(List<Card> cards) {
        if (cards.size() != 5) return false;
        Map<Rank, Long> freq = cards.stream()
                .collect(java.util.stream.Collectors.groupingBy(Card::getRank, java.util.stream.Collectors.counting()));
        return freq.containsValue(3L) && freq.containsValue(2L);
    }

    private int getStraightMaxKey(List<Card> cards) {
        List<Integer> weights = cards.stream()
                .map(c -> c.getRank().getWeight())
                .sorted()
                .collect(java.util.stream.Collectors.toList());
        if (weights.get(0) == 2 && weights.get(1) == 3 && 
            weights.get(2) == 4 && weights.get(3) == 5 && weights.get(4) == 14) {
            return 5; // A-2-3-4-5 的最大是5
        }
        return weights.get(4);
    }

    private int getFourRankKey(List<Card> cards) {
        Map<Rank, Long> freq = cards.stream()
                .collect(java.util.stream.Collectors.groupingBy(Card::getRank, java.util.stream.Collectors.counting()));
        for (Map.Entry<Rank, Long> e : freq.entrySet()) {
            if (e.getValue() == 4) {
                return e.getKey().getWeight();
            }
        }
        return 0;
    }

    private int getThreeRankKey(List<Card> cards) {
        Map<Rank, Long> freq = cards.stream()
                .collect(java.util.stream.Collectors.groupingBy(Card::getRank, java.util.stream.Collectors.counting()));
        for (Map.Entry<Rank, Long> e : freq.entrySet()) {
            if (e.getValue() == 3) {
                return e.getKey().getWeight();
            }
        }
        return 0;
    }

    private int getFlushMaxKey(List<Card> cards) {
        return cards.stream()
                .mapToInt(c -> c.getRank().getWeight())
                .max().orElse(0);
    }

    /**
     * 获取手牌中最强的五张牌型
     */
    private List<Card> getBestBigPattern(List<Card> hand) {
        // 按优先级：同花顺 > 铁支 > 葫芦 > 同花 > 顺子
        List<Card> straightFlush = findStraightFlush(hand);
        if (straightFlush != null) return straightFlush;
        
        List<Card> fourOfAKind = findFourOfAKind(hand);
        if (fourOfAKind != null) return fourOfAKind;
        
        List<Card> fullHouse = findFullHouse(hand);
        if (fullHouse != null) return fullHouse;
        
        List<Card> flush = findFlush(hand);
        if (flush != null) return flush;
        
        List<Card> straight = findStraight(hand);
        if (straight != null) return straight;
        
        return null;
    }

    // 牌型检测辅助方法（从PhaseManager复制，因为是private）
    private List<Card> findStraightFlush(List<Card> hand) {
        Map<Suit, List<Card>> bySuit = hand.stream()
                .collect(java.util.stream.Collectors.groupingBy(Card::getSuit));
        for (List<Card> suitCards : bySuit.values()) {
            if (suitCards.size() >= 5) {
                List<Card> straight = findStraight(suitCards);
                if (straight != null && straight.size() == 5) {
                    return straight;
                }
            }
        }
        return null;
    }

    private List<Card> findFourOfAKind(List<Card> hand) {
        Map<Rank, Long> freq = hand.stream()
                .collect(java.util.stream.Collectors.groupingBy(Card::getRank, java.util.stream.Collectors.counting()));
        final Rank[] fourRankHolder = {null};
        for (Map.Entry<Rank, Long> e : freq.entrySet()) {
            if (e.getValue() >= 4) {
                fourRankHolder[0] = e.getKey();
                break;
            }
        }
        if (fourRankHolder[0] == null) return null;
        final Rank fourRank = fourRankHolder[0];
        List<Card> result = hand.stream()
                .filter(c -> c.getRank() == fourRank)
                .limit(4)
                .collect(java.util.stream.Collectors.toList());
        hand.stream()
                .filter(c -> c.getRank() != fourRank)
                .findFirst()
                .ifPresent(result::add);
        return result.size() == 5 ? result : null;
    }

    private List<Card> findFullHouse(List<Card> hand) {
        Map<Rank, Long> freq = hand.stream()
                .collect(java.util.stream.Collectors.groupingBy(Card::getRank, java.util.stream.Collectors.counting()));
        final Rank[] threeRankHolder = {null};
        final Rank[] pairRankHolder = {null};
        for (Map.Entry<Rank, Long> e : freq.entrySet()) {
            if (e.getValue() >= 3 && threeRankHolder[0] == null) {
                threeRankHolder[0] = e.getKey();
            } else if (e.getValue() >= 2) {
                pairRankHolder[0] = e.getKey();
            }
        }
        final Rank threeRank = threeRankHolder[0];
        final Rank pairRank = pairRankHolder[0];
        if (threeRank != null && pairRank != null) {
            List<Card> result = hand.stream()
                    .filter(c -> c.getRank() == threeRank)
                    .limit(3)
                    .collect(java.util.stream.Collectors.toList());
            result.addAll(hand.stream()
                    .filter(c -> c.getRank() == pairRank)
                    .limit(2)
                    .collect(java.util.stream.Collectors.toList()));
            return result;
        }
        return null;
    }

    private List<Card> findFlush(List<Card> hand) {
        Map<Suit, List<Card>> bySuit = hand.stream()
                .collect(java.util.stream.Collectors.groupingBy(Card::getSuit));
        for (List<Card> cards : bySuit.values()) {
            if (cards.size() >= 5) {
                return cards.stream()
                        .sorted((a, b) -> b.getRank().getWeight() - a.getRank().getWeight())
                        .limit(5)
                        .collect(java.util.stream.Collectors.toList());
            }
        }
        return null;
    }

    private List<Card> findStraight(List<Card> hand) {
        List<Card> sorted = hand.stream()
                .filter(c -> c.getRank() != Rank.TWO)
                .sorted((a, b) -> a.getRank().getWeight() - b.getRank().getWeight())
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        List<List<Card>> straights = new ArrayList<>();
        List<Card> current = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            if (current.isEmpty()) {
                current.add(sorted.get(i));
            } else {
                Card prev = current.get(current.size() - 1);
                Card curr = sorted.get(i);
                int prevWeight = prev.getRank().getWeight();
                int currWeight = curr.getRank().getWeight();
                boolean isConsecutive = (currWeight == prevWeight + 1) ||
                        (prev.getRank() == Rank.FIVE && curr.getRank() == Rank.ACE);
                if (isConsecutive) {
                    current.add(curr);
                } else {
                    if (current.size() >= 5) {
                        straights.add(new ArrayList<>(current));
                    }
                    current.clear();
                    current.add(curr);
                }
            }
        }
        if (current.size() >= 5) {
            straights.add(current);
        }
        if (!straights.isEmpty()) {
            straights.sort((a, b) -> {
                int aMax = a.get(a.size() - 1).getRank().getWeight();
                int bMax = b.get(b.size() - 1).getRank().getWeight();
                return Integer.compare(bMax, aMax);
            });
            return straights.get(0).subList(0, 5);
        }
        return null;
    }
}
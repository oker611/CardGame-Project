package com.example.cardgame.ai;

import com.example.cardgame.model.*;
import com.example.cardgame.rule.ConfigurableRuleEngine;
import com.example.cardgame.rule.PlayValidator;
import com.example.cardgame.rule.RuleConfig;
import com.example.cardgame.rule.RuleEngine;
import com.example.cardgame.util.CardTracker;
import java.util.*;
import java.util.concurrent.*;
import com.example.cardgame.rule.ConfigurableRuleEngine;
import com.example.cardgame.rule.RuleConfig;

public class MonteCarloAIDecisionStrategy implements AIDecisionStrategy {

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

    public MonteCarloAIDecisionStrategy() {
        this.ruleEngine = new ConfigurableRuleEngine(RuleConfig.SOUTHERN);
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

        // 1. 强制压牌检测（残局压制）- 仅当上一轮有出牌且为单牌时
        if (lastPlay != null && !lastPlay.isEmpty() && phaseManager.shouldForceBeat(aiPlayer, gameState, lastPlay)) {
            List<Card> lastPlayed = lastPlay.getCards();
            Card bestBeat = findBestBeatCard(hand, lastPlayed);
            if (bestBeat != null) {
                List<Card> toPlay = Collections.singletonList(bestBeat);
                // 强制压牌场景已确保合法性（单牌且牌值足够大或为2），直接返回
                System.out.println("[MonteCarloAI] 强制压牌: " + bestBeat);
                return toPlay;
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
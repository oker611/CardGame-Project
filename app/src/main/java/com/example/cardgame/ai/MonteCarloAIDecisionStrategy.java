package com.example.cardgame.ai;

import com.example.cardgame.model.*;
import com.example.cardgame.rule.PatternRecognizer;
import com.example.cardgame.rule.ConfigurableRuleEngine;
import com.example.cardgame.rule.PlayValidator;
import com.example.cardgame.rule.RuleConfig;
import com.example.cardgame.rule.RuleEngine;
import com.example.cardgame.util.CardTracker;
import java.util.*;
import java.util.concurrent.*;

public class MonteCarloAIDecisionStrategy implements AIDecisionStrategy {

    // 调试开关（正式版改为 false）
    private static final boolean DEBUG_AI = true;

    // 可调参数（遗传算法优化结果 + 手动调优）
    private static final int NUM_SAMPLES = 10;           // 蒙特卡洛模拟世界数量（降低到10避免ANR）
    private static final int TOP_K_CANDIDATES = 4;      // 候选动作截断数
    private static final long DECISION_TIMEOUT_MS = 1500;  // 决策超时（毫秒，1500ms足够完成精细模拟）

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
    
    // 连续过牌轮数（用于消极游戏检测）
    private int consecutivePassRounds = 0;

    // ========== 残局多轮试探追踪 ==========
    // 记录上次试探的牌型，用于多轮类型切换
    private String lastProbingType = null;
    private int probingRoundCount = 0;

    // 自适应因子（由 AdaptiveAIDecisionStrategy 设置）
    private double aggressivenessFactor = 1.0;
    private double defenseFactor = 1.0;

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
        
        // ========== 消极游戏检测：连续两轮清空桌面，强制出牌 ==========
        if (isFirstTurn) {
            consecutivePassRounds++;
        } else {
            consecutivePassRounds = 0;
        }
        
        if (consecutivePassRounds >= 2 && isFirstTurn && !hand.isEmpty()) {
            // 检查是否有 AI 手牌 > 5
            boolean hasAIMoreThan5 = false;
            for (Player p : gameState.getPlayers()) {
                if (p.getType() == PlayerType.AI && p.getHandCards().size() > 5) {
                    hasAIMoreThan5 = true;
                    break;
                }
            }
            
            if (hasAIMoreThan5) {
                List<Card> sortedHand = new ArrayList<>(hand);
                sortedHand.sort((a, b) -> b.getRank().getWeight() - a.getRank().getWeight());
                System.out.println("[MonteCarloAI] 消极游戏检测：连续清空桌面，强制出最大单张 " + sortedHand.get(0));
                consecutivePassRounds = 0;
                return Collections.singletonList(sortedHand.get(0));
            }
        }

        // ========== 残局强制压牌：对手只剩1-2张牌且AI有能压的牌，直接出 ==========
        if (!isFirstTurn && lastPlay != null && !lastPlay.isEmpty()) {
            int minOpponentHandSize = getMinOpponentHandSize(gameState, aiPlayer);
            if (minOpponentHandSize <= 2) {
                Play forcedPlay = findAnyValidBeatPlay(hand, lastPlay, isFirstRound, isFirstTurn);
                if (forcedPlay != null) {
                    System.out.println("[MonteCarloAI] 残局强制压牌，对手剩 " + minOpponentHandSize + " 张，出 " + forcedPlay.getCards());
                    return forcedPlay.getCards();
                }
            }
        }
        
        // ========== 残局主动改变牌型试探（多轮类型切换）==========
        // 当对手手牌≤3时，按顺序尝试不同牌型，迫使对手拆牌
        if (isFirstTurn) {
            int minOpponentHandSize = getMinOpponentHandSize(gameState, aiPlayer);
            if (minOpponentHandSize <= 3 && hand.size() >= 2) {
                List<Card> probingPlay = findMultiRoundProbingPlay(hand, lastPlay, isFirstRound, isFirstTurn);
                if (probingPlay != null) {
                    System.out.println("[MonteCarloAI] 残局多轮试探：" + lastProbingType + " 尝试，对手剩" + minOpponentHandSize + "张，出 " + probingPlay);
                    return probingPlay;
                }

                // ========== 试探失败时的兜底：对手1-2张时出最大牌 ==========
                // 试探失败后，如果对手只剩1-2张，必须出最大牌压制，不能出小牌
                if (minOpponentHandSize <= 2 && hand.size() >= 2) {
                    List<Card> maxPlay = findMaxCombinationPlay(hand, lastPlay, isFirstRound, isFirstTurn);
                    if (maxPlay != null) {
                        System.out.println("[MonteCarloAI] 残局压制：试探失败，对手剩" + minOpponentHandSize + "张，强制出最大组合 " + maxPlay);
                        return maxPlay;
                    } else {
                        // 没有组合牌，出最大单张
                        List<Card> sortedHand = new ArrayList<>(hand);
                        sortedHand.sort((a, b) -> b.getRank().getWeight() - a.getRank().getWeight());
                        System.out.println("[MonteCarloAI] 残局压制：试探失败，对手剩" + minOpponentHandSize + "张，强制出最大单张 " + sortedHand.get(0));
                        return Collections.singletonList(sortedHand.get(0));
                    }
                }
            }
        } else {
            // 非主动出牌时（非FirstTurn），重置试探状态
            if (!isFirstTurn) {
                lastProbingType = null;
                probingRoundCount = 0;
            }
        }
        
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
        // 但如果是主动出牌且对手手牌≤4，则跳过此策略
        if (hand.size() > 11 && !isFirstTurn) {
            boolean isUrgent = isFirstTurn && getMinOpponentHandSize(gameState, aiPlayer) <= 4;
            if (!isUrgent) {
                List<Card> smallPlay = getSmallestValidPlay(hand, lastPlay);
                if (smallPlay != null) {
                    System.out.println("[MonteCarloAI] 开局保守出小牌: " + smallPlay);
                    return smallPlay;
                }
            }
        }

        // 2.5 中盘谨慎策略：手牌6-11张时，优先出小牌
        // 但如果是主动出牌且对手手牌≤4，则跳过此策略
        if (hand.size() >= 6 && hand.size() <= 11 && !isFirstTurn) {
            boolean isUrgent = isFirstTurn && getMinOpponentHandSize(gameState, aiPlayer) <= 4;
            if (!isUrgent) {
                List<Card> smallPlay = getSmallestValidPlay(hand, lastPlay);
                if (smallPlay != null) {
                    System.out.println("[MonteCarloAI] 中盘谨慎出小牌: " + smallPlay);
                    return smallPlay;
                }
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

        // ========== 动态蒙特卡洛模拟次数 ==========
        // 手牌多时快速决策，手牌少时精细决策
        int dynamicSamples = calculateDynamicSamples(hand.size());
        if (DEBUG_AI) {
            System.out.println("[MonteCarloAI] 动态模拟次数: " + dynamicSamples + " (手牌:" + hand.size() + "张)");
        }

        PhaseManager.GamePhase phase = phaseManager.getCurrentPhase(aiPlayer, gameState);
        List<OpponentHandSampler.World> worlds = opponentHandSampler.sampleWorlds(aiPlayer, gameState, cardTracker, dynamicSamples);
        monteCarloSimulator.setOpponentProfiles(opponentProfiles);

        // 异步评估候选动作，避免阻塞主线程（但策略接口是同步的，我们内部使用超时）
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Map<Play, Double>> future = executor.submit(() -> {
            Map<Play, Double> scores = new HashMap<>();
            
            // 计算紧急模式
            boolean isInitiative = (lastPlay == null || lastPlay.isEmpty());
            int minOpponentHand = getMinOpponentHandSize(gameState, aiPlayer);
            boolean isUrgent = minOpponentHand <= 4;
            
            for (Play candidate : candidates) {
                double rawScore = monteCarloSimulator.evaluate(candidate, aiPlayer, gameState, worlds);
                // 计算出牌后的手牌
                List<Card> handAfterPlay = new ArrayList<>(hand);
                handAfterPlay.removeAll(candidate.getCards());
                double adjusted = phaseManager.adjustScore(rawScore, candidate, phase, aiPlayer, gameState, handAfterPlay);
                
                // ========== 直接添加评分逻辑 ==========
                int cardCount = candidate.getCards().size();
                CardPattern pattern = candidate.getPattern();
                
                // 1. 张数奖励：每多一张 +50
                if (cardCount > 1) {
                    double countBonus = (cardCount - 1) * 50;
                    adjusted += countBonus;
                    System.out.println("[MonteCarloAI] 张数奖励: " + cardCount + "张 +" + countBonus);
                }
                
                // 2. 组合牌额外奖励
                if (pattern != null && pattern != CardPattern.SINGLE) {
                    adjusted += 20;
                    System.out.println("[MonteCarloAI] 组合牌奖励 +20");
                }
                
                // 3. 压牌奖励
                if (!isInitiative && lastPlay != null && !lastPlay.isEmpty()) {
                    int lastCardCount = lastPlay.getCards().size();
                    
                    // 3.1 对子压对子
                    if (lastCardCount == cardCount && lastCardCount == 2) {
                        CardPattern lastPattern = lastPlay.getPattern();
                        if (lastPattern == CardPattern.PAIR && pattern == CardPattern.PAIR) {
                            int currentPairValue = candidate.getCards().get(0).getRank().getWeight();
                            int lastPairValue = lastPlay.getCards().get(0).getRank().getWeight();
                            if (currentPairValue > lastPairValue) {
                                double bonus = 80;
                                if (currentPairValue >= 14) {
                                    bonus = 30;
                                }
                                adjusted += bonus;
                                System.out.println("[MonteCarloAI] 对子压对子奖励 +" + bonus);
                            }
                        }
                    }
                    
                    // 3.2 用组合牌压单张（锄大地规则：组合牌 > 单张）
                    if (lastCardCount == 1 && cardCount > 1) {
                        // 计算单张牌力
                        int lastSingleValue = lastPlay.getCards().get(0).getRank().getWeight();
                        int currentMinValue = candidate.getCards().stream()
                                .mapToInt(c -> c.getRank().getWeight())
                                .min().orElse(0);
                        if (currentMinValue > lastSingleValue) {
                            double bonus = 60;
                            // 如果是大组合牌（三张及以上），奖励更高
                            if (cardCount >= 3) {
                                bonus = 80;
                            }
                            adjusted += bonus;
                            System.out.println("[MonteCarloAI] 组合牌压单张奖励 +" + bonus);
                        }
                    }
                }
                
                // 4. 紧急模式：惩罚单张，奖励组合
                if (isUrgent && isInitiative) {
                    if (cardCount == 1) {
                        adjusted -= 40;
                        System.out.println("[MonteCarloAI] 紧急模式单张惩罚 -40");
                    } else {
                        adjusted += 30;
                        System.out.println("[MonteCarloAI] 紧急模式组合奖励 +30");
                    }
                }
                
                // 应用自适应因子
                adjusted = applyAdaptiveFactors(adjusted, candidate, lastPlay, gameState, aiPlayer);
                
                // 打印分数组成（调试用）
                System.out.println("[MonteCarloAI] 候选出牌: " + candidate.getCards() + 
                    " 牌型: " + pattern + " 最终分数: " + adjusted);
                
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
     * 重置连续过牌计数器（游戏开始时调用）
     */
    public void resetPassCounter() {
        consecutivePassRounds = 0;
        if (DEBUG_AI) {
            System.out.println("[MonteCarloAI] 重置连续过牌计数器");
        }
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
        System.out.println("[MonteCarloAI] applyAdaptiveFactors called - score=" + score + 
                ", aggressiveness=" + aggressivenessFactor + ", defense=" + defenseFactor);
        
        double adjustedScore = score;
        boolean isInitiativePlay = (lastPlay == null || lastPlay.isEmpty());
        
        if (candidate == null || candidate.isEmpty()) {
            return adjustedScore;
        }
        
        List<Card> cards = candidate.getCards();
        List<Card> hand = aiPlayer.getHandCards();
        int handSize = hand.size();
        
        // ========== 开局鼓励出组合牌 ==========
        // 在开局阶段（手牌数 > 8）且是主动出牌时，鼓励出组合牌
        if (isInitiativePlay && handSize > 8) {
            CardPattern pattern = candidate.getPattern();
            int cardCount = cards.size();
            
            // 如果是组合牌（对子、顺子、同花等），给予额外奖励
            if (pattern != null && pattern != CardPattern.SINGLE && cardCount >= 2) {
                double openingBonus = handSize * 2.0;  // 手牌越多，奖励越高
                adjustedScore += openingBonus;
                if (DEBUG_AI) {
                    System.out.println("[MonteCarloAI] 开局组合牌奖励 +" + openingBonus + " (手牌:" + handSize + "张, 牌型:" + pattern + ")");
                }
            }
        }
        
        // ========== 防守模式：保留大牌奖励 ==========
        // 防守因子越高，对大牌（2、A）的保留意愿越强
        if (defenseFactor > 1.0) {
            int bigCardsInPlay = 0;
            for (Card card : cards) {
                if (card.getRank() == Rank.TWO || card.getRank() == Rank.ACE) {
                    bigCardsInPlay++;
                }
            }
            // 防守模式下，出大牌会受到惩罚（鼓励保留）
            if (bigCardsInPlay > 0) {
                double penalty = bigCardsInPlay * 15.0 * (defenseFactor - 1.0);
                adjustedScore -= penalty;
                if (DEBUG_AI) {
                    System.out.println("[MonteCarloAI] 防守模式大牌惩罚：出" + bigCardsInPlay + "张大牌 -" + penalty);
                }
            }
            // 防守模式下，出小牌会获得奖励
            boolean allSmallCards = true;
            for (Card card : cards) {
                if (card.getRank().getWeight() >= 10) { // 10及以上视为大牌
                    allSmallCards = false;
                    break;
                }
            }
            if (allSmallCards) {
                double bonus = 10.0 * (defenseFactor - 1.0) * cards.size();
                adjustedScore += bonus;
                if (DEBUG_AI) {
                    System.out.println("[MonteCarloAI] 防守模式小牌奖励：+" + bonus);
                }
            }
        }
        
        // ========== 进攻模式：出掉最小牌奖励 ==========
        // 进攻因子越高，越倾向于出掉手中最小的牌
        if (aggressivenessFactor > 1.0) {
            // 计算手牌中最小的牌值
            int minHandRank = hand.stream()
                    .mapToInt(c -> c.getRank().getWeight())
                    .min().orElse(Integer.MAX_VALUE);
            
            // 计算出牌中的最小牌值
            int minPlayRank = cards.stream()
                    .mapToInt(c -> c.getRank().getWeight())
                    .min().orElse(Integer.MAX_VALUE);
            
            // 如果出的是手牌中最小的牌，给予额外奖励
            if (minPlayRank == minHandRank) {
                double bonus = 15.0 * (aggressivenessFactor - 1.0) * cards.size();
                adjustedScore += bonus;
                if (DEBUG_AI) {
                    System.out.println("[MonteCarloAI] 进攻模式出最小牌奖励：+" + bonus);
                }
            }
            
            // 进攻模式下，组合牌获得额外奖励
            if (cards.size() > 1) {
                double comboBonus = 8.0 * (aggressivenessFactor - 1.0) * cards.size();
                adjustedScore += comboBonus;
                if (DEBUG_AI) {
                    System.out.println("[MonteCarloAI] 进攻模式组合牌奖励：+" + comboBonus);
                }
            }
        }
        
        // ========== 防守模式：优先过牌 ==========
        // 防守因子低时（<1.0），倾向于过牌
        if (defenseFactor < 1.0 && !isInitiativePlay) {
            // 如果不是主动出牌，过牌的吸引力增加
            adjustedScore *= defenseFactor;
            if (DEBUG_AI) {
                System.out.println("[MonteCarloAI] 防守模式压牌惩罚：分数 * " + defenseFactor);
            }
        }
        
        // ========== 进攻模式：主动压牌奖励 ==========
        // 进攻因子越高，越倾向于压牌
        if (aggressivenessFactor > 1.0 && !isInitiativePlay && lastPlay != null && !lastPlay.isEmpty()) {
            // 计算压牌强度（当前牌值与上家牌值的差值）
            int myValue = cards.stream().mapToInt(c -> c.getRank().getWeight()).sum();
            int lastValue = lastPlay.getCards().stream().mapToInt(c -> c.getRank().getWeight()).sum();
            double beatStrength = (double)(myValue - lastValue) / lastValue;
            
            double bonus = 10.0 * (aggressivenessFactor - 1.0) * beatStrength;
            adjustedScore += bonus;
            if (DEBUG_AI) {
                System.out.println("[MonteCarloAI] 进攻模式压牌奖励：+" + bonus + " (强度=" + beatStrength + ")");
            }
        }
        
        // ========== 原有因子调整 ==========
        boolean isAggressivePlay = isAggressivePlay(candidate, lastPlay);
        boolean isDefensivePlay = isDefensivePlay(candidate, lastPlay);

        if (isAggressivePlay) {
            adjustedScore *= aggressivenessFactor;
        }
        if (isDefensivePlay) {
            adjustedScore *= defenseFactor;
        }

        // ========== 紧急模式处理 ==========
        if (gameState != null) {
            int minOpponentHandSize = getMinOpponentHandSize(gameState, aiPlayer);
            
            // 紧急模式：对手手牌 ≤ 4 时，主动出牌禁止单张，鼓励组合牌
            if (isInitiativePlay && minOpponentHandSize <= 4) {
                int totalCards = cards.size();
                if (totalCards == 1) {
                    adjustedScore -= 12.0 * aggressivenessFactor;
                    if (DEBUG_AI) {
                        System.out.println("[MonteCarloAI] 紧急模式：单张 -" + (12.0 * aggressivenessFactor));
                    }
                } else {
                    double comboBonus = 15.0 * totalCards * aggressivenessFactor;
                    adjustedScore += comboBonus;
                    if (DEBUG_AI) {
                        System.out.println("[MonteCarloAI] 紧急模式：组合牌 +" + comboBonus);
                    }
                }
            }
            
            // 压牌紧急奖励（对手手牌 ≤ 3 时，压牌获得高分）
            if (!isInitiativePlay && minOpponentHandSize <= 3) {
                double urgencyBonus = 15.0 * aggressivenessFactor;
                if (cards.size() > 1) {
                    urgencyBonus += 20.0 * aggressivenessFactor;
                }
                int myHandSize = hand.size();
                int handAfterPlay = myHandSize - cards.size();
                if (handAfterPlay <= 1) {
                    urgencyBonus += 30.0;
                    System.out.println("[MonteCarloAI] 压牌后即将获胜！额外+30分");
                }
                adjustedScore += urgencyBonus;
                System.out.println("[MonteCarloAI] 紧急压牌奖励 +" + urgencyBonus);
            }
            
            // ========== 残局强制压牌逻辑 ==========
            // 当对手手牌 ≤ 2 时，如果 AI 有能压的牌，强制压牌（给予极高加成）
            if (!isInitiativePlay && lastPlay != null && !lastPlay.isEmpty() && minOpponentHandSize <= 2) {
                // 计算基础压牌加成（大幅提高）
                double endgameBonus = 100.0 * (3 - minOpponentHandSize); // 剩2张+100，剩1张+200
                adjustedScore += endgameBonus;
                
                // 如果出的是大牌（如2、A），额外加成
                boolean hasBigCard = cards.stream()
                        .anyMatch(c -> c.getRank() == Rank.TWO || c.getRank() == Rank.ACE);
                if (hasBigCard) {
                    endgameBonus += 50.0;
                    adjustedScore += 50.0;
                }
                
                // 如果压牌后AI手牌更少，额外加成（鼓励终结）
                int myHandSize = hand.size();
                int handAfterPlay = myHandSize - cards.size();
                if (handAfterPlay <= myHandSize / 2) {
                    endgameBonus += 30.0;
                    adjustedScore += 30.0;
                }
                
                System.out.println("[MonteCarloAI] 残局强制压牌奖励 +" + endgameBonus + " (对手剩" + minOpponentHandSize + "张)");
            }
        }

        // ========== 小牌跟牌奖励 ==========
        // 玩家出小牌时（如3、4、5），如果AI手牌中有稍大的牌，应尽量跟牌
        if (!isInitiativePlay && lastPlay != null && !lastPlay.isEmpty()) {
            int lastMaxRank = lastPlay.getCards().stream()
                    .mapToInt(c -> c.getRank().getWeight())
                    .max().orElse(0);
            
            // 上家出的是小牌（≤6）
            if (lastMaxRank > 0 && lastMaxRank <= 6) {
                int myMinRank = cards.stream()
                        .mapToInt(c -> c.getRank().getWeight())
                        .min().orElse(Integer.MAX_VALUE);
                
                // 如果AI出的牌略大于上家（差值≤5），给予奖励
                if (myMinRank > lastMaxRank && myMinRank - lastMaxRank <= 5) {
                    double smallCardBonus = 15.0 + (6 - lastMaxRank) * 3; // 越小的牌奖励越高
                    adjustedScore += smallCardBonus;
                    System.out.println("[MonteCarloAI] 小牌跟牌奖励 +" + smallCardBonus + " (上家出" + lastMaxRank + ", AI出" + myMinRank + ")");
                }
            }
        }

        // ========== 组合牌主动奖励（不依赖紧急模式）==========
        if (isInitiativePlay && cards.size() >= 2) {
            double comboBonus = 10.0 * cards.size();
            CardPattern pattern = candidate.getPattern();
            
            // 对不同牌型给予不同加成
            if (pattern == CardPattern.STRAIGHT_FLUSH || pattern == CardPattern.QUADRUPLE) {
                comboBonus *= 2;      // 同花顺、炸弹双倍奖励
            } else if (pattern == CardPattern.FULL_HOUSE || pattern == CardPattern.IRON_BRANCH) {
                comboBonus *= 1.5;    // 葫芦、铁支1.5倍奖励
            } else if (pattern == CardPattern.FLUSH || pattern == CardPattern.STRAIGHT) {
                comboBonus *= 1.3;    // 同花、顺子1.3倍奖励
            }
            
            adjustedScore += comboBonus;
            if (DEBUG_AI) {
                System.out.println("[MonteCarloAI] 组合牌主动奖励 +" + comboBonus + " (牌型: " + pattern + ")");
            }
        }
        
        // ========== 张数奖励 ==========
        if (isInitiativePlay) {
            int cardCount = cards.size();
            if (cardCount > 1) {
                double countBonus = (cardCount - 1) * 10;
                adjustedScore += countBonus;
                if (DEBUG_AI) {
                    System.out.println("[MonteCarloAI] 张数奖励：" + cardCount + "张 +" + countBonus);
                }
            }
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
     * ========== 残局多轮试探方法 ==========
     * 按顺序尝试不同牌型：单张对子 -> 三张 -> 顺子
     * 迫使对手拆牌，从而创造压牌机会
     */
    private List<Card> findMultiRoundProbingPlay(List<Card> hand, Play lastPlay,
                                                   boolean isFirstRound, boolean isFirstTurn) {
        Map<Rank, List<Card>> rankMap = hand.stream()
                .collect(java.util.stream.Collectors.groupingBy(Card::getRank));

        // 试探顺序：单张对子 -> 三张(如果上次是对子) -> 顺子(如果上次是三张)
        List<String> probingSequence = determineProbingSequence();

        for (String type : probingSequence) {
            List<Card> play = tryProbingType(type, rankMap, hand, lastPlay, isFirstRound, isFirstTurn);
            if (play != null) {
                lastProbingType = type;
                probingRoundCount++;
                return play;
            }
        }

        return null; // 所有试探都失败
    }

    /**
     * 确定试探顺序
     * 根据上次的试探类型，决定下次应该尝试什么
     */
    private List<String> determineProbingSequence() {
        List<String> sequence = new ArrayList<>();

        if (lastProbingType == null) {
            // 第一次试探：尝试对子
            sequence.add("PAIR");
        } else if (lastProbingType.equals("PAIR")) {
            // 上次对子被跟上了，尝试三张
            sequence.add("TRIPLE");
            sequence.add("PAIR"); // 三张没有就回退到对子
        } else if (lastProbingType.equals("TRIPLE")) {
            // 上次三张被跟上了，尝试顺子
            sequence.add("STRAIGHT");
            sequence.add("TRIPLE"); // 顺子没有就回退到三张
        } else if (lastProbingType.equals("STRAIGHT")) {
            // 上次顺子被跟上了，尝试更大的顺子或对子
            sequence.add("PAIR");
            sequence.add("STRAIGHT");
        } else {
            // 默认从对子开始
            sequence.add("PAIR");
        }

        return sequence;
    }

    /**
     * 尝试特定类型的试探
     */
    private List<Card> tryProbingType(String type, Map<Rank, List<Card>> rankMap,
                                       List<Card> hand, Play lastPlay,
                                       boolean isFirstRound, boolean isFirstTurn) {
        switch (type) {
            case "PAIR":
                return tryFindPair(rankMap, lastPlay, isFirstRound, isFirstTurn);
            case "TRIPLE":
                return tryFindTriple(rankMap, lastPlay, isFirstRound, isFirstTurn);
            case "STRAIGHT":
                return tryFindStraight(hand, lastPlay, isFirstRound, isFirstTurn);
            default:
                return null;
        }
    }

    private List<Card> tryFindPair(Map<Rank, List<Card>> rankMap, Play lastPlay,
                                    boolean isFirstRound, boolean isFirstTurn) {
        for (List<Card> sameRank : rankMap.values()) {
            if (sameRank.size() >= 2) {
                List<Card> pair = new ArrayList<>(sameRank.subList(0, 2));
                PlayValidator.ValidationResult result = ruleEngine.validatePlay(
                        pair, lastPlay != null ? lastPlay.getCards() : null,
                        isFirstRound, isFirstTurn);
                if (result.valid) {
                    return pair;
                }
            }
        }
        return null;
    }

    private List<Card> tryFindTriple(Map<Rank, List<Card>> rankMap, Play lastPlay,
                                       boolean isFirstRound, boolean isFirstTurn) {
        for (List<Card> sameRank : rankMap.values()) {
            if (sameRank.size() >= 3) {
                List<Card> triple = new ArrayList<>(sameRank.subList(0, 3));
                PlayValidator.ValidationResult result = ruleEngine.validatePlay(
                        triple, lastPlay != null ? lastPlay.getCards() : null,
                        isFirstRound, isFirstTurn);
                if (result.valid) {
                    return triple;
                }
            }
        }
        return null;
    }

    private List<Card> tryFindStraight(List<Card> hand, Play lastPlay,
                                         boolean isFirstRound, boolean isFirstTurn) {
        if (hand.size() < 5) return null;

        // 简单的顺子检测：从手牌中找出5张连续点数的牌
        List<Card> sortedHand = new ArrayList<>(hand);
        sortedHand.sort((a, b) -> a.getRank().getWeight() - b.getRank().getWeight());

        // 尝试找到5张连续的牌
        for (int i = 0; i <= sortedHand.size() - 5; i++) {
            List<Card> potentialStraight = new ArrayList<>();
            int expectedWeight = sortedHand.get(i).getRank().getWeight();

            // 检查i开始的5张牌是否是连续的
            boolean isConsecutive = true;
            for (int j = 0; j < 5; j++) {
                Card card = sortedHand.get(i + j);
                if (card.getRank().getWeight() == expectedWeight + j) {
                    potentialStraight.add(card);
                } else {
                    isConsecutive = false;
                    break;
                }
            }

            if (isConsecutive && potentialStraight.size() == 5) {
                PlayValidator.ValidationResult result = ruleEngine.validatePlay(
                        potentialStraight, lastPlay != null ? lastPlay.getCards() : null,
                        isFirstRound, isFirstTurn);
                if (result.valid) {
                    return potentialStraight;
                }
            }
        }
        return null;
    }

    /**
     * ========== 查找最大组合牌（用于残局压制）==========
     * 找到手中最大的合法组合牌（对子>三张>顺子>同花等）
     */
    private List<Card> findMaxCombinationPlay(List<Card> hand, Play lastPlay,
                                               boolean isFirstRound, boolean isFirstTurn) {
        Map<Rank, List<Card>> rankMap = hand.stream()
                .collect(java.util.stream.Collectors.groupingBy(Card::getRank));

        List<Card> bestPlay = null;
        int bestScore = 0;

        // 检查四张炸弹（最高优先级）
        for (List<Card> sameRank : rankMap.values()) {
            if (sameRank.size() >= 4) {
                List<Card> bomb = new ArrayList<>(sameRank.subList(0, 4));
                PlayValidator.ValidationResult result = ruleEngine.validatePlay(
                        bomb, lastPlay != null ? lastPlay.getCards() : null,
                        isFirstRound, isFirstTurn);
                if (result.valid) {
                    int score = 4 * 100; // 炸弹权重很高
                    if (score > bestScore) {
                        bestScore = score;
                        bestPlay = bomb;
                    }
                }
            }
        }

        // 检查五张牌型（顺子、同花等）
        if (hand.size() >= 5) {
            List<List<Card>> combinations = new ArrayList<>();
            generateFiveCardCombinations(hand, 0, new ArrayList<>(), combinations);
            for (List<Card> fiveCards : combinations) {
                PlayValidator.ValidationResult result = ruleEngine.validatePlay(
                        fiveCards, lastPlay != null ? lastPlay.getCards() : null,
                        isFirstRound, isFirstTurn);
                if (result.valid) {
                    PatternRecognizer.PatternInfo info = ruleEngine.recognizePattern(fiveCards);
                    int patternScore = getPatternScoreFromType(info.getType());
                    int cardScore = fiveCards.stream().mapToInt(c -> c.getRank().getWeight()).sum();
                    int totalScore = patternScore * 100 + cardScore;
                    if (totalScore > bestScore) {
                        bestScore = totalScore;
                        bestPlay = fiveCards;
                    }
                }
            }
        }

        // 检查三张
        for (List<Card> sameRank : rankMap.values()) {
            if (sameRank.size() >= 3) {
                List<Card> triple = new ArrayList<>(sameRank.subList(0, 3));
                PlayValidator.ValidationResult result = ruleEngine.validatePlay(
                        triple, lastPlay != null ? lastPlay.getCards() : null,
                        isFirstRound, isFirstTurn);
                if (result.valid) {
                    int score = 3 * 100 + triple.stream().mapToInt(c -> c.getRank().getWeight()).sum();
                    if (score > bestScore) {
                        bestScore = score;
                        bestPlay = triple;
                    }
                }
            }
        }

        // 检查对子
        for (List<Card> sameRank : rankMap.values()) {
            if (sameRank.size() >= 2) {
                List<Card> pair = new ArrayList<>(sameRank.subList(0, 2));
                PlayValidator.ValidationResult result = ruleEngine.validatePlay(
                        pair, lastPlay != null ? lastPlay.getCards() : null,
                        isFirstRound, isFirstTurn);
                if (result.valid) {
                    int score = 2 * 100 + pair.stream().mapToInt(c -> c.getRank().getWeight()).sum();
                    if (score > bestScore) {
                        bestScore = score;
                        bestPlay = pair;
                    }
                }
            }
        }

        return bestPlay;
    }

    /**
     * 获取牌型的基础分数（用于排序）- 基于CardPattern
     */
    private int getPatternScore(CardPattern pattern) {
        if (pattern == null) return 0;
        switch (pattern) {
            case STRAIGHT_FLUSH: return 9;
            case IRON_BRANCH: return 8;
            case FULL_HOUSE: return 7;
            case FLUSH: return 6;
            case STRAIGHT: return 5;
            case QUADRUPLE: return 4;
            case TRIPLE: return 3;
            case PAIR: return 2;
            case SINGLE: return 1;
            default: return 0;
        }
    }

    /**
     * 获取牌型的基础分数（用于排序）- 基于PatternType
     */
    private int getPatternScoreFromType(PatternRecognizer.PatternType patternType) {
        if (patternType == null) return 0;
        switch (patternType) {
            case STRAIGHT_FLUSH: return 9;
            case IRON_BRANCH: return 8;
            case FULL_HOUSE: return 7;
            case FLUSH: return 6;
            case STRAIGHT: return 5;
            case QUADRUPLE: return 4;
            case TRIPLE: return 3;
            case PAIR: return 2;
            case SINGLE: return 1;
            default: return 0;
        }
    }

    /**
     * ========== 动态蒙特卡洛模拟次数 ==========
     * 根据AI手牌数动态调整模拟次数
     *
     * 规则：
     * - 手牌 > 8：张数多时快速决策（100次）
     * - 手牌 4~8：中盘平衡决策（200次）
     * - 手牌 ≤ 3：残局精细决策（400次）
     */
    private int calculateDynamicSamples(int handSize) {
        if (handSize > 8) {
            return 100;  // 开局快速决策
        } else if (handSize >= 4) {
            return 200;  // 中盘平衡
        } else {
            return 400;  // 残局精细
        }
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

    /**
     * 寻找任何能压住上家的合法出牌（残局强制压牌用）
     * @param hand AI手牌
     * @param lastPlay 上家出牌
     * @param isFirstRound 是否首轮
     * @param isFirstTurn 是否首出
     * @return 能压住上家的出牌，如果没有则返回null
     */
    private Play findAnyValidBeatPlay(List<Card> hand, Play lastPlay, boolean isFirstRound, boolean isFirstTurn) {
        if (lastPlay == null || lastPlay.isEmpty()) return null;
        
        List<Card> lastCards = lastPlay.getCards();
        
        // 首先尝试单张压牌
        for (Card card : hand) {
            List<Card> single = Collections.singletonList(card);
            PlayValidator.ValidationResult result = ruleEngine.validatePlay(
                    single, lastCards, isFirstRound, isFirstTurn);
            if (result.valid) {
                return new Play(null, single, CardPattern.SINGLE);
            }
        }
        
        // 尝试对子压牌
        Map<Rank, List<Card>> rankMap = hand.stream()
                .collect(java.util.stream.Collectors.groupingBy(Card::getRank));
        for (List<Card> sameRank : rankMap.values()) {
            if (sameRank.size() >= 2) {
                List<Card> pair = sameRank.subList(0, 2);
                PlayValidator.ValidationResult result = ruleEngine.validatePlay(
                        pair, lastCards, isFirstRound, isFirstTurn);
                if (result.valid) {
                    return new Play(null, pair, CardPattern.PAIR);
                }
            }
        }
        
        // 尝试三张压牌
        for (List<Card> sameRank : rankMap.values()) {
            if (sameRank.size() >= 3) {
                List<Card> triple = sameRank.subList(0, 3);
                PlayValidator.ValidationResult result = ruleEngine.validatePlay(
                        triple, lastCards, isFirstRound, isFirstTurn);
                if (result.valid) {
                    return new Play(null, triple, CardPattern.TRIPLE);
                }
            }
        }
        
        // 尝试炸弹（四张相同）
        for (List<Card> sameRank : rankMap.values()) {
            if (sameRank.size() >= 4) {
                List<Card> bomb = sameRank.subList(0, 4);
                PlayValidator.ValidationResult result = ruleEngine.validatePlay(
                        bomb, lastCards, isFirstRound, isFirstTurn);
                if (result.valid) {
                    return new Play(null, bomb, CardPattern.QUADRUPLE);
                }
            }
        }
        
        // 尝试五张牌型
        if (hand.size() >= 5) {
            List<List<Card>> combinations = new ArrayList<>();
            generateFiveCardCombinations(hand, 0, new ArrayList<>(), combinations);
            for (List<Card> fiveCards : combinations) {
                PlayValidator.ValidationResult result = ruleEngine.validatePlay(
                        fiveCards, lastCards, isFirstRound, isFirstTurn);
                if (result.valid) {
                    PatternRecognizer.PatternInfo info = ruleEngine.recognizePattern(fiveCards);
                    CardPattern pattern = mapPatternType(info.getType());
                    return new Play(null, fiveCards, pattern);
                }
            }
        }
        
        return null;
    }
    
    private void generateFiveCardCombinations(List<Card> source, int start, List<Card> current, List<List<Card>> result) {
        if (current.size() == 5) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < source.size(); i++) {
            current.add(source.get(i));
            generateFiveCardCombinations(source, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }
    
    private CardPattern mapPatternType(PatternRecognizer.PatternType type) {
        if (type == null) return CardPattern.INVALID;
        switch (type) {
            case SINGLE: return CardPattern.SINGLE;
            case PAIR: return CardPattern.PAIR;
            case TRIPLE: return CardPattern.TRIPLE;
            case QUADRUPLE: return CardPattern.QUADRUPLE;
            case STRAIGHT: return CardPattern.STRAIGHT;
            case FLUSH: return CardPattern.FLUSH;
            case FULL_HOUSE: return CardPattern.FULL_HOUSE;
            case IRON_BRANCH: return CardPattern.IRON_BRANCH;
            case STRAIGHT_FLUSH: return CardPattern.STRAIGHT_FLUSH;
            default: return CardPattern.INVALID;
        }
    }
}
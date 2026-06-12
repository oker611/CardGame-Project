# AI策略文档

> 锄大地扑克牌游戏的AI决策系统设计与实现

---

## 目录

1. [概述](#1-概述)
2. [架构设计](#2-架构设计)
3. [核心算法](#3-核心算法)
   - 3.1 贪心算法
   - 3.2 蒙特卡洛算法
   - 3.3 自适应算法
4. [设计模式应用](#4-设计模式应用)
   - 4.1 策略模式
   - 4.2 状态模式
   - 4.3 代理模式
5. [LLM集成](#5-llm集成)
6. [性能优化](#6-性能优化)
7. [代码示例](#7-代码示例)
8. [使用说明](#8-使用说明)
9. [扩展方向](#9-扩展方向)

---

## 1. 概述

本项目实现了一个**三层AI决策系统**，为锄大地扑克牌游戏提供不同难度的AI对手：

| 难度 | 算法 | 复杂度 | 特点 |
|------|------|--------|------|
| 简单 | 贪心算法 | O(n) | 快速决策，适合新手 |
| 中等/困难 | 蒙特卡洛算法 | O(n×k×m) | 模拟推演，策略性强 |
| 智能 | 自适应算法 | O(n×k×m) | 学习玩家风格，越玩越聪明 |

---

## 2. 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                      GameController                            │
│  - 策略工厂（根据难度创建AI策略）                              │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                AIDecisionStrategy（接口）                      │
│  + decidePlay(Player, GameState): List<Card>                  │
│  + recordPlayFailure(): void                                  │
│  + resetFailCount(): void                                     │
└─────────────────────────────────────────────────────────────────┘
                            │
            ┌───────────────┼───────────────┐
            │               │               │
            ▼               ▼               ▼
┌─────────────────┐ ┌─────────────────┐ ┌───────────────────────┐
│ GreedyAIDS      │ │ MonteCarloAIDS  │ │ AdaptiveAIDS          │
│ (贪心策略)       │ │ (蒙特卡洛策略)   │ │ (自适应策略)          │
│                 │ │                 │ │ - 代理MonteCarlo      │
│ - Normal        │ │ - PhaseManager  │ │ - HumanStyleProfile   │
│ - Aggressive    │ │ - CandidateGen  │ │ - 风格学习            │
│ - Defensive     │ │ - WorldSampler  │ │                       │
└─────────────────┘ └─────────────────┘ └───────────────────────┘
```

---

## 3. 核心算法

### 3.1 贪心算法 (GreedyAIDecisionStrategy)

**原理**：枚举所有合法出牌组合，选择当前最优解。

**决策流程**：
1. 生成所有合法出牌组合
2. 如果上家出了牌，过滤出能压的组合
3. 按牌面大小排序，选择最小能压制的牌（节省大牌）
4. 根据激进/保守风格调整选择偏好

**适用场景**：简单难度，追求快速决策。

**核心代码**：
```java
@Override
public List<Card> decidePlay(Player aiPlayer, GameState gameState) {
    List<Card> hand = aiPlayer.getHandCards();
    List<List<Card>> allPlays = generateAllValidPlays(hand);
    
    if (lastPlay != null) {
        allPlays = filterBeatablePlays(allPlays, lastPlay.getCards());
    }
    
    allPlays.sort(Comparator.comparingInt(this::getPlayCompareValue));
    return allPlays.isEmpty() ? null : allPlays.get(0);
}
```

### 3.2 蒙特卡洛算法 (MonteCarloAIDecisionStrategy)

**原理**：通过多次随机模拟评估每个候选动作的期望收益。

**决策流程**：
1. **残局强制压牌**：对手≤2张牌时优先压牌
2. **生成候选动作**：枚举合法出牌，截取Top-K候选
3. **采样对手手牌**：随机采样多个可能的手牌分布（世界）
4. **模拟游戏**：在每个世界中模拟游戏进行
5. **计算评分**：统计胜率和得分
6. **选择最优**：选择评分最高的动作

**核心参数**：
```java
private static final int NUM_SAMPLES = 10;        // 模拟世界数量
private static final int TOP_K_CANDIDATES = 4;    // 候选动作数
private static final long DECISION_TIMEOUT_MS = 1500; // 超时时间
```

**适用场景**：中等/困难难度，追求高质量决策。

### 3.3 自适应算法 (AdaptiveAIDecisionStrategy)

**原理**：在蒙特卡洛算法基础上添加风格学习能力。

**设计模式**：代理模式（包装蒙特卡洛策略）

**学习流程**：
1. **数据收集**：记录人类玩家的出牌历史
2. **风格分析**：调用LLM分析玩家风格（激进/保守/均衡）
3. **策略调整**：根据风格调整进攻/防守因子
4. **跨局记忆**：保存学习结果到SharedPreferences

**风格映射**：
| 玩家风格 | 进攻因子 | 防守因子 |
|----------|----------|----------|
| 激进 | 1.2 | 0.8 |
| 保守 | 1.5 | 0.5 |
| 均衡 | 1.0 | 1.0 |

**核心代码**：
```java
public class AdaptiveAIDecisionStrategy implements AIDecisionStrategy {
    private final MonteCarloAIDecisionStrategy monteCarloStrategy;
    private HumanStyleProfile humanStyleProfile;
    
    @Override
    public List<Card> decidePlay(Player aiPlayer, GameState gameState) {
        if (humanStyleProfile != null) {
            applyStyleToFactors(); // 根据风格调整因子
        }
        return monteCarloStrategy.decidePlay(aiPlayer, gameState); // 委托执行
    }
}
```

**适用场景**：智能模式，追求个性化对战体验。

---

## 4. 设计模式应用

### 4.1 策略模式

**定义**：定义一系列算法，封装每个算法，使它们可以互换。

**应用**：`AIDecisionStrategy`接口统一三种AI策略。

**优势**：
- **开闭原则**：新增策略只需实现接口
- **运行时切换**：根据难度动态切换策略
- **可测试性**：策略独立，易于单元测试

### 4.2 状态模式

**定义**：允许对象在内部状态改变时改变行为。

**应用**：`PhaseManager`管理AI在EARLY/MID/LATE阶段的策略。

**阶段划分**：
| 阶段 | 手牌数 | 策略 |
|------|--------|------|
| EARLY | >10 | 优先出小牌，保留大牌 |
| MID | 5-10 | 平衡攻防，注意配合 |
| LATE | <5 | 强制压牌，阻止对手获胜 |

### 4.3 代理模式

**定义**：为其他对象提供代理以控制对这个对象的访问。

**应用**：`AdaptiveAIDecisionStrategy`代理`MonteCarloAIDecisionStrategy`。

**优势**：
- **代码复用**：复用蒙特卡洛算法逻辑
- **功能增强**：添加学习功能而不修改原代码
- **透明访问**：调用者无需知道代理存在

---

## 5. LLM集成

### 5.1 风格分析流程

```
游戏进行中 → 收集出牌记录 → 游戏结束 → HumanStyleAnalyzer → LLM分析 → 更新风格 → 保存记忆
```

### 5.2 两种风格分析器

| 分析器 | 用途 | 分析对象 | 调用时机 |
|--------|------|----------|----------|
| `HumanStyleAnalyzer` | 学习人类玩家风格 | 本地人类玩家 | 游戏结束时 |
| `OpponentStyleAnalyzer` | 分析对手风格 | 所有对手 | 游戏进行中 |

### 5.3 LLM调用机制

```java
// 调用Vivo蓝心分析风格
String llmResponse = llmAnalyzer.summarizeOpponentStyle(historySummary);

// 解析结果
if (llmResponse.contains("激进")) {
    profile.setStyleLabel(STYLE_AGGRESSIVE);
} else if (llmResponse.contains("保守")) {
    profile.setStyleLabel(STYLE_CONSERVATIVE);
} else {
    profile.setStyleLabel(STYLE_BALANCED);
}
```

### 5.4 本地Fallback

当LLM调用失败时，使用本地规则分析：

```java
// 统计规则
if (comboRatio > 0.4) → 激进（喜欢出组合牌）
if (singleRatio > 0.6) → 保守（总是出单张）
if (bigCardRatio > 0.5) → 激进（经常出大牌）
```

---

## 6. 性能优化

### 6.1 异步执行

```java
ExecutorService executor = Executors.newSingleThreadExecutor();
Future<Map<Play, Double>> future = executor.submit(() -> {
    return evaluateCandidates(candidates);
});
Map<Play, Double> scores = future.get(1500, TimeUnit.MILLISECONDS);
```

### 6.2 超时机制

```java
try {
    return future.get(DECISION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
} catch (TimeoutException e) {
    // 超时返回启发式最佳
    return candidates.get(0).getCards();
}
```

### 6.3 候选截断

```java
// 只保留Top-K最有潜力的候选
List<Play> candidates = candidateGenerator.generate(hand, lastPlay);
candidates = candidates.subList(0, Math.min(TOP_K_CANDIDATES, candidates.size()));
```

---

## 7. 代码示例

### 7.1 策略接口

```java
public interface AIDecisionStrategy {
    List<Card> decidePlay(Player aiPlayer, GameState gameState);
    void recordPlayFailure();
    void resetFailCount();
}
```

### 7.2 策略工厂

```java
private void initAIEventListener() {
    String difficulty = prefs.getString("ai_difficulty", "MEDIUM");
    
    switch (difficulty) {
        case "EASY":
            aiStrategy = new GreedyAIDecisionStrategy(ruleConfig);
            break;
        case "MEDIUM":
            aiStrategy = new MonteCarloAIDecisionStrategy();
            break;
        case "HARD":
            aiStrategy = new AdaptiveAIDecisionStrategy();
            break;
    }
}
```

### 7.3 残局强制压牌

```java
// 优先级高于蒙特卡洛评分
if (minOpponentHandSize <= 2) {
    Play forcedPlay = findAnyValidBeatPlay(hand, lastPlay, isFirstRound, isFirstTurn);
    if (forcedPlay != null) {
        return forcedPlay.getCards();
    }
}
```

---

## 8. 使用说明

### 8.1 设置难度

```java
// 在SharedPreferences中设置难度
SharedPreferences prefs = getSharedPreferences("game_prefs", MODE_PRIVATE);
prefs.edit().putString("ai_difficulty", "HARD").apply();
```

### 8.2 启用风格学习

```java
// 在SharedPreferences中启用风格学习
prefs.edit().putBoolean("enable_style_learning", true).apply();
```

### 8.3 获取风格分析结果

```java
// 游戏结束时获取分析结果
String summary = gameController.getStyleAnalysisSummary();
```

---

## 9. 扩展方向

### 9.1 算法扩展

- [ ] 引入强化学习策略
- [ ] 实现深度蒙特卡洛树搜索（Deep MCTS）
- [ ] 添加基于规则的专家系统

### 9.2 功能扩展

- [ ] AI思考过程可视化
- [ ] 策略组合模式（多种策略加权组合）
- [ ] 跨游戏记忆（学习不同玩家的风格）

### 9.3 性能优化

- [ ] GPU加速蒙特卡洛模拟
- [ ] 分布式计算支持
- [ ] 增量学习机制

---

## 附录：类图

```
┌─────────────────────────────────────────────────────────────────┐
│                    AIDecisionStrategy                          │
│  + decidePlay(Player, GameState): List<Card>                  │
│  + recordPlayFailure(): void                                  │
│  + resetFailCount(): void                                     │
└─────────────────────────────────────────────────────────────────┘
                              △
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌───────────────┐    ┌───────────────────┐    ┌───────────────────────┐
│ GreedyAIDS    │    │ MonteCarloAIDS   │    │ AdaptiveAIDS          │
│               │    │                   │    │                       │
│ - ruleConfig  │    │ - numSamples     │    │ - monteCarloStrategy  │
│ - style       │    │ - topKCandidates │    │ - humanStyleProfile   │
└───────┬───────┘    └───────────────────┘    └───────────────────────┘
        │
        △
   ┌────┴────────────┐
   │                 │
   ▼                 ▼
┌───────────┐  ┌───────────┐  ┌───────────┐
│NormalAIDS │  │Aggressive│  │DefensiveAI│
│           │  │AIDS       │  │S          │
└───────────┘  └───────────┘  └───────────┘
```

---

## License

MIT License

---

*Last updated: June 2026*

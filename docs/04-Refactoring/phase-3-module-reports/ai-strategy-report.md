# AI 策略模块设计模式应用报告

## 1. 概述

本项目的 AI 策略模块采用了多种经典设计模式，实现了灵活的难度分层和策略切换机制。核心设计目标包括：

- **可扩展性**：支持新增策略类型而不修改现有代码
- **可替换性**：运行时动态切换不同难度的 AI 策略
- **可维护性**：职责清晰，易于理解和维护
- **性能优化**：不同难度使用不同复杂度的算法

---

## 2. 核心设计模式分析

### 2.1 策略模式 (Strategy Pattern)

**模式定义**：定义一系列算法，把它们封装起来，并且使它们可以相互替换。

**应用位置**：AI 决策策略的核心架构

**类图结构**：

```
┌─────────────────────────────────────────────────────────────────┐
│                    AIDecisionStrategy (接口)                    │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  + decidePlay(Player, GameState): List<Card>           │   │
│  │  + recordPlayFailure(): void                           │   │
│  │  + resetFailCount(): void                             │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              △
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌───────────────┐    ┌───────────────────┐    ┌───────────────────────┐
│ GreedyAIDS    │    │ MonteCarloAIDS   │    │ AdaptiveAIDS          │
│ (贪心策略)     │    │ (蒙特卡洛策略)    │    │ (自适应策略)          │
└───────────────┘    └───────────────────┘    └───────────────────────┘
        △
        │
   ┌────┴────┐
   │         │
   ▼         ▼
┌─────────┐ ┌───────────┐
│NormalAI │ │Aggressive │
│(普通)   │ │AI(激进)   │
└─────────┘ └───────────┘
   │         │
   └────┬────┘
        ▼
┌───────────┐
│DefensiveAI│
│(保守)     │
└───────────┘
```

**策略层次结构**：

| 策略类 | 继承关系 | 算法复杂度 | 适用难度 |
|--------|----------|------------|----------|
| `GreedyAIDecisionStrategy` | 实现 `AIDecisionStrategy` | O(n) 贪心枚举 | 简单 |
| `NormalAIDecisionStrategy` | 继承 `GreedyAIDecisionStrategy` | O(n) | 简单-普通 |
| `AggressiveAIDecisionStrategy` | 继承 `GreedyAIDecisionStrategy` | O(n) | 简单-激进 |
| `DefensiveAIDecisionStrategy` | 继承 `GreedyAIDecisionStrategy` | O(n) | 简单-保守 |
| `MonteCarloAIDecisionStrategy` | 实现 `AIDecisionStrategy` | O(n * k * m) | 中等 |
| `AdaptiveAIDecisionStrategy` | 实现 `AIDecisionStrategy` | O(n * k * m) | 困难/智能 |

**关键代码示例**：

```java
// 策略接口定义
public interface AIDecisionStrategy {
    List<Card> decidePlay(Player aiPlayer, GameState gameState);
    void recordPlayFailure();
    void resetFailCount();
}

// 贪心策略实现
public class GreedyAIDecisionStrategy implements AIDecisionStrategy {
    @Override
    public List<Card> decidePlay(Player aiPlayer, GameState gameState) {
        // 贪心算法：枚举所有可能，选择最小能压牌的组合
        List<List<Card>> allPlays = generateAllValidPlays(hand);
        // ... 筛选、排序、选择最优
    }
}

// 蒙特卡洛策略实现
public class MonteCarloAIDecisionStrategy implements AIDecisionStrategy {
    @Override
    public List<Card> decidePlay(Player aiPlayer, GameState gameState) {
        // 蒙特卡洛模拟：生成候选动作，模拟多次游戏，选择胜率最高的
        List<Play> candidates = candidateGenerator.generate(hand, lastPlay, ...);
        // ... 模拟评估、评分选择
    }
}
```

**设计优势**：

1. **开闭原则**：新增策略只需实现接口，无需修改现有代码
2. **单一职责**：每个策略专注于一种决策算法
3. **可测试性**：策略独立，易于单元测试
4. **运行时切换**：支持动态切换策略（如根据难度设置）

---

### 2.2 代理模式 / 装饰器模式 (Proxy / Decorator Pattern)

**模式定义**：为其他对象提供一种代理以控制对这个对象的访问，或在不改变接口的前提下增强功能。

**应用位置**：`AdaptiveAIDecisionStrategy`

**设计意图**：在保持 `AIDecisionStrategy` 接口一致性的同时，包装 `MonteCarloAIDecisionStrategy` 并添加自适应学习功能。

**核心实现**：

```java
public class AdaptiveAIDecisionStrategy implements AIDecisionStrategy {
    // 代理的目标策略
    private final MonteCarloAIDecisionStrategy monteCarloStrategy;
    private HumanStyleProfile humanStyleProfile;

    @Override
    public List<Card> decidePlay(Player aiPlayer, GameState gameState) {
        // 委托给蒙特卡洛策略执行实际决策
        return monteCarloStrategy.decidePlay(aiPlayer, gameState);
    }

    // 增强功能：根据玩家风格调整策略因子
    private void applyStyleToFactors() {
        if (humanStyleProfile.isAggressive()) {
            monteCarloStrategy.setAggressivenessFactor(1.2);
            monteCarloStrategy.setDefenseFactor(0.8);
        }
        // ... 其他风格适配
    }
}
```

**代理模式特征**：

| 特征 | 实现方式 |
|------|----------|
| **接口一致性** | 实现相同的 `AIDecisionStrategy` 接口 |
| **委托执行** | 内部持有 `MonteCarloAIDecisionStrategy` 实例 |
| **增强功能** | 通过 `applyStyleToFactors()` 动态调整策略参数 |
| **透明访问** | 调用者无需知道代理存在 |

---

### 2.3 简单工厂模式 (Simple Factory Pattern)

**模式定义**：定义一个创建对象的接口，让子类决定实例化哪个类。

**应用位置**：`GameController` 中的策略初始化

**工厂逻辑**：

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
            if (adaptiveAI == null) {
                adaptiveAI = new AdaptiveAIDecisionStrategy();
            }
            aiStrategy = adaptiveAI;
            break;
        default:
            aiStrategy = new MonteCarloAIDecisionStrategy();
    }
    
    aiEventListener = new AIEventListener(this, gameEngine, aiStrategy, aiHost);
}
```

**难度映射关系**：

| 用户选择 | AI 策略实现 | 算法类型 |
|----------|-------------|----------|
| 简单 (EASY) | `GreedyAIDecisionStrategy` | 贪心枚举 |
| 中等 (MEDIUM) | `MonteCarloAIDecisionStrategy` | 蒙特卡洛模拟 |
| 困难/智能 (HARD) | `AdaptiveAIDecisionStrategy` | 自适应蒙特卡洛 |

---

### 2.4 模板方法模式 (Template Method Pattern)

**模式定义**：定义一个算法骨架，将某些步骤延迟到子类实现。

**应用位置**：`GreedyAIDecisionStrategy` 的风格配置

**模板方法结构**：

```java
public class GreedyAIDecisionStrategy implements AIDecisionStrategy {
    // 模板方法：统一的决策流程
    @Override
    public List<Card> decidePlay(Player aiPlayer, GameState gameState) {
        // 步骤1: 生成所有合法出牌组合
        List<List<Card>> allPlays = generateAllValidPlays(hand);
        
        // 步骤2: 首轮首出检查
        if (isFirstRound && isFirstTurn) {
            allPlays = filterMustIncludeRequiredOpeningCard(allPlays);
        }
        
        // 步骤3: 压牌过滤
        if (lastPlayCards != null) {
            allPlays = filterBeatablePlays(allPlays, lastPlayCards);
        }
        
        // 步骤4: 根据风格选择（可变部分）
        return selectBestPlay(allPlays);
    }
    
    // 可覆盖的钩子方法：根据风格调整选择逻辑
    private List<Card> selectBestPlay(List<List<Card>> plays) {
        // 默认：按比较值从小到大排序，选最小的
        plays.sort(Comparator.comparingInt(this::getPlayCompareValue));
        return plays.get(0);
    }
}

// 子类通过构造函数参数调整风格
public class AggressiveAIDecisionStrategy extends GreedyAIDecisionStrategy {
    public AggressiveAIDecisionStrategy(RuleConfig ruleConfig) {
        super(ruleConfig, Style.AGGRESSIVE); // 设置激进风格
    }
}
```

**风格参数配置**：

```java
public void setStyle(Style style) {
    this.style = style;
    switch (style) {
        case AGGRESSIVE:
            this.aggressivenessFactor = 1.3;
            this.defenseFactor = 0.7;
            break;
        case DEFENSIVE:
            this.aggressivenessFactor = 0.7;
            this.defenseFactor = 1.3;
            break;
        default:
            this.aggressivenessFactor = 1.0;
            this.defenseFactor = 1.0;
    }
}
```

---

## 3. 设计模式协作关系

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        GameController (工厂)                           │
│                              │                                        │
│                              ▼                                        │
│              ┌───────────────────────────────────┐                    │
│              │     AIDecisionStrategy (接口)     │                    │
│              └─────────────────┬─────────────────┘                    │
│                                │                                      │
│     ┌──────────────────────────┼──────────────────────────┐            │
│     │                          │                          │            │
│     ▼                          ▼                          ▼            │
│ ┌───────────┐          ┌───────────────┐        ┌─────────────────┐   │
│ │  Greedy   │          │ MonteCarlo    │        │   Adaptive      │   │
│ │ Strategy  │          │   Strategy    │        │   Strategy      │   │
│ └─────┬─────┘          └───────┬───────┘        └────────┬────────┘   │
│       │                        │                         │            │
│       ▼                        │                         ▼            │
│ ┌─────────────────┐            │              ┌─────────────────┐    │
│ │ Normal/Aggressive/Defensive │            │  MonteCarlo      │    │
│ │ (风格变体)       │            │              │  (被代理)       │    │
│ └─────────────────┘            │              └─────────────────┘    │
│                                │                                      │
│                                ▼                                      │
│                   ┌─────────────────────┐                            │
│                   │   CandidateGenerator│ (策略内部组件)             │
│                   │   MonteCarloSimulator│                          │
│                   │   OpponentHandSampler│                          │
│                   └─────────────────────┘                            │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 4. 设计模式应用总结

| 设计模式 | 应用位置 | 解决的问题 | 带来的价值 |
|----------|----------|------------|------------|
| **策略模式** | 整体架构 | 不同难度需要不同决策算法 | 开闭原则、可扩展性、可测试性 |
| **代理模式** | `AdaptiveAIDS` | 在不修改蒙特卡洛策略的前提下添加自适应能力 | 代码复用、功能增强、透明访问 |
| **简单工厂** | `GameController` | 根据配置动态创建策略实例 | 解耦创建逻辑、统一入口 |
| **模板方法** | `GreedyAIDS` | 统一决策流程，允许风格定制 | 代码复用、流程标准化 |

---

## 5. 架构优势

### 5.1 难度分层设计

```
用户选择难度
       │
       ▼
┌─────────────────────────────────────────┐
│ 简单模式 ──▶ GreedyAIDS (O(n) 贪心)    │
│ 中等模式 ──▶ MonteCarloAIDS (O(n*k*m)) │
│ 困难模式 ──▶ AdaptiveAIDS              │
│             (MonteCarlo + 风格学习)     │
└─────────────────────────────────────────┘
```

### 5.2 智能模式增强

**自适应策略工作流程**：

1. **风格识别**：通过 `HumanStyleAnalyzer` 分析玩家出牌风格
2. **策略调整**：根据识别结果动态调整进攻/防守因子
3. **决策执行**：委托蒙特卡洛策略执行决策
4. **持续学习**：游戏结束后更新风格档案，支持跨局记忆

---

## 6. 扩展建议

### 6.1 策略模式扩展

如需新增策略类型，只需：

1. 实现 `AIDecisionStrategy` 接口
2. 在工厂方法中添加新分支

```java
// 新增示例：强化学习策略
public class ReinforcementLearningStrategy implements AIDecisionStrategy {
    @Override
    public List<Card> decidePlay(Player aiPlayer, GameState gameState) {
        // RL 决策逻辑
    }
}
```

### 6.2 工厂模式优化

当前使用简单工厂，可进一步升级为 **抽象工厂模式** 或 **策略工厂模式**，支持更复杂的策略组合。

---

## 7. 结论

本项目的 AI 策略模块通过多种设计模式的组合应用，实现了：

1. **高内聚低耦合**：策略独立，职责清晰
2. **可扩展性**：新增策略无需修改现有代码
3. **性能分级**：不同难度使用不同复杂度算法
4. **智能自适应**：通过代理模式实现风格学习
5. **易于测试**：策略独立，支持单元测试

设计模式的合理应用使得 AI 策略模块具备良好的架构弹性，能够适应未来的功能扩展和性能优化需求。

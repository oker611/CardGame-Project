# GoF 设计模式应用汇总 — 锄大地卡牌游戏

> 第3周交付 | 更新时间：2026-06-12

---

## 1. 策略模式 (Strategy Pattern)

**定义**：定义一系列算法，把它们封装起来，并且使它们可以相互替换。

**应用位置**：AI 决策核心架构

**UML 结构**：
```
AIDecisionStrategy (interface)
  ├── GreedyAIDecisionStrategy — 贪心枚举 O(n)
  │     ├── NormalAIDecisionStrategy
  │     ├── AggressiveAIDecisionStrategy
  │     └── DefensiveAIDecisionStrategy
  ├── MonteCarloAIDecisionStrategy — 蒙特卡洛模拟 O(n*k*m)
  └── AdaptiveAIDecisionStrategy — 自适应蒙特卡洛
```

**解决的问题**：不同难度需要不同决策算法，支持运行时切换。

**关键代码位置**：
- 接口：`app/src/main/java/com/example/cardgame/ai/AIDecisionStrategy.java`
- 实现：`GreedyAIDecisionStrategy.java`, `MonteCarloAIDecisionStrategy.java`, `AdaptiveAIDecisionStrategy.java`
- 工厂：`AIStrategyFactory.java`

---

## 2. 观察者模式 (Observer Pattern)

**定义**：定义对象间一对多依赖，当一个对象状态变化时所有依赖自动收到通知。

**应用位置**：全局事件总线

**UML 结构**：
```
IEventBus (interface)
  └── EventBus (CopyOnWriteArrayList<GameEventListener>)
        ├── UI Observer: GameActivity implements GameEventListener
        ├── AI Observer: AIEventListener
        ├── Bluetooth Relay: BluetoothEventRelay
        └── Engine Publisher: GameEngine, TurnManager

事件类型: CardPlayedEvent / PlayerPassedEvent / TurnChangedEvent / GameOverEvent
```

**解决的问题**：UI刷新、AI决策、蓝牙同步三大模块解耦，新增观察者不影响已有代码。

**关键代码位置**：
- 接口：`app/src/main/java/com/example/cardgame/event/IEventBus.java`
- 实现：`EventBus.java`
- 事件：`CardPlayedEvent.java`, `PlayerPassedEvent.java`, `TurnChangedEvent.java`, `GameOverEvent.java`
- 已注入到：`GameEngine`, `TurnManager`

---

## 3. 工厂模式 (Factory Pattern)

**定义**：定义创建对象的接口，让子类决定实例化哪个类。

**应用位置**：AI 策略统一创建入口

**关键方法**：
```java
AIStrategyFactory.create(AIDifficulty, RuleConfig)      // 按难度创建
AIStrategyFactory.createGreedyWithStyle(AIStrategyStyle, RuleConfig) // 按风格创建
AIStrategyFactory.createAdaptiveIfNeeded(existing, config, profile)  // 复用或新建
```

**解决的问题**：集中管理 7 种 AI 策略的依赖组装（6 个子依赖注入），调用方无需知道具体构造逻辑。

**关键代码位置**：`app/src/main/java/com/example/cardgame/ai/AIStrategyFactory.java`

---

## 4. 适配器模式 (Adapter Pattern)

**定义**：将一个类的接口转换为客户端期望的另一个接口。

**应用位置两处**：

**A. BluetoothEventRelay** — 适配 `GameEvent` 到蓝牙消息：
```
GameEventListener (目标接口)
  └── BluetoothEventRelay (适配器)
        └── BluetoothGateway (被适配者)
```
- 守卫模式：检查 `player.getType() != REMOTE` 防止回声循环

**B. AdaptiveAIDecisionStrategy** — 包装 MonteCarlo 并添加风格学习：
```
AIDecisionStrategy (目标接口)
  └── AdaptiveAIDecisionStrategy (适配器)
        └── MonteCarloAIDecisionStrategy (被适配者)
```
- 增强功能：`applyStyleToFactors()` 根据 HumanStyleProfile 调整进攻/防守因子

**关键代码位置**：
- `app/src/main/java/com/example/cardgame/controller/BluetoothEventRelay.java`
- `app/src/main/java/com/example/cardgame/ai/AdaptiveAIDecisionStrategy.java`

---

## 5. Builder 模式 (Builder Pattern)

**定义**：将一个复杂对象的构建与它的表示分离。

**应用位置**：规则引擎配置

```java
RuleConfig.SOUTHERN   // ♦3首出必选, 南方花色权重
RuleConfig.NORTHERN   // 无首轮限制, 花色颠倒, 禁用铁支

// Builder 创建新规则变体：
new RuleConfig.Builder()
    .requiredOpeningRank(Rank.THREE)
    .requiredOpeningSuit(Suit.DIAMOND)
    .build()
```

**解决的问题**：支持南方/北方双规则体系，不可变对象保证线程安全。

**关键代码位置**：`app/src/main/java/com/example/cardgame/rule/RuleConfig.java`

---

## 6. 单例模式 (Singleton Pattern)

**定义**：确保一个类只有一个实例，并提供全局访问点。

**应用位置**：EventBus（Holder 静态内部类线程安全实现）

```java
public class EventBus implements IEventBus {
    private static class Holder {
        private static final EventBus INSTANCE = new EventBus();
    }
    public static EventBus getInstance() { return Holder.INSTANCE; }
}
```

**注意**：虽然 `getInstance()` 保留为便捷方法，但 `GameEngine` 和 `TurnManager` 已改为通过 `IEventBus` 构造器注入，便于单元测试。

**关键代码位置**：`app/src/main/java/com/example/cardgame/event/EventBus.java`

---

## 7. 模板方法模式 (Template Method Pattern)

**定义**：定义算法骨架，将某些步骤延迟到子类实现。

**应用位置**：`GreedyAIDecisionStrategy.decidePlay()`

```
1. 生成所有合法出牌组合 (generateAllValidPlays)
2. 首轮首出检查 (filterMustIncludeRequiredOpeningCard)
3. 压牌过滤 (filterBeatablePlays)
4. 风格选择 (selectBestPlay) ← 可变部分，子类覆写
```

**关键代码位置**：`app/src/main/java/com/example/cardgame/ai/GreedyAIDecisionStrategy.java`

---

## 设计模式应用统计

| 模式 | 应用数 | 评分维度 |
|------|--------|---------|
| Strategy | 7 个策略变体 | 可扩展性 |
| Observer | 3 个观察者 + 4 类事件 | 解耦 |
| Factory | 1 个工厂 (3 创建方法) | 封装 |
| Adapter | 2 处 | 兼容性 |
| Builder | 1 个 (2 预设 + 自定义) | 灵活性 |
| Singleton | 1 个 (支持注入) | 性能 |
| Template Method | 1 个 | 一致性 |

**总计：6+1 种设计模式，覆盖 7 个核心模块。**

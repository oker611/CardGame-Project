# 核心逻辑层设计模式引入评估报告

## 评估范围

`engine/`、`rule/`核心逻辑模块。

## 评估原则

**收益 > 成本**。引入设计模式的前提是：改善效果 > 工作量 + 引入的复杂度。不为了"用上模式"而用模式。

---

## 一、已有模式盘点（现状良好）

| 模式 | 位置 | 作用 |
|---|---|---|
| Facade | `RuleEngine` 封装 `PatternRecognizer` + `PlayValidator` | 对外暴露统一校验入口，调用方无需了解内部两阶段校验 |
| Delegation | `GameEngine` → `DealManager` / `TurnManager` / `SettlementManager` | 发牌、轮转、结算三个生命周期阶段各司其职 |
| MVC | `GameController` 隔离 UI 和 Engine | UI 只调 `GameActionHandler` 接口，只消费 `GameViewData`，不触碰 `GameState` |
| Observer | `BluetoothEventListener`、`Runnable` 回调 | 网络事件和 UI 刷新的回调通知 |
| DTO | `dto/` 包下 `GameViewData`、`PlayResult` 等 | 内部模型和 UI 展示数据严格隔离 |

**结论**：引擎层已有 5 个恰当的模式在支撑架构，分层干净，职责清晰。

---

## 二、不应该引入的设计模式

### 2.1 状态模式 (State Pattern) —— **不引入**

**当前做法**：`GameState` 用 `openingTurn` (boolean) + `gameOver` (boolean) + `lastPlay == null` 判空来管理游戏阶段。实际游戏只有 4 个隐含阶段：发牌 → 出牌循环 → 结算 → 结束。

**状态模式要求**：定义 `GamePhase` 接口 + 4~5 个 State 实现类 + `GameEngine` 维护状态转移逻辑。

**不引入的原因**：
- 锄大地的回合流程极其简单且固定。`playCards()` 和 `passTurn()` 里的 guard clause（校验是否轮到你、游戏是否结束、是否首轮必出）总共不到 10 行，用 boolean + null check 表达已足够直观。
- 引入状态模式后，这些 guard 逻辑不会消失，只是从 `if` 语句变成各自 State 类里的方法调用。代码行数反而增加（5+ 个新文件），调用链多一层间接。
- 状态模式的真正价值体现在**10+ 状态、复杂转移规则**的场景（如格斗游戏连招状态机、TCP 连接状态机），而非回合制卡牌游戏的简单线性流程。

**结论**：收益几乎为零（现有 10 行 guard 已经清晰），成本是新增 5+ 个类和一层抽象——明显不划算。

---

### 2.2 模板方法模式 (Template Method) —— **不引入，用更简单的方案替代**

**当前做法**：`AIPlayer` 中 `findHigherPair`、`findHigherTriple`、`findHigherQuadruple` 三个方法结构完全一致，唯一区别是 `size() >= 2/3/4`。共约 60 行高度重复代码。

**模板方法模式要求**：抽象父类定义算法骨架（分组 → 过滤 → 排序 → 取最小），子类覆盖 `getRequiredCount()` 等方法。

**不引入的原因**：
- 模板方法通过**继承**消除重复，引入了父类-子类的层次关系，阅读时需要跨文件追踪。
- 这个场景下**组合优于继承**：直接提取一个参数化方法即可解决：

```java
// 三个方法合并为一个
private List<Card> findHigherGroup(int requiredCount, List<Card> lastGroup) {
    // 一份逻辑，requiredCount 参数化
}
```

- 一行参数解决的事，不需要引入抽象父类 + 3 个子类的继承体系。
- 消除的重复代码量（~60 行）和引入的复杂度（新的继承层次）不成比例。

**替代方案**：简单方法提取，零新类，零继承，效果等同。

---

### 2.3 工厂方法模式 (Factory Method) —— **不引入**

**不引入的原因**：
- `Card`、`Player`、`GameState` 均为简单 POJO，只用 `new` 直接构造。不存在多态替换需求，不存在复杂构造逻辑。
- `CardFactory`、`PlayerFactory` 本质是把 `new Card(...)` 包装成 `factory.createCard(...)`——改变的是调用方式，不改变语义，不带来任何多态灵活性。
- 工厂方法的真正价值在于：同一接口创建不同实现（如 `ConnectionFactory` 创建 MySQL/Oracle 连接），或者构造过程涉及多步初始化需要封装。这两个前提在当前代码中均不成立。

**结论**：无多态创建需求，工厂方法是纯开销。

---

## 三、适于多规则场景的设计模式（因为老师给的文档里面说要加南北规则）

### 3.0 南北规则变体的典型差异

| 差异维度 | 南方（港式，当前实现） | 可能的北方变体 |
|---|---|---|
| 首轮必出牌 | ♦3 | ♣3 / ♠3 / 无限制 |
| 花色排序 | ♦ < ♣ < ♥ < ♠ | 可能颠倒、或不计花色 |
| 点数排序 | 3 最小 → 2 最大 | 部分变体 A 最大、2 降级 |
| 合法牌型 | 单/对/三/四/顺/花/葫/铁/同花顺 | 可能增减（如禁用铁支、加入两对） |
| 五牌优先级 | 顺 < 花 < 葫 < 铁 < 同花顺 | 可能调整 |
| 连续 Pass 重置 | 3 人 | 可能 2 人或 4 人 |
| 计分 | 无 | 可能有点数倍率、剩余牌计分 |

关键判断：这些差异哪些是**参数化**的（改一个值即可），哪些是**算法级**的（需要不同的代码逻辑）。

---

### 3.1 策略模式 (Strategy) on RuleEngine —— **强烈推荐**

**当前状态**：`RuleEngine` 是具体类（36 行，2 个方法），`RuleConfig` 是空壳占位类。

```java
// 现状：硬编码规则，无法切换
public class RuleEngine {                    // 具体类，无接口
    private final PatternRecognizer recognizer = new PatternRecognizer();
    private final PlayValidator validator = new PlayValidator();
    public PatternInfo recognizePattern(List<Card> cards) { ... }
    public ValidationResult validatePlay(...) { ... }
}

public class RuleConfig {
    // 完全为空，预留的扩展点从未被使用
}
```

**推荐方案**：

```
RuleEngine (interface)                ← 新增接口
    ↑
    ├── ConfigurableRuleEngine        ← 主力实现，读 RuleConfig 覆盖 90% 参数化差异
    └── StandardRuleEngine            ← 当前硬编码逻辑重命名，作为默认/兜底
```

- `RuleEngine` 改为接口，两个方法签名不变：`recognizePattern()` / `validatePlay()`
- `RuleConfig` 从空壳变为配置对象，承载花色权重表、点数权重表、首轮必出牌条件、合法牌型集合、五牌优先级映射、连续 Pass 重置阈值等参数
- 提供预置静态配置：`RuleConfig.STANDARD` / `RuleConfig.SOUTHERN` / `RuleConfig.NORTHERN`
- 某个变体有算法级差异（如跨牌型升级、全新牌型）时，直接新增 `RuleEngine` 实现类

**为什么这次收益 > 成本**：

- **成本极低**：`RuleEngine` 仅 2 个公有方法、36 行，提取接口是分钟级操作。`RuleConfig` 本就是为此预留的扩展点，现在正是填充它的时候。`GameEngine` 通过 `ruleEngine.validatePlay(...)` 调用，改成接口后 **GameEngine 一行不用改**。
- **收益明确**：
  - 每个规则变体要么是一份配置数据（零新代码），要么是一个干净的 `RuleEngine` 实现类
  - 不会出现 `if (southern) { ... } else if (northern) { ... }` 散落在 PatternRecognizer 和 PlayValidator 各处的局面
  - 新增变体 = 新增配置或新增一个类，对现有代码零侵入（开闭原则）
  - 单元测试可以针对每个 `RuleEngine` 实现独立验证规则正确性
- **对比上一轮**：上次针对 AI 策略说"暂缓"，是因为**确实只有一个策略**。现在南北规则是**明确规划要做的需求**，YAGNI 的前提已经消失。模式的触发条件从"不存在"变成了"存在"。

---

### 3.2 模板方法模式 (Template Method) on PlayValidator —— **可选，视差异程度决定**

**仅在以下情况才值得引入**：某个变体需要**改变验证流程的结构**，而不仅仅是改参数。

**例子——不需要模板方法的场景（参数化就够）**：

```java
// 花色排序不同？→ RuleConfig 里换一个权重数组
// 首轮必出牌不同？→ RuleConfig 里换一个 (Rank, Suit) 条件
// 合法牌型不同？ → RuleConfig 里换一个 Set<PatternType>
// 全是数据层面的差异，ConfigurableRuleEngine 直接读配置即可
```

**例子——需要模板方法的场景**：

```java
// 某变体允许"对子压单张"的跨牌型升级 → 验证步骤序列变了
// 某变体要求首轮额外检查"不能出顺子"   → 需要插入新的验证步骤
// 某变体有完全不同的 Pass 判定逻辑      → 条件分支结构变了
```

此时模板方法提供固定骨架 + 可变钩子：

```java
abstract class PlayValidator {
    public ValidationResult validatePlay(...) {
        if (!canPassOnFirstTurn() && isEmpty)        return reject();  // ← 钩子
        if (!isPatternAllowed(pattern))               return reject();  // ← 钩子
        if (!checkFirstCardRule(cards))               return reject();  // ← 钩子
        if (!canBeatLastPlay(pattern, lastPattern))   return reject();  // ← 钩子
        return accept();
    }
    abstract boolean isPatternAllowed(PatternType type);
    abstract boolean checkFirstCardRule(List<Card> cards);
    abstract boolean canBeatLastPlay(...);
}
```

**判断标准**：先上策略模式（`RuleEngine` 接口 + `ConfigurableRuleEngine`）。如果发现两个变体的 `PlayValidator` 验证步骤**流程序列不同**，再在各自实现内部决定是否用模板方法。**不建议率先同时引入两个模式——分层递进，按需追加。**

---

## 四、总结

### 当前阶段（单规则）

```
不引入任何新模式。
已有 Facade + Delegation + MVC + Observer + DTO 足够。
实际改进方向：剥离蓝牙方法 + 参数化 AI 重复方法。
```

### 多规则变体阶段（南北规则）

```
优先：Strategy on RuleEngine → RuleEngine 接口 + ConfigurableRuleEngine + RuleConfig 填充
可选：Template Method on PlayValidator → 仅当某变体需要结构性差异时引入
不需要：Factory / Abstract Factory / State / Command
```

**一以贯之的原则**：模式是手段不是目的。同一个模式（Strategy），单规则时是过度设计，多规则时是最优解——判断标准始终是"收益是否大于成本"，而收益取决于前提条件是否真实存在。

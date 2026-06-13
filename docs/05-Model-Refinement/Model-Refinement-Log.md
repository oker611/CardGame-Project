# Model Refinement Log — 模型精化变更记录

> 第4周要求："refine the above models" | 更新时间：2026-06-12

---

## 1. 类图精化

### 变更记录

| 日期 | 变更 | 原因 |
|------|------|------|
| 2026-05 | `EventBus` 新增 `IEventBus` 接口 | 支持依赖注入，便于单元测试 mock |
| 2026-06 | `GameEngine` 字段类型从具体类改为接口 | `IDealManager`, `ITurnManager`, `ISettlementManager` |
| 2026-06 | `MonteCarloAIDecisionStrategy` 拆为 5 组件 | 单一职责：PatternAnalyzer, CandidateScorer, CandidateGenerator, PhaseManager, MonteCarloSimulator |
| 2026-06 | `BluetoothGateway` 拆为 4 组件 | RoomController, ReliabilityManager, ReconnectionHandler, NetworkGameBridge |
| 2026-06 | 新增 14 个接口 | 支撑完整 DI 体系：AIStrategyStyle, GameStateAccess, ILLMAnalyzer, IVivoLLMClient, IPatternRecognizer, IPlayValidator, IPhaseManager, ICandidateGenerator, IMonteCarloSimulator, IOpponentHandSampler, IDealManager, ITurnManager, ISettlementManager, IEventBus |
| 2026-06 | 删除 `AIPlayer` 类 | 零引用遗留代码 (324行) |
| 2026-06 | `HumanStyleAnalyzer` 去静态化 | 静态线程池改为每实例独立 ExecutorService |
| 2026-06 | `CardGameApplication` 去静态化 | 静态字段改为实例字段 |

### 当前类图结构

```
model/          — Card, Player, Play, GameState, HumanStyleProfile, Rank, Suit
engine/         — GameEngine implements GameStateAccess, IDealManager, ITurnManager, ISettlementManager
rule/           — RuleEngine, PatternRecognizer implements IPatternRecognizer, PlayValidator implements IPlayValidator
ai/             — AIDecisionStrategy (7 impls), AIStrategyFactory, PhaseManager, CandidateGenerator, MonteCarloSimulator
controller/     — GameController, BluetoothController, BluetoothEventRelay
network/        — BluetoothGateway, NetworkGameBridge, BluetoothConnectionManager, RoomController, ReliabilityManager
event/          — EventBus implements IEventBus, 4 event types
llm/            — LLMAnalyzer implements ILLMAnalyzer, VivoLLMClient implements IVivoLLMClient
ui/             — GameActivity, RoomLobbyActivity, RoomSettingsActivity, SearchDeviceActivity
```

---

## 2. 状态机图精化

### 游戏主状态机

```
[游戏未开始]
     │
     ▼
[开局发牌] ── DealManager.dealCards()
     │
     ▼
[等待出牌] ←── TurnManager.switchPlayer()
     │
     ├── 出牌 → [校验合法] → [执行出牌] → SettlementManager.checkAndSettle()
     │                                      │
     │                                      ├── 未结束 → [等待出牌]
     │                                      └── 结束 → [游戏结束]
     │
     └── 过牌 → [标记为pass] → consecutivePassCount++
              │
              ├── < 3 → [等待出牌]
              └── >= 3 → [清空桌面] → [等待出牌]
```

### AI 决策状态机 (PhaseManager)

```
[开局 EARLY]  handSize >= 13, bigCardsLeft
     │ 策略：保留大牌(2/A), 出小牌
     ▼
[中盘 MID]    8 <= handSize < 13
     │ 策略：抢夺出牌权，压制下家
     ▼
[残局 LATE]   handSize <= 7
     │ 策略：最快速度脱手，炸弹优先
     ▼
[终局]        手牌清空 → 胜利
```

### 蓝牙连接状态机

```
[未连接] → [扫描中] → [连接中] → [已连接]
                │           │
                └── 超时 ──→ [扫描失败]
                            │
                ┌── 重连 ──┘
                │
[已连接] ← [心跳正常]
    │            │
    └── 超时 ──→ [断线检测] → [重连中] → [已连接]
                              │
                              └── 5次失败 → [断开]
```

---

## 3. 领域模型精化

### 原领域模型 (Sprint 2)
- Player, Card, GameState, Play
- 关系：GameState 聚合 Player，Player 聚合 List<Card>

### 精化后 (Sprint 4-5)
- 新增 `HumanStyleProfile`：跨游戏持久化玩家风格
- 新增 `AIPlayerProfile`：AI 玩家配置（强度等级、进攻/防守因子）
- Refined `GameState`：添加 `allPlayedCards` (记牌器历史), `lastPlayByPlayer` (每玩家最后出牌)
- 新增 `CardTracker`：独立记牌器组件（各节点本地计算）

---

## 4. 交互图精化

### 出牌流程 (精化后 — 含事件驱动)

```
Human Player → GameActivity.submitPlay()
  → GameController.submitPlay()
    → GameEngine.playCards()
      → RuleEngine.validatePlay()
        → PlayValidator.validatePlay()
          → PatternRecognizer.recognizePattern()
      → GameState 更新 (手牌移除, lastPlay, etc.)
      → SettlementManager.checkAndSettle()
      → TurnManager.switchPlayer()
        → eventBus.post(TurnChangedEvent)
          → UI refresh + AI trigger + Bluetooth sync
```

精化点：原流程控制器直接调用 UI refresh + AI + Bluetooth，现改为 EventBus 发布事件，三者独立订阅。

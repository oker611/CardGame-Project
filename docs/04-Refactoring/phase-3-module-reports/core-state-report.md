# 游戏引擎模块设计模式应用报告

**负责人**：陈政昊
**模块**：Engine（游戏核心引擎层）

---

## 一、外观模式 (Facade Pattern)

### 1.1 为什么需要外观模式？

`GameEngine` 内部由多个子组件协同工作——`DealManager`（发牌）、`TurnManager`（回合切换）、`SettlementManager`（结算）。旧架构中外部调用方需要分别了解这些组件的接口。

引入外观模式后，`GameEngine` 对外暴露统一入口：
- `playCards()` → 内部调用 RuleEngine → TurnManager → SettlementManager
- `passTurn()` → 内部调用 TurnManager → SettlementManager

外部调用方（`GameController`、`NetworkGameBridge`）只需与 `GameEngine` 打交道，无需知道子组件的存在。

### 1.2 代码证据

```java
// GameEngine 内部组合了三个 Manager
private final IDealManager dealManager;
private final ITurnManager turnManager;
private final ISettlementManager settlementManager;

// playCards 内部依次调用 Rule → Settlement → Turn
public PlayResult playCards(String playerId, List<String> selectedCardIds) {
    // 1. 规则校验
    ValidationResult vr = ruleEngine.validatePlay(...);
    // 2. 更新 GameState
    player.getHandCards().removeIf(...);
    // 3. 结算检查
    settlementManager.checkAndSettle(gameState);
    // 4. 回合切换
    if (!gameState.isGameOver()) turnManager.switchPlayer(gameState);
    // 5. 发布事件
    eventBus.post(new CardPlayedEvent(playerId, cardIds));
}
```

---

## 二、依赖注入

### 2.1 问题

旧架构中 `GameEngine` 通过 `new DealManager()` / `new TurnManager()` / `new SettlementManager()` 直接硬编码创建依赖，导致单元测试无法 mock。

### 2.2 方案

引入三个接口（`IDealManager`、`ITurnManager`、`ISettlementManager`），通过构造器注入。`GameEngine` 不再依赖具体实现类，测试时可注入 mock。

### 2.3 效果

| 维度 | 改造前 | 改造后 |
|------|--------|--------|
| 依赖创建 | 硬编码 `new` | 构造器注入 |
| 可测试性 | 无法 mock | 完全可 mock |
| 扩展性 | 修改 GameEngine 源码 | 新增实现类即可 |

---

## 三、接口隔离

`GameStateAccess` 接口仅暴露 `AIEventListener` 需要的两个方法（`getGameState()` + `passTurn()`），避免 `AIEventListener` 直接依赖整个 `GameEngine` 的完整接口，遵循 ISP。

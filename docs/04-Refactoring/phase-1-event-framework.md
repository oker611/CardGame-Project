# 阶段一：接入事件总线（只增不删，并行开发）

## 目标

让 UI、蓝牙、AI 三个模块**新写**代码连接到事件总线，
验证能收到 `GameEngine` 发布的事件。
**绝对不删、不注释任何原有代码。**

## 前提

- 第0步已完成：`com.example.cardgame.event` 包下有 7 个文件，
  `GameEngine` 和 `TurnManager` 中已插入事件发布调用。
- 项目已编译通过。

## 公共事件一览

| 事件类 | 含义 | 携带数据 |
| :--- | :--- | :--- |
| `CardPlayedEvent` | 某玩家成功出牌 | `playerId`, `playedCardIds` |
| `PlayerPassedEvent` | 某玩家成功过牌 | `playerId` |
| `TurnChangedEvent` | 回合切换到新玩家 | `newCurrentPlayerId`, `reason` |
| `GameOverEvent` | 游戏结束 | `winnerId` |

## 通用模板（所有人必读）

```java
// 1. 在你的模块主类或新建的监听器类中实现接口
import com.example.cardgame.event.GameEventListener;
import com.example.cardgame.event.GameEvent;
import com.example.cardgame.event.EventBus;
// ... 其他需要的事件类

public class XxxActivity / XxxManager implements GameEventListener {

    @Override
    public void onEvent(GameEvent event) {
        if (event instanceof CardPlayedEvent) {
            CardPlayedEvent e = (CardPlayedEvent) event;
            // TODO: 处理出牌事件
        } else if (event instanceof PlayerPassedEvent) {
            PlayerPassedEvent e = (PlayerPassedEvent) event;
            // TODO: 处理过牌事件
        } else if (event instanceof TurnChangedEvent) {
            TurnChangedEvent e = (TurnChangedEvent) event;
            // TODO: 处理回合切换事件
        } else if (event instanceof GameOverEvent) {
            GameOverEvent e = (GameOverEvent) event;
            // TODO: 处理游戏结束事件
        }
    }

    // 在初始化时注册
    // EventBus.getInstance().register(this);

    // 在销毁时取消注册
    // EventBus.getInstance().unregister(this);
}
```

## 分模块任务

### 🟪 UI 同学：`GameActivity` 接入事件

**负责人**：xxx
**涉及文件**：`GameActivity.java`（只增不删）

**步骤**：

1. 让 `GameActivity` 实现 `GameEventListener` 接口。
2. 在 `onCreate` 中注册：
   ```java
   EventBus.getInstance().register(this);
   ```
3. 在 `onDestroy` 中取消注册：
   ```java
   EventBus.getInstance().unregister(this);
   ```
4. 实现 `onEvent` 方法：
    - `CardPlayedEvent` → 调用 `runOnUiThread(() -> refreshUI())`
    - `PlayerPassedEvent` → 调用 `runOnUiThread(() -> refreshUI())`
    - `TurnChangedEvent` → 调用 `runOnUiThread(() -> updateTurnHighlight(event.getNewCurrentPlayerId()))`
      （如暂时没有这个方法，先只打日志）
    - `GameOverEvent` → 调用 `runOnUiThread(() -> handleGameOver(event.getWinnerId()))`
      （如暂时没有这个方法，先只打日志）

**验证**：
运行游戏，出牌/过牌后，在 logcat 中过滤 `CardPlayedEvent` 等关键词，
确认 UI 收到了事件（打一行日志即可）。

**DDL**：周五晚

---

### 🟨 蓝牙同学：新建 `BluetoothEventRelay` 接入事件

**负责人**：xxx
**涉及文件**：新建 `BluetoothEventRelay.java`（只增）

**步骤**：

1. 在 `com.example.cardgame.controller` 包下新建 `BluetoothEventRelay` 类，
   实现 `GameEventListener`。
2. 构造器接收 `BluetoothGateway` 和 `GameEngine` 引用：
   ```java
   private final BluetoothGateway gateway;
   private final GameEngine gameEngine;

   public BluetoothEventRelay(BluetoothGateway gateway, GameEngine gameEngine) {
       this.gateway = gateway;
       this.gameEngine = gameEngine;
   }
   ```
3. 在 `BluetoothController` 初始化时，创建 `BluetoothEventRelay` 实例并注册到 `EventBus`。
4. 实现 `onEvent`：
    - `CardPlayedEvent` → 调用 `gateway.sendPlayAction(...)`
      （需根据 playerId 和 cardIds 构造 Play 对象，或临时在 gateway 新增一个重载方法）
    - `PlayerPassedEvent` → 调用 `gateway.sendPassAction(event.getPlayerId())`
    - `GameOverEvent` → 查询 winnerName 后调用 `gateway.sendGameOver(winnerId, winnerName)`

**验证**：
启动蓝牙对局，打出牌后观察对方设备是否收到同步。
同时在 logcat 中确认事件被 `BluetoothEventRelay` 收到。

**DDL**：周五晚

---

### 🟩 AI 同学：新建 `AIEventListener` 接入事件

**负责人**：xxx
**涉及文件**：新建 `AIEventListener.java`（只增）

**步骤**：

1. 在 `com.example.cardgame.ai` 包下新建 `AIEventListener` 类，
   实现 `GameEventListener`。
2. 构造器接收 `GameEngine` 和 `GameController`（或 AI 逻辑入口）引用。
3. 在 `GameController` 初始化时，创建 `AIEventListener` 实例并注册到 `EventBus`。
4. 实现 `onEvent`：
    - `TurnChangedEvent` → 检查 `newCurrentPlayerId` 对应的玩家类型：
      ```java
      Player player = gameEngine.getGameState().getPlayerById(event.getNewCurrentPlayerId());
      if (player != null && player.getType() == PlayerType.AI) {
          // 触发 AI 行动（使用 Handler 延迟 500ms 模拟思考）
          new Handler(Looper.getMainLooper()).postDelayed(() -> {
              // 调用 AI 决策并执行出牌/过牌
          }, 500);
      }
      ```
    - `GameOverEvent` → 停止 AI 思考（如适用）。

**验证**：
进行人机对局，观察 AI 是否在轮到自己时行动。
同时 logcat 确认 `TurnChangedEvent` 被 AI 监听器收到。

**DDL**：周五晚

---

## 验收标准（全体）

- [ ] 各模块监听器已注册到 `EventBus`
- [ ] 运行一局游戏，logcat 中能看到各模块打印的事件接收日志
- [ ] 原有功能完全正常（旧代码未被删除或注释）
- [ ] 项目编译通过

## 常见问题

**Q：事件里的数据不够用怎么办？**
A：先打日志确认事件能收到。需要补充数据的话，在群里讨论后，
只新增事件字段（不删不改已有字段），然后通知所有人。

**Q：我需要在 `onEvent` 里做复杂操作吗？**
A：阶段一只需要**收到事件 + 打日志**。复杂逻辑放在阶段三各自重构时再做。

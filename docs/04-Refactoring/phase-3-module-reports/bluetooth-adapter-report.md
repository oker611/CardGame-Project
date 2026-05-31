# 蓝牙模块设计模式应用报告

**负责人**：傅钧烨
**模块**：Bluetooth（蓝牙通信层）

---

## 一、观察者模式

### 1.1 为什么需要观察者模式？

在旧架构中，蓝牙同步是这样工作的：

玩家出牌 → GameController → BluetoothController.sendLocalPlay() → BluetoothGateway → 远端
`GameController` 必须主动调用 `BluetoothActionHandler` 的发送方法。

这导致：

- **紧耦合**：控制器需要同时知道 UI 刷新、蓝牙同步、AI 调度三件事
- **遗漏风险**：每新增一个游戏事件（如回合切换），都需要在 `GameController` 中手动添加蓝牙同步调用
- **职责混乱**：`GameController` 本该只负责调度，却要关心"什么时候该发蓝牙消息"

观察者模式解决的核心问题是：**让蓝牙模块自己监听游戏事件，而不是被控制器通知去同步**。

### 1.2 怎么做的？

两层观察者机制：

**第一层：EventBus（游戏事件 → 蓝牙同步）**

- `BluetoothEventRelay` 实现 `GameEventListener`，注册到全局 `EventBus`
- 当 `GameEngine` 发布 `CardPlayedEvent`、`PlayerPassedEvent`、`GameOverEvent` 时，`BluetoothEventRelay` 自动收到通知
- 内部带守卫逻辑：跳过 REMOTE 玩家的事件（防止网络回声），HOST 独占广播 `GameOverEvent`
- 通过 `BluetoothGateway` 将事件转发为蓝牙消息发送到远端

**第二层：BluetoothEventListener（蓝牙状态 → UI 更新）**

- `BluetoothController` 实现 `BluetoothEventListener` 接口
- `BluetoothGateway` 在连接、断开、消息收发、玩家加入/离开、错误发生时回调 `BluetoothController`
- `BluetoothController` 更新 `BluetoothViewData`，UI 通过轮询/观察 `BluetoothViewData` 获取最新状态

### 1.3 具体干了什么事？

| 步骤 | 做了什么 |
|------|----------|
| 1 | 新建 `BluetoothEventRelay` 类，实现 `GameEventListener` 接口 |
| 2 | 在 `BluetoothController` 构造时创建 `BluetoothEventRelay` 并注册到 `EventBus` |
| 3 | 实现 `onEvent()` 方法：`CardPlayedEvent` → `sendPlayAction`，`PlayerPassedEvent` → `sendPassAction`，`GameOverEvent` → `sendGameOver` |
| 4 | `BluetoothEventListener` 接口定义 12 个回调方法，覆盖连接生命周期、玩家管理、消息收发、错误处理 |
| 5 | 删除 `GameController` 中旧的 `bluetoothActionHandler.sendLocalPlay/Pass/GameOver` 调用，完全迁移到事件驱动 |

### 1.4 带来了什么好处？

| 维度 | 之前 | 之后 |
|------|------|------|
| 耦合度 | GameController 主动调用蓝牙发送 | 蓝牙模块订阅事件，自主同步 |
| 扩展性 | 新增事件类型要改 GameController | 在 BluetoothEventRelay 中新增事件处理即可 |
| 防回声 | 无保护，远端消息可能循环触发 | 通过 PlayerType.REMOTE 守卫和 HOST 独占逻辑阻断回声 |
| 代码清晰度 | GameController 混杂蓝牙同步代码 | 蓝牙同步逻辑完全封装在 BluetoothEventRelay 中 |

**总结**：蓝牙从"被控制器通知"变成了"监听游戏事件、自主同步"，与 UI 模块的观察者模式形成对称架构。

---

## 二、外观模式

### 2.1 为什么需要外观模式？

蓝牙通信涉及多个子系统：

- `BluetoothConnectionManager`：管理 RFCOMM Socket、设备发现、多路连接
- `BluetoothSender` / `BluetoothReceiver`：单路消息收发
- `BluetoothMessageCodec`：JSON 编解码
- `NetworkGameBridge`：网络消息到 GameEngine 的桥接
- 心跳机制：定时探测 + 超时断开
- 多路广播：HOST 向所有 CLIENT 广播游戏消息

如果不封装，上层代码需要直接操作这些组件，理解成本极高。外观模式用一个 `BluetoothGateway` 统一对外接口。

### 2.2 怎么做的？

`BluetoothGateway` 作为唯一的外观入口：

```
BluetoothController
       │
       ▼
BluetoothGateway ◄── 外观入口
       │
       ├── BluetoothConnectionManager  （连接管理）
       ├── BluetoothSender × N         （发送器，每路连接一个）
       ├── BluetoothReceiver × N       （接收器，每路连接一个）
       ├── BluetoothMessageCodec       （编解码）
       ├── NetworkGameBridge           （引擎桥接）
       └── Heartbeat                  （心跳机制）
```

对外暴露的简洁接口：

| 方法 | 用途 |
|------|------|
| `startAsHost(hostId, hostName)` | HOST 创建房间，自动 accept 3 个 CLIENT |
| `connectAsClient(clientId, address, name)` | CLIENT 连接 HOST |
| `sendPlayAction(play)` | 发送出牌消息（广播到所有远端） |
| `sendPassAction(playerId)` | 发送过牌消息 |
| `syncGameState(state)` | 同步开局状态 |
| `sendGameOver(winnerId, winnerName)` | 发送游戏结束通知 |
| `disconnect()` | 断开所有连接并清理资源 |

### 2.3 为什么这么设计？

- **单一入口**：`BluetoothController` 只需与 `BluetoothGateway` 交互，不感知内部 6 个子系统
- **隐藏复杂度**：多路连接管理（3 路 CLIENT 并发 accept）、JSON 编解码、心跳保活对上层完全透明
- **职责分离**：`BluetoothGateway` 只管"怎么发消息"，`BluetoothEventRelay` 只管"什么时候该发消息"
- **易测试**：可以 mock `BluetoothGateway` 来测试 `BluetoothController` 或 `BluetoothEventRelay`

---

## 三、桥接模式

### 3.1 为什么需要桥接模式？

蓝牙消息的接收端需要将网络消息"翻译"为对 `GameEngine` 的方法调用，但这两者属于完全不同的抽象层次：

- 网络层：`BluetoothMessage`（JSON 字符串 + 消息类型枚举）
- 引擎层：`GameEngine.playCards()`、`GameEngine.passTurn()`、`GameEngine.rebuildGameState()`

桥接模式将**消息格式**与**引擎调用**解耦，使两者可以独立变化。

### 3.2 怎么做的？

`NetworkGameBridge` 作为桥接层：

```
BluetoothMessage ──► NetworkGameBridge ──► GameEngine
   (网络格式)           (桥接层)            (引擎调用)
```

核心流程：

1. `BluetoothGateway.onMessageReceived()` 收到消息
2. 非 JOIN/HEARTBEAT 类消息交由 `NetworkGameBridge.handleMessage()`
3. `NetworkGameBridge` 根据 `MessageType` 解码 payload，通过反射调用 `GameEngine` 方法：
   - `INIT_GAME` → `gameEngine.rebuildGameState()` 或 `rebuildGameStateMulti()`
   - `PLAY_ACTION` → `gameEngine.executeRemotePlay()`
   - `PASS_ACTION` → `gameEngine.executeRemotePass()`
   - `GAME_OVER` → 通过 `BluetoothEventListener` 回调通知上层

### 3.3 为什么用反射而非直接调用？

| 考量 | 结论 |
|------|------|
| 编译期依赖 | 反射避免了 `network` 包对 `engine` 包具体方法的编译期强依赖 |
| 兼容性 | `GameEngine` 方法签名变化时，bridge 只需调整字符串常量，不会编译报错 |
| 版本协商 | 未来可在 `BluetoothMessage` 中增加协议版本号，bridge 按版本选择不同方法 |

**权衡**：反射牺牲了编译期类型安全，换取了模块间的松耦合。当前 `GameEngine` 接口稳定，风险可控。

---

## 四、策略模式（连接降级策略）

### 4.1 为什么需要策略模式？

Android 蓝牙 RFCOMM 连接在不同 ROM 上行为不一致：
- 原生 Android：`createRfcommSocketToServiceRecord` 正常工作
- 部分国产 ROM（小米、华为等）：安全 RFCOMM 连接失败，需降级到 `createInsecureRfcommSocketToServiceRecord`

策略模式将两种连接方式封装为可替换的策略。

### 4.2 怎么做的？

`BluetoothConnectionManager.connectWithFallback()`：

```
连接设备
    │
    ├── 策略1: 安全 RFCOMM (createRfcommSocketToServiceRecord)
    │         │
    │         └── 失败 ──► 策略2: 不安全 RFCOMM (createInsecureRfcommSocketToServiceRecord)
    │                            │
    │                            └── 失败 ──► 抛出异常
    │
    └── 成功 ──► 返回 Socket
```

带超时控制的 `connectSocketWithTimeout()` 进一步增强了策略的健壮性：
- 独立线程执行 `socket.connect()`
- `join(timeout)` 等待，超时则关闭 socket 并抛出异常
- 避免部分 ROM 上 `connect()` 永久阻塞的问题

### 4.3 带来了什么好处？

| 维度 | 之前 | 之后 |
|------|------|------|
| 兼容性 | 单一连接方式，部分设备连不上 | 自动降级，覆盖主流 ROM |
| 可扩展性 | 无法添加新策略 | 新增策略只需在 fallback 链中添加 |
| 超时保护 | 无超时，可能永久阻塞 | 8 秒超时 + 独立线程，不会卡死主流程 |

---

## 五、命令模式（未采用）

### 5.1 为什么不采用？

在 `NetworkGameBridge.handleMessage()` 中，消息分发使用了 `switch-case` 而非命令模式：

```java
switch (message.getMessageType()) {
    case INIT_GAME:    handleInitGame(message);    break;
    case PLAY_ACTION:  handlePlayAction(message);  break;
    case PASS_ACTION:  handlePassAction(message);  break;
    case GAME_OVER:    handleGameOver(message);    break;
    // ...
}
```

| 考量 | 结论 |
|------|------|
| 消息类型数量 | 当前仅 4 种游戏消息类型，switch-case 足够清晰 |
| 引入成本 | 命令模式需要为每种消息新建 Command 类（至少 4 个），增加大量样板代码 |
| 收益 | 每种 handler 已经是独立方法，switch-case 仅做路由，额外抽象层不带来实质简化 |
| 开闭原则 | 新增消息类型只需加一个 case + 一个 handler 方法，改动量相近 |

**总结**：当前消息类型数量少且稳定，命令模式的收益不足以抵消其引入的复杂度。如未来消息类型超过 10 种，可重新评估。

---

## 六、总结

| 设计模式 | 是否采用 | 核心原因 |
|----------|----------|----------|
| 观察者模式 | ✅ 是 | 两层观察者：EventBus 驱动蓝牙同步，BluetoothEventListener 回调驱动 UI 状态更新 |
| 外观模式 | ✅ 是 | BluetoothGateway 统一封装 6 个子系统，隐藏多路连接、编解码、心跳等复杂性 |
| 桥接模式 | ✅ 是 | NetworkGameBridge 解耦网络消息格式与 GameEngine 方法调用 |
| 策略模式 | ✅ 是 | 连接降级策略（安全→不安全 RFCOMM）提升国产 ROM 兼容性 |
| 命令模式 | ❌ 否 | 消息类型仅 4 种，switch-case 足够简洁，引入命令模式成本大于收益 |

本次重构的核心是**将蓝牙同步从"控制器推送"变为"事件驱动"**，通过观察者模式（`BluetoothEventRelay` + `EventBus`）解耦了蓝牙模块与控制器。同时，外观模式（`BluetoothGateway`）和桥接模式（`NetworkGameBridge`）配合使用，实现了"高内聚、低耦合"的蓝牙通信分层架构。

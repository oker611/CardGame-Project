# 阶段二：删除旧通信路径（逐模块切除，逐次验证）

## 目标

在确认阶段一“事件总线”正常工作后，**逐模块删除**原有的直接调用代码。
每切除一个模块，立即验证，确保游戏功能不受影响。

## 前提

- 阶段一已完成，所有模块的事件监听器已接入、能收到事件并打印日志。
- 项目编译通过，原有功能正常。
- 所有旧代码当前仍保留在代码库中（未被注释或删除）。

## 切除顺序（重要！必须按此顺序逐一进行）

| 顺序 | 负责模块 | 删除内容 | 原因 |
| :--- | :--- | :--- | :--- |
| 1 | UI | `GameController` 中 `notifyUiRefresh()` 调用及相关回调 | UI 刷新是高频操作，先切最容易验证，且不影响其他模块 |
| 2 | 蓝牙 | `GameController` 中 `bluetoothActionHandler.sendLocalPlay/Pass/GameOver` 调用 | 蓝牙同步依赖网络，但独立性强，出问题容易定位 |
| 3 | AI | `GameController` 中 `postDelayed(this::triggerNextAction, ...)` 旧 AI 调度 | AI 调度逻辑复杂，最后切，确保前两步没问题再动 |

**每完成一个人的切除，必须在群里 @组长，由组长（或委托人）运行游戏验证后再进行下一个人的切除。**

---

## 详细删除清单

### 🟪 顺序1：UI 模块（负责人：xxx）

**要删除的旧代码位置**：

1. **`GameController.java`**
   - 删除 `notifyUiRefresh()` 方法及其所有调用。
     - 搜索 `notifyUiRefresh()`，删除该方法的定义，并删除所有 `notifyUiRefresh();` 调用行。
   - 删除 `setUiRefreshCallback()` 方法及相关成员变量 `uiRefreshCallback`。
   - 删除 `private Runnable uiRefreshCallback;` 字段定义。

2. **`GameActivity.java`**
   - 删除 `bluetoothRefreshRunnable` 中对 `refreshUI()` 的直接调用。
     - 搜索 `bluetoothRefreshRunnable`，将内部的 `refreshUI();` 调用行删除（保留轮询结构如果空，可整个移除 Runnable，但保留无害）。
   - 可保留 `setUiRefreshCallback` 的调用设置，但既然控制器已无此方法，相关代码会编译报错，需一并删除。

**验证方法**：
- 编译通过。
- 启动一局本地游戏，手动操作出牌、过牌，UI 正常刷新，无闪烁或停滞。
- Logcat 中确认 UI 刷新是由 `CardPlayedEvent` 等事件触发（看日志输出）。

**验收后**：组长在群里回复“UI 旧代码切除验证通过，蓝牙同学可以开始”。

---

### 🟨 顺序2：蓝牙模块（负责人：xxx）

**要删除的旧代码位置**：

1. **`GameController.java`**
   - 删除 `submitPlay()` 方法内对 `bluetoothActionHandler.sendLocalPlay(lastPlay);` 的调用。
   - 删除 `passTurn()` 方法内对 `bluetoothActionHandler.sendLocalPass(currentPlayer.getPlayerId());` 的调用。
   - 删除 `sendGameOverIfNeeded()` 方法内对 `bluetoothActionHandler.sendGameOver(...)` 的调用，乃至整个 `sendGameOverIfNeeded()` 方法（如果只有蓝牙内容）。
   - 如果 `bluetoothActionHandler` 字段不再使用，可以删除。

2. **`BluetoothController.java`**
   - 如果 `sendLocalPlay`、`sendLocalPass`、`sendGameOver` 方法不再被其他地方调用（应只有 `GameController` 调用），可以删除这些方法。
   - 注意：`BluetoothEventRelay` 中可能调用了 `BluetoothGateway` 的方法，而不是 `BluetoothController`，确保别误删。

**验证方法**：
- 编译通过。
- 启动蓝牙对局，打出牌后对方设备能正常收到，游戏结束消息正常。
- Logcat 中确认同步是由 `BluetoothEventRelay` 收到事件后发送的（而非旧路径）。

**验收后**：组长在群里回复“蓝牙旧代码切除验证通过，AI 同学可以开始”。

---

### 🟩 顺序3：AI 模块（负责人：xxx）

**要删除的旧代码位置**：

1. **`GameController.java`**
   - 删除与 AI 调度相关的 `postDelayed` 调用链。
     - 搜索 `triggerNextAction`，删除相关方法及所有调用 `new Handler(Looper.getMainLooper()).postDelayed(this::triggerNextAction, ...)` 的行。
   - 注意：可能还有其他地方依赖 `triggerNextAction`（如检查是否轮到 AI 等），确认只保留事件驱动的 AI 调用路径。
   - 如果 `aiPlayerCache` 等只服务于旧调度，可一并清理（保留 AI 实例本身，因为事件监听器可能仍需使用）。

2. **`AIEventListener.java`**（阶段一新建的）
   - 检查 `AIEventListener` 中是否已完整替代了旧调度的所有功能，确保没有遗漏（如开局后首次触发）。

**验证方法**：
- 编译通过。
- 进行人机对局，AI 正常出牌、过牌，没有停止思考或反复出牌。
- 游戏整体流程无异常。

**验收后**：组长宣布“阶段二全部完成，进入阶段三”。

---

## 注意事项

- **只用删除，不用注释**。直接删除旧代码行，因为一旦确认切除成功，保留注释代码无意义，反而制造混乱。
- **每步只能一个人操作**，避免多人同时修改同一个文件（尤其是 `GameController.java`），导致合并冲突。
- 若删除后出现编译错误或运行异常，**立即回退**（Git 撤销），在群里说明问题，共同分析后再试。

## 验收总标准

- [ ] 项目编译无错误
- [ ] 本地游戏、蓝牙对战、人机对战三种模式均运行正常
- [ ] 代码库中不再包含被删除的旧通信路径代码
- [ ] 所有模块行为仍然符合预期（无功能退化）
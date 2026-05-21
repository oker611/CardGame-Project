# 项目代码现状分析：面向观察者模式引入的前置评估

分析范围：`app/src/main/java/com/example/cardgame` 下的生产代码。未修改任何代码。

## 1. 模块边界识别

| 顶层模块 | 包/目录 | 主要职责 | 关键类（不超过 3 个） |
|---|---|---|---|
| 应用启动 / 全局入口 | `com.example.cardgame` | Android `Application` 入口；初始化并静态持有游戏引擎、游戏控制器、蓝牙控制器入口 | `CardGameApplication` |
| UI | `com.example.cardgame.ui` | Activity 页面、RecyclerView Adapter、按钮交互、界面刷新、游戏结束弹窗、蓝牙房间页面 | `GameActivity`, `RoomLobbyActivity`, `SearchDeviceActivity` |
| 控制器 | `com.example.cardgame.controller` | 连接 UI、游戏引擎、AI 与蓝牙；处理出牌、过牌、开局、AI 自动行动、UI 回调 | `GameController`, `BluetoothController`, `GameActionHandler` |
| 游戏核心引擎 | `com.example.cardgame.engine` | 初始化游戏、发牌、出牌/过牌状态推进、回合切换、胜负结算 | `GameEngine`, `DealManager`, `TurnManager` |
| 规则引擎 | `com.example.cardgame.rule` | 出牌合法性校验、牌型识别、规则配置 | `RuleEngine`, `PlayValidator`, `PatternRecognizer` |
| AI | `com.example.cardgame.ai` | 根据当前牌局选择 AI 出牌或 Pass | `AIPlayer` |
| 领域模型 | `com.example.cardgame.model` | 表示游戏状态、玩家、牌、出牌记录、玩家类型等领域数据 | `GameState`, `Player`, `Card` |
| DTO / 视图数据 | `com.example.cardgame.dto` | 控制层与 UI 间传输游戏视图数据、蓝牙视图数据、操作结果 | `GameViewData`, `BluetoothViewData`, `PlayResult` |
| 蓝牙通信 / 网络桥接 | `com.example.cardgame.network`, `com.example.cardgame.network.payload` | 蓝牙连接、设备发现、消息编码/解码、消息收发、远程指令桥接到游戏引擎 | `BluetoothGateway`, `NetworkGameBridge`, `BluetoothConnectionManager` |
| 工具 | `com.example.cardgame.util` | 蓝牙权限检查、日志输出、诊断日志写文件 | `BluetoothPermissionHelper`, `HermesLog`, `Logger` |

---

## 2. 跨模块依赖关系

### 2.1 依赖箭头

基于 `import` 与直接引用，模块依赖关系如下：

| 依赖关系 | 依据 |
|---|---|
| 应用启动 → 控制器 | `CardGameApplication` import 并创建 `GameController`, `BluetoothController` |
| 应用启动 → 游戏核心引擎 | `CardGameApplication` import 并创建 `GameEngine` |
| UI → 应用启动 | `GameActivity`, `RoomLobbyActivity`, `SearchDeviceActivity`, `RoomSettingsActivity` 调用 `CardGameApplication.get...()` |
| UI → 控制器 | UI 持有 `GameActionHandler`, `BluetoothActionHandler` |
| UI → DTO / 视图数据 | UI 读取 `GameViewData`, `BluetoothViewData`, `PlayerViewData` |
| UI → 领域模型 | `DeviceAdapter` / `SearchDeviceActivity` 使用 `DeviceInfo` |
| UI → 工具 | 蓝牙页面使用 `BluetoothPermissionHelper` |
| 控制器 → 游戏核心引擎 | `GameController` 持有 `GameEngine`；`BluetoothController` 构造时接收 `GameEngine` |
| 控制器 → AI | `GameController` 创建并调用 `AIPlayer` |
| 控制器 → DTO / 视图数据 | `GameController` 返回 `GameViewData`, `PlayResult`, `PassResult`；`BluetoothController` 维护 `BluetoothViewData` |
| 控制器 → 领域模型 | `GameController` / `BluetoothController` 使用 `GameState`, `Player`, `Play`, `Card`, `PlayerType` |
| 控制器 → 规则引擎 | `GameController` 直接持有 `PlayValidator` |
| 控制器 → 蓝牙通信 / 网络桥接 | `BluetoothController` 直接持有 `BluetoothGateway` |
| 控制器 → 工具 | `GameController` 调用 `HermesLog` |
| 游戏核心引擎 → DTO / 结果对象 | `GameEngine` 返回 `PlayResult`, `PassResult` |
| 游戏核心引擎 → 领域模型 | `GameEngine`, `DealManager`, `TurnManager`, `SettlementManager` 使用 `GameState`, `Player`, `Card`, `Play` |
| 游戏核心引擎 → 规则引擎 | `GameEngine` 持有 `RuleEngine`，并引用 `PlayValidator.ValidationResult`, `PatternRecognizer.PatternInfo` |
| 游戏核心引擎 → 工具 | `GameEngine` 调用 `Logger.win()`；多个引擎类使用 `System.out.println` |
| 规则引擎 → 领域模型 | `RuleEngine`, `PlayValidator`, `PatternRecognizer` 使用 `Card`, `Player`, `Rank`, `Suit` |
| AI → 领域模型 | `AIPlayer` 使用 `Card`, `Rank`, `Suit` |
| AI → 规则引擎 | `AIPlayer` 持有 `PatternRecognizer`, `PlayValidator` |
| DTO / 视图数据 → 领域模型 | `PlayResult`, `PassResult`, `ValidationResult` 引用 `GameState` / `CardPattern` |
| 蓝牙通信 / 网络桥接 → 游戏核心引擎 | `BluetoothGateway` / `NetworkGameBridge` 接收并调用 `GameEngine` |
| 蓝牙通信 / 网络桥接 → 领域模型 | 网络层 payload 与 bridge 使用 `GameState`, `Play`, `Card`, `PlayerType` |
| 蓝牙通信 / 网络桥接 → 工具 | `BluetoothGateway`, `BluetoothReceiver`, `BluetoothSender`, `NetworkGameBridge` 调用 `HermesLog` 或 `Log` |
| 蓝牙通信 / 网络桥接 → 网络消息载荷 | `BluetoothMessageCodec`, `BluetoothGateway`, `NetworkGameBridge` 使用 `InitGamePayload`, `PlayActionPayload`, `PassActionPayload`, `GameOverPayload` 等 |
| 工具 → Android SDK | `BluetoothPermissionHelper`, `HermesLog` 使用 Android 蓝牙、权限、文件路径、日志 API |

### 2.2 循环依赖

按包级 `import` 与直接类型引用观察：

- 未发现明确的跨顶层模块循环依赖，例如 `A → B` 且 `B → A` 的源码级双向 `import`。
- 存在回调式运行时通信：`BluetoothController implements BluetoothEventListener`，`BluetoothGateway` 持有 `BluetoothEventListener` 并回调，但 `network` 包未直接 import `controller` 包。

相关位置：

```73:76:app/src/main/java/com/example/cardgame/network/BluetoothGateway.java
public void setBluetoothEventListener(BluetoothEventListener eventListener) {
    this.eventListener = eventListener;
    this.networkGameBridge.setBluetoothEventListener(eventListener);
}
```

```20:20:app/src/main/java/com/example/cardgame/controller/BluetoothController.java
public class BluetoothController implements BluetoothActionHandler, BluetoothEventListener {
```

---

## 3. 关键调用链分析（核心流程）

### 3.1 出牌流程

#### 3.1.1 本地 UI 出牌流程

调用链：

`GameActivity.btn_play.onClick()`（UI / `GameActivity`）  
→ `GameActionHandler.submitPlay()`（控制器接口）  
→ `GameController.submitPlay()`（控制器 / `GameController`）  
→ `GameEngine.playCards()`（游戏核心引擎 / `GameEngine`）  
→ `RuleEngine.validatePlay()`（规则引擎 / `RuleEngine`）  
→ `PlayValidator.validatePlay()`（规则引擎 / `PlayValidator`）  
→ `PatternRecognizer.recognizePattern()`（规则引擎 / `PatternRecognizer`）  
→ `GameEngine` 修改 `Player` 与 `GameState`（领域模型）  
→ `SettlementManager.checkAndSettle()`（游戏核心引擎 / `SettlementManager`）  
→ `TurnManager.switchPlayer()`（游戏核心引擎 / `TurnManager`，未结束时）  
→ `BluetoothActionHandler.sendLocalPlay()`（控制器接口，蓝牙模式下）  
→ `BluetoothController.sendLocalPlay()`（控制器 / `BluetoothController`）  
→ `BluetoothGateway.sendPlayAction()`（蓝牙通信 / `BluetoothGateway`）  
→ `BluetoothMessageCodec.buildPlayActionMessage()`（蓝牙通信 / `BluetoothMessageCodec`）  
→ `BluetoothGateway.sendBluetoothMessage()`（蓝牙通信 / `BluetoothGateway`）  
→ `BluetoothSender.sendMessage()` / `sendRaw()`（蓝牙通信 / `BluetoothSender`）  
→ `GameController.notifyUiRefresh()`（控制器 / `GameController`）  
→ `GameActivity.refreshUI()`（UI / `GameActivity`）

入口：

```179:185:app/src/main/java/com/example/cardgame/ui/GameActivity.java
findViewById(R.id.btn_play).setOnClickListener(v -> {
    if (gameActionHandler != null) {
        PlayResult result = gameActionHandler.submitPlay(new ArrayList<>(selectedCardIds));
        if (result != null) {
            Toast.makeText(this, result.getMessage(), Toast.LENGTH_SHORT).show();
            if (result.isSuccess()) {
                refreshUI();
```

控制层提交出牌：

```165:203:app/src/main/java/com/example/cardgame/controller/GameController.java
public PlayResult submitPlay(List<String> selectedCardIds) {
    GameState state = gameEngine.getGameState();
    if (state == null || state.getCurrentPlayer() == null) {
        return new PlayResult(false, "Game state not ready.", state);
    }
    Player currentPlayer = state.getCurrentPlayer();
    if (!myPlayerId.equals(currentPlayer.getPlayerId()) || currentPlayer.getType() != PlayerType.HUMAN) {
        return new PlayResult(false, "不是您的回合", state);
    }
    ...
    PlayResult result = gameEngine.playCards(currentPlayer.getPlayerId(), cardsToPlay);
    if (result.isSuccess()) {
        ...
        if (bluetoothMode && bluetoothActionHandler != null && gameEngine.getGameState() != null) {
            Play lastPlay = gameEngine.getGameState().getLastPlay();
            if (lastPlay != null) bluetoothActionHandler.sendLocalPlay(lastPlay);
            sendGameOverIfNeeded();
        }
        notifyUiRefresh();
```

核心验证与状态更新：

```53:109:app/src/main/java/com/example/cardgame/engine/GameEngine.java
public PlayResult playCards(String playerId, List<String> selectedCardIds) {
    System.out.println("[CardGame][PLAY] request playerId=" + playerId + ", selectedCardIds=" + selectedCardIds);

    Player player = gameState.getPlayerById(playerId);
    ...
    PlayValidator.ValidationResult validationResult =
            ruleEngine.validatePlay(selectedCards, lastPlayCards, isFirstRound, isFirstTurn);
    ...
    PatternRecognizer.PatternInfo patternInfo = ruleEngine.recognizePattern(selectedCards);
    CardPattern finalPattern = mapPatternType(patternInfo.getType());

    Play currentPlay = new Play(playerId, selectedCards, finalPattern);

    player.getHandCards().removeIf(card -> selectedCardIds.contains(card.getCardId()));

    gameState.setLastPlay(currentPlay);
    player.setPassed(false);
    gameState.setLastWinnerId(playerId);
    gameState.resetConsecutivePassCount();
    if (gameState.isOpeningTurn()) gameState.setOpeningTurn(false);
    gameState.updateLastPlayByPlayer(playerId, selectedCards);
    ...
    settlementManager.checkAndSettle(gameState);
    if (!gameState.isGameOver()) {
        turnManager.switchPlayer(gameState);
    } else {
        Logger.win("游戏结束，获胜者: " + gameState.getWinnerId());
    }
```

蓝牙同步：

```143:145:app/src/main/java/com/example/cardgame/controller/BluetoothController.java
public void sendLocalPlay(Play play) {
    bluetoothGateway.sendPlayAction(play);
}
```

```255:271:app/src/main/java/com/example/cardgame/network/BluetoothGateway.java
public void sendPlayAction(Play play) {
    if (play == null) {
        Log.w("CardGame", "[WARN] [蓝牙] [发送] 出牌消息为空 | play:null");
        return;
    }

    PlayActionPayload payload = new PlayActionPayload(
            play.getPlayerId(),
            new ArrayList<>(),
            play
    );

    BluetoothMessage message = messageCodec.buildPlayActionMessage(
            localPlayerId, "ALL", payload);

    sendBluetoothMessage(message, "本地玩家出牌:" + play.getPlayerId());
}
```

#### 3.1.2 远程蓝牙出牌流程

调用链：

`BluetoothReceiver.receiveLoop()`（蓝牙通信 / `BluetoothReceiver`）  
→ `BluetoothReceiver.handleRawMessage()`（蓝牙通信 / `BluetoothReceiver`）  
→ `BluetoothGateway.onMessageReceived()`（蓝牙通信 / `BluetoothGateway`）  
→ `NetworkGameBridge.handleMessage()`（蓝牙通信 / `NetworkGameBridge`）  
→ `NetworkGameBridge.handlePlayAction()`（蓝牙通信 / `NetworkGameBridge`）  
→ `GameEngine.executeRemotePlay()`（游戏核心引擎 / `GameEngine`，通过反射调用）  
→ `GameEngine.playCards()`（游戏核心引擎 / `GameEngine`）  
→ 后续验证、状态更新、结算、切换回合与本地出牌流程相同  
→ `GameActivity.bluetoothRefreshRunnable` 周期性调用 `refreshUI()`（UI / `GameActivity`）

接收消息：

```124:134:app/src/main/java/com/example/cardgame/network/BluetoothReceiver.java
private void handleRawMessage(String rawJson) {
    try {
        BluetoothMessage message = messageCodec.decode(rawJson);

        if (message == null || message.getMessageType() == null) {
            throw new IOException("Invalid bluetooth message: " + rawJson);
        }

        if (messageListener != null) {
            messageListener.onMessageReceived(message);
```

网关转发到 bridge：

```445:492:app/src/main/java/com/example/cardgame/network/BluetoothGateway.java
public void onMessageReceived(BluetoothMessage message) {
    ...
    switch (message.getMessageType()) {
        ...
        default:
            if (isHost() && communicationReady) {
                forwardToOtherClients(message);
            }

            networkGameBridge.handleMessage(message);
            break;
    }
}
```

远程出牌处理：

```148:170:app/src/main/java/com/example/cardgame/network/NetworkGameBridge.java
private void handlePlayAction(BluetoothMessage message) {
    try {
        PlayActionPayload payload =
                messageCodec.decodePlayActionPayload(message.getPayloadJson());

        Play play = payload.getPlay();

        if (play != null) {
            invokeEngineMethod(
                    "executeRemotePlay",
                    new Class[]{Play.class},
                    play
            );
        } else {
            invokeEngineMethod(
                    "playCards",
                    new Class[]{String.class, List.class},
                    payload.getPlayerId(),
                    payload.getSelectedCardIds()
            );
        }

        notifyReceived(MessageType.PLAY_ACTION, "收到远程出牌:" + payload.getPlayerId());
```

远程出牌落到核心引擎：

```284:307:app/src/main/java/com/example/cardgame/engine/GameEngine.java
public PlayResult executeRemotePlay(Play play) {
    if (play == null) {
        System.out.println("[CardGame][BLUETOOTH] executeRemotePlay failed: play is null");
        return createPlayResult(false, "Remote play is null", gameState);
    }
    ...
    return playCards(play.getPlayerId(), cardIds);
}
```

远程流程的 UI 刷新入口：

```75:87:app/src/main/java/com/example/cardgame/ui/GameActivity.java
private final Runnable bluetoothRefreshRunnable = new Runnable() {
    @Override
    public void run() {
        if (gameActionHandler != null && isBluetoothGame) {
            refreshUI();

            if (isHost) {
                gameActionHandler.triggerNextAction();
            }
        }

        bluetoothRefreshHandler.postDelayed(this, 1000);
    }
};
```

### 3.2 过牌流程

#### 3.2.1 本地 UI 过牌流程

调用链：

`GameActivity.btn_pass.onClick()`（UI / `GameActivity`）  
→ `GameActionHandler.passTurn()`（控制器接口）  
→ `GameController.passTurn()`（控制器 / `GameController`）  
→ `GameEngine.passTurn()`（游戏核心引擎 / `GameEngine`）  
→ 修改 `Player.passed` 与 `GameState.lastPlayByPlayer / consecutivePassCount / currentPlayerId`（领域模型）  
→ `TurnManager.switchPlayer()`（游戏核心引擎 / `TurnManager`，普通 Pass）  
→ `BluetoothActionHandler.sendLocalPass()`（控制器接口，蓝牙模式下）  
→ `BluetoothController.sendLocalPass()`（控制器 / `BluetoothController`）  
→ `BluetoothGateway.sendPassAction()`（蓝牙通信 / `BluetoothGateway`）  
→ `BluetoothMessageCodec.buildPassActionMessage()`（蓝牙通信 / `BluetoothMessageCodec`）  
→ `BluetoothGateway.sendBluetoothMessage()`（蓝牙通信 / `BluetoothGateway`）  
→ `BluetoothSender.sendMessage()` / `sendRaw()`（蓝牙通信 / `BluetoothSender`）  
→ `GameController.notifyUiRefresh()`（控制器 / `GameController`）  
→ `GameActivity.refreshUI()`（UI / `GameActivity`）

入口：

```193:198:app/src/main/java/com/example/cardgame/ui/GameActivity.java
findViewById(R.id.btn_pass).setOnClickListener(v -> {
    if (gameActionHandler != null) {
        PassResult result = gameActionHandler.passTurn();
        if (result != null) {
            Toast.makeText(this, result.getMessage(), Toast.LENGTH_SHORT).show();
            refreshUI();
```

控制层：

```212:230:app/src/main/java/com/example/cardgame/controller/GameController.java
public PassResult passTurn() {
    GameState state = gameEngine.getGameState();
    ...
    cancelCountdown(currentPlayer);
    PassResult result = gameEngine.passTurn(currentPlayer.getPlayerId());
    if (result.isSuccess()) {
        if (bluetoothMode && bluetoothActionHandler != null) {
            bluetoothActionHandler.sendLocalPass(currentPlayer.getPlayerId());
        }
        notifyUiRefresh();
        if (!gameEngine.isGameOver()) {
            new Handler(Looper.getMainLooper()).postDelayed(this::triggerNextAction, 100);
```

核心引擎：

```112:165:app/src/main/java/com/example/cardgame/engine/GameEngine.java
public PassResult passTurn(String playerId) {
    System.out.println("[CardGame][PASS] request playerId=" + playerId);

    if (gameState == null || gameState.isGameOver()) {
        ...
    }

    Player player = gameState.getPlayerById(playerId);
    ...
    player.setPassed(true);
    gameState.updateLastPlayByPlayer(playerId, null);
    gameState.incrementConsecutivePassCount();

    if (gameState.getConsecutivePassCount() >= 3) {
        String winnerId = gameState.getLastWinnerId();

        gameState.setLastPlay(null);
        gameState.clearAllPassStatus();
        gameState.clearAllLastPlayRecords();
        gameState.resetConsecutivePassCount();

        if (winnerId != null && !gameState.isOpeningTurn()) {
            gameState.setCurrentPlayerId(winnerId);
        } else {
            turnManager.switchPlayer(gameState);
        }
    } else {
        turnManager.switchPlayer(gameState);
    }

    System.out.println("[CardGame][PASS] success playerId=" + playerId);
    return createPassResult(true, "PASS_OK", gameState);
}
```

蓝牙同步：

```148:150:app/src/main/java/com/example/cardgame/controller/BluetoothController.java
public void sendLocalPass(String playerId) {
    bluetoothGateway.sendPassAction(playerId);
}
```

```274:286:app/src/main/java/com/example/cardgame/network/BluetoothGateway.java
public void sendPassAction(String playerId) {
    if (playerId == null || playerId.trim().isEmpty()) {
        Log.w("CardGame", "[WARN] [蓝牙] [发送] Pass玩家为空 | playerId:null");
        return;
    }

    PassActionPayload payload = new PassActionPayload(playerId);

    BluetoothMessage message = messageCodec.buildPassActionMessage(
            localPlayerId, "ALL", payload);

    sendBluetoothMessage(message, "本地玩家Pass:" + playerId);
}
```

#### 3.2.2 远程蓝牙过牌流程

调用链：

`BluetoothReceiver.handleRawMessage()`（蓝牙通信 / `BluetoothReceiver`）  
→ `BluetoothGateway.onMessageReceived()`（蓝牙通信 / `BluetoothGateway`）  
→ `NetworkGameBridge.handleMessage()`（蓝牙通信 / `NetworkGameBridge`）  
→ `NetworkGameBridge.handlePassAction()`（蓝牙通信 / `NetworkGameBridge`）  
→ `GameEngine.executeRemotePass()`（游戏核心引擎 / `GameEngine`，通过反射调用）  
→ `GameEngine.passTurn()`（游戏核心引擎 / `GameEngine`）  
→ 后续状态更新与本地过牌流程相同  
→ `GameActivity.bluetoothRefreshRunnable` 周期性调用 `refreshUI()`（UI / `GameActivity`）

远程 Pass 处理：

```176:195:app/src/main/java/com/example/cardgame/network/NetworkGameBridge.java
private void handlePassAction(BluetoothMessage message) {
    try {
        PassActionPayload payload =
                messageCodec.decodePassActionPayload(message.getPayloadJson());

        boolean executed = invokeEngineMethod(
                "executeRemotePass",
                new Class[]{String.class},
                payload.getPlayerId()
        );

        if (!executed) {
            invokeEngineMethod(
                    "passTurn",
                    new Class[]{String.class},
                    payload.getPlayerId()
            );
        }

        notifyReceived(MessageType.PASS_ACTION, "收到远程Pass:" + payload.getPlayerId());
```

远程 Pass 落到核心引擎：

```313:315:app/src/main/java/com/example/cardgame/engine/GameEngine.java
public PassResult executeRemotePass(String playerId) {
    System.out.println("[CardGame][BLUETOOTH] executeRemotePass playerId=" + playerId);
    return passTurn(playerId);
}
```

### 3.3 游戏结束流程

#### 3.3.1 本地出牌导致游戏结束

调用链：

`GameEngine.playCards()`（游戏核心引擎 / `GameEngine`）  
→ `SettlementManager.checkAndSettle()`（游戏核心引擎 / `SettlementManager`）  
→ `GameState.setGameOver(true)` / `GameState.setWinnerId()`（领域模型 / `GameState`）  
→ `Logger.win()`（工具 / `Logger`）  
→ `GameController.submitPlay()`（控制器 / `GameController`）  
→ `GameController.sendGameOverIfNeeded()`（控制器 / `GameController`）  
→ `BluetoothActionHandler.sendGameOver()`（控制器接口，蓝牙模式下）  
→ `BluetoothController.sendGameOver()`（控制器 / `BluetoothController`）  
→ `BluetoothGateway.sendGameOver()`（蓝牙通信 / `BluetoothGateway`）  
→ `BluetoothMessageCodec.buildGameOverMessage()`（蓝牙通信 / `BluetoothMessageCodec`）  
→ `BluetoothGateway.sendBluetoothMessage()`（蓝牙通信 / `BluetoothGateway`）  
→ `GameController.notifyUiRefresh()`（控制器 / `GameController`）  
→ `GameActivity.refreshUI()`（UI / `GameActivity`）  
→ `GameActivity.showGameOverDialog()`（UI / `GameActivity`）

结算：

```43:55:app/src/main/java/com/example/cardgame/engine/SettlementManager.java
public void checkAndSettle(GameState gameState) {
    if (gameState.isGameOver()) {
        return;
    }

    for (Player player : gameState.getPlayers()) {
        if (player.getHandCards().isEmpty()) {
            gameState.setGameOver(true);
            gameState.setWinnerId(player.getPlayerId());

            System.out.println("[CardGame][WIN] Winner: "
                    + player.getPlayerId()
```

游戏结束后发送蓝牙消息：

```493:500:app/src/main/java/com/example/cardgame/controller/GameController.java
private void sendGameOverIfNeeded() {
    if (!bluetoothMode || bluetoothActionHandler == null) return;
    if (!gameEngine.isGameOver()) return;
    GameState state = gameEngine.getGameState();
    if (state == null || state.getWinnerId() == null) return;
    Player winner = state.getPlayerById(state.getWinnerId());
    String winnerName = winner != null ? winner.getPlayerName() : state.getWinnerId();
    bluetoothActionHandler.sendGameOver(state.getWinnerId(), winnerName);
}
```

蓝牙 `GAME_OVER` 消息构造与发送：

```324:335:app/src/main/java/com/example/cardgame/network/BluetoothGateway.java
public void sendGameOver(String winnerId, String winnerName) {
    if (winnerId == null || winnerId.trim().isEmpty()) {
        Log.w("CardGame", "[WARN] [蓝牙] [发送] 游戏结束胜者为空 | winnerId:null");
        return;
    }

    GameOverPayload payload = new GameOverPayload(winnerId, winnerName);

    BluetoothMessage message = messageCodec.buildGameOverMessage(
            localPlayerId, "ALL", payload);

    sendBluetoothMessage(message, "游戏结束:" + winnerId);
}
```

UI 弹窗：

```262:264:app/src/main/java/com/example/cardgame/ui/GameActivity.java
if (data.isGameOver() && !gameOverDialogShown) {
    showGameOverDialog(data);
}
```

```581:624:app/src/main/java/com/example/cardgame/ui/GameActivity.java
private void showGameOverDialog(GameViewData data) {
    if (gameOverDialogShown) return;

    gameOverDialogShown = true;

    List<PlayerViewData> players = data.getPlayers();
    if (players == null || players.isEmpty()) return;

    List<PlayerViewData> sorted = new ArrayList<>(players);
    sorted.sort((a, b) -> Integer.compare(a.getRemainingCardCount(), b.getRemainingCardCount()));

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    View dialogView = getLayoutInflater().inflate(R.layout.dialog_game_over, null);
    ...
    dialog.show();
}
```

#### 3.3.2 远程 `GAME_OVER` 消息流程

调用链：

`BluetoothReceiver.handleRawMessage()`（蓝牙通信 / `BluetoothReceiver`）  
→ `BluetoothGateway.onMessageReceived()`（蓝牙通信 / `BluetoothGateway`）  
→ `NetworkGameBridge.handleMessage()`（蓝牙通信 / `NetworkGameBridge`）  
→ `NetworkGameBridge.handleGameOver()`（蓝牙通信 / `NetworkGameBridge`）  
→ `BluetoothEventListener.onGameOver()`（蓝牙通信回调接口）  
→ `BluetoothController.onGameOver()`（控制器 / `BluetoothController`）  
→ `BluetoothViewData.setStatusText()`（DTO / `BluetoothViewData`）

代码中 `NetworkGameBridge.handleGameOver()` 未直接调用 `GameEngine` 修改 `GameState`；`GameActivity.showGameOverDialog()` 依赖 `GameViewData.isGameOver()`。因此，仅由 `GAME_OVER` 消息直接触发 `GameActivity` 弹窗的调用链未明确。

```201:210:app/src/main/java/com/example/cardgame/network/NetworkGameBridge.java
private void handleGameOver(BluetoothMessage message) {
    try {
        GameOverPayload payload =
                messageCodec.decodeGameOverPayload(message.getPayloadJson());

        notifyReceived(MessageType.GAME_OVER, "游戏结束，胜者:" + payload.getWinnerId());

        if (eventListener != null) {
            eventListener.onGameOver(payload.getWinnerId(), payload.getWinnerName());
        }
```

```284:286:app/src/main/java/com/example/cardgame/controller/BluetoothController.java
public void onGameOver(String winnerId, String winnerName) {
    bluetoothViewData.setStatusText("游戏结束，胜者：" + winnerName);
}
```

---

## 4. 耦合度事实清单

### 4.1 直接 UI 刷新调用

#### 4.1.1 `GameController` 保存并触发 UI 刷新回调

```64:90:app/src/main/java/com/example/cardgame/controller/GameController.java
@Override
public void setUiRefreshCallback(Runnable callback) {
    this.uiRefreshCallback = callback;
}

private void notifyUiRefresh() {
    if (uiRefreshCallback != null) uiRefreshCallback.run();
}
```

调用位置示例：

```201:205:app/src/main/java/com/example/cardgame/controller/GameController.java
sendGameOverIfNeeded();
}
notifyUiRefresh();
if (!gameEngine.isGameOver()) {
    new Handler(Looper.getMainLooper()).postDelayed(this::triggerNextAction, 100);
```

```226:230:app/src/main/java/com/example/cardgame/controller/GameController.java
bluetoothActionHandler.sendLocalPass(currentPlayer.getPlayerId());
}
notifyUiRefresh();
if (!gameEngine.isGameOver()) {
    new Handler(Looper.getMainLooper()).postDelayed(this::triggerNextAction, 100);
```

#### 4.1.2 `GameController` 直接调用倒计时 UI 回调接口

```50:57:app/src/main/java/com/example/cardgame/controller/GameController.java
public interface CountdownUICallback {
    void showCountdown();
    void updateCountdown(int secondsLeft);
    void hideCountdown();
}

public void setCountdownCallback(CountdownUICallback callback) {
    this.countdownCallback = callback;
}
```

```375:388:app/src/main/java/com/example/cardgame/controller/GameController.java
if (countdownCallback != null) {
    countdownCallback.updateCountdown(seconds);
}
...
if (countdownCallback != null) {
    countdownCallback.showCountdown();
}
```

```414:425:app/src/main/java/com/example/cardgame/controller/GameController.java
if (countdownCallback != null) {
    countdownCallback.hideCountdown();
}
...
if (countdownCallback != null) {
    countdownCallback.hideCountdown();
}
```

#### 4.1.3 `GameActivity` 将自身 UI 刷新方法传入控制层

```139:140:app/src/main/java/com/example/cardgame/ui/GameActivity.java
if (gameActionHandler != null) {
    gameActionHandler.setUiRefreshCallback(() -> runOnUiThread(this::refreshUI));
```

### 4.2 核心逻辑中直接输出日志

#### 4.2.1 `GameEngine` 直接 `System.out.println`

```53:64:app/src/main/java/com/example/cardgame/engine/GameEngine.java
public PlayResult playCards(String playerId, List<String> selectedCardIds) {
    System.out.println("[CardGame][PLAY] request playerId=" + playerId + ", selectedCardIds=" + selectedCardIds);
    ...
    if (selectedCardIds == null || selectedCardIds.isEmpty()) {
        System.out.println("[CardGame][PLAY] rejected: selectedCardIds is empty");
```

```112:128:app/src/main/java/com/example/cardgame/engine/GameEngine.java
public PassResult passTurn(String playerId) {
    System.out.println("[CardGame][PASS] request playerId=" + playerId);

    if (gameState == null || gameState.isGameOver()) {
        System.out.println("[CardGame][PASS] rejected: game is over");
        ...
    }
    ...
    if (gameState.isOpeningTurn() || gameState.getLastPlay() == null || gameState.getLastPlay().isEmpty()) {
        System.out.println("[CardGame][PASS] rejected: new round starter must play cards");
```

#### 4.2.2 `DealManager`, `TurnManager`, `SettlementManager` 直接 `System.out.println`

```86:109:app/src/main/java/com/example/cardgame/engine/DealManager.java
private void printDealLogs(GameState gameState) {
    System.out.println("[CardGame][DEAL] Dealing completed.");
    ...
    System.out.println("[CardGame][DEAL] Player "
    ...
    System.out.println("[CardGame][TURN] Opening player: "
```

```30:35:app/src/main/java/com/example/cardgame/engine/TurnManager.java
gameState.setCurrentPlayerId(nextPlayer.getPlayerId());

gameState.setOpeningTurn(false);

System.out.println("[CardGame][TURN] Next player: "
```

```48:53:app/src/main/java/com/example/cardgame/engine/SettlementManager.java
for (Player player : gameState.getPlayers()) {
    if (player.getHandCards().isEmpty()) {
        gameState.setGameOver(true);
        gameState.setWinnerId(player.getPlayerId());

        System.out.println("[CardGame][WIN] Winner: "
```

### 4.3 硬编码的外部模块调用：直接 `new` 具体类 / 静态调用

#### 4.3.1 `CardGameApplication` 直接创建核心对象并静态持有

```14:23:app/src/main/java/com/example/cardgame/CardGameApplication.java
private static GameEngine gameEngine;
private static GameActionHandler gameActionHandler;
private static BluetoothActionHandler bluetoothActionHandler;

@Override
public void onCreate() {
    super.onCreate();

    gameEngine = new GameEngine();
    gameActionHandler = new GameController(gameEngine);
```

```36:43:app/src/main/java/com/example/cardgame/CardGameApplication.java
public static synchronized BluetoothActionHandler getBluetoothActionHandler(Context context) {
    if (bluetoothActionHandler == null) {
        bluetoothActionHandler = new BluetoothController(
                context.getApplicationContext(),
                gameEngine
        );
    }
    return bluetoothActionHandler;
}
```

#### 4.3.2 `GameEngine` 构造具体管理器与规则引擎

```33:37:app/src/main/java/com/example/cardgame/engine/GameEngine.java
public GameEngine() {
    this.dealManager = new DealManager();
    this.turnManager = new TurnManager();
    this.settlementManager = new SettlementManager();
    this.ruleEngine = new RuleEngine();
}
```

#### 4.3.3 `GameController` 直接创建规则校验器、玩家、AI

```44:45:app/src/main/java/com/example/cardgame/controller/GameController.java
// 倒计时相关
private final PlayValidator playValidator = new PlayValidator();
```

```97:123:app/src/main/java/com/example/cardgame/controller/GameController.java
List<Player> players = new ArrayList<>();
Player p1 = new Player("P1", "Alice");
Player p2 = new Player("P2", "Bob");
Player p3 = new Player("P3", "Cindy");
Player p4 = new Player("P4", "David");
...
RuleConfig ruleConfig = new RuleConfig();
```

```432:435:app/src/main/java/com/example/cardgame/controller/GameController.java
private AIPlayer getOrCreateAIPlayer(Player player) {
    if (player.getType() != PlayerType.AI) return null;
    return aiPlayerCache.computeIfAbsent(player.getPlayerId(), id -> {
        AIPlayer ai = new AIPlayer(id);
```

#### 4.3.4 `BluetoothController` 直接创建蓝牙视图数据和网关

```25:29:app/src/main/java/com/example/cardgame/controller/BluetoothController.java
public BluetoothController(Context context, GameEngine gameEngine) {
    this.bluetoothViewData = new BluetoothViewData();
    updateBluetoothStatus();
    this.bluetoothGateway = new BluetoothGateway(context, gameEngine);
    this.bluetoothGateway.setBluetoothEventListener(this);
}
```

#### 4.3.5 `BluetoothGateway` 直接创建连接、编解码、桥接、收发器

```66:69:app/src/main/java/com/example/cardgame/network/BluetoothGateway.java
public BluetoothGateway(Context context, GameEngine gameEngine) {
    this.connectionManager = new BluetoothConnectionManager(context);
    this.messageCodec = new BluetoothMessageCodec();
    this.networkGameBridge = new NetworkGameBridge(gameEngine, messageCodec);
```

```121:123:app/src/main/java/com/example/cardgame/network/BluetoothGateway.java
BluetoothSender sender = new BluetoothSender(conn.outputStream, messageCodec);
BluetoothReceiver receiver = new BluetoothReceiver(conn.inputStream, messageCodec, this);
receiver.startListening();
```

#### 4.3.6 静态调用

```102:144:app/src/main/java/com/example/cardgame/ui/GameActivity.java
gameActionHandler = CardGameApplication.getGameActionHandler();
...
bluetoothActionHandler = CardGameApplication.getBluetoothActionHandler(this);
```

```106:107:app/src/main/java/com/example/cardgame/engine/GameEngine.java
} else {
    Logger.win("游戏结束，获胜者: " + gameState.getWinnerId());
}
```

```32:34:app/src/main/java/com/example/cardgame/controller/BluetoothController.java
private void updateBluetoothStatus() {
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    bluetoothViewData.setBluetoothAvailable(adapter != null);
```

### 4.4 事件隐式传递：通过共享 `GameState` / 数据对象传递状态

#### 4.4.1 出牌通过修改 `GameState` 与 `Player` 传递状态

```84:97:app/src/main/java/com/example/cardgame/engine/GameEngine.java
player.getHandCards().removeIf(card -> selectedCardIds.contains(card.getCardId()));

gameState.setLastPlay(currentPlay);
player.setPassed(false);

gameState.setLastWinnerId(playerId);
gameState.resetConsecutivePassCount();

if (gameState.isOpeningTurn()) gameState.setOpeningTurn(false);
gameState.updateLastPlayByPlayer(playerId, selectedCards);
```

#### 4.4.2 过牌通过修改 `GameState` 与 `Player` 传递状态

```132:146:app/src/main/java/com/example/cardgame/engine/GameEngine.java
player.setPassed(true);
gameState.updateLastPlayByPlayer(playerId, null);
gameState.incrementConsecutivePassCount();

if (gameState.getConsecutivePassCount() >= 3) {
    String winnerId = gameState.getLastWinnerId();

    gameState.setLastPlay(null);
    gameState.clearAllPassStatus();
    gameState.clearAllLastPlayRecords();
    gameState.resetConsecutivePassCount();
```

#### 4.4.3 游戏结束通过 `GameState` 字段传递

```48:51:app/src/main/java/com/example/cardgame/engine/SettlementManager.java
for (Player player : gameState.getPlayers()) {
    if (player.getHandCards().isEmpty()) {
        gameState.setGameOver(true);
        gameState.setWinnerId(player.getPlayerId());
```

#### 4.4.4 UI 通过 `GameController.getGameViewData()` 读取 `GameState` 派生界面数据

```247:298:app/src/main/java/com/example/cardgame/controller/GameController.java
public GameViewData getGameViewData() {
    GameState state = gameEngine.getGameState();
    if (state == null) return emptyViewData();
    Player currentPlayer = state.getCurrentPlayer();
    Player me = state.getPlayerById(myPlayerId);
    ...
    Map<String, List<Card>> rawMap = state.getLastPlayByPlayer();
    ...
    return new GameViewData(me.getPlayerId(), me.getPlayerName(), players,
            new ArrayList<>(selectedCardIds), myHandCards,
            state.getLastPlay() == null ? "" : state.getLastPlay().toString(),
            gameEngine.isGameOver(),
            gameEngine.isGameOver() && winner != null ? winner.getPlayerName() : "",
            playerLastPlayCards);
}
```

#### 4.4.5 蓝牙开局同步直接发送完整 `GameState`

```290:321:app/src/main/java/com/example/cardgame/network/BluetoothGateway.java
public void syncGameState(GameState gameState) {
    if (gameState == null) {
        Log.w("CardGame", "[WARN] [蓝牙] [发送] 同步状态为空 | gameState:null");
        return;
    }
    ...
    InitGamePayload payload = new InitGamePayload(
            handMap,
            playerOrder,
            gameState.getCurrentPlayerId(),
            gameState
    );

    BluetoothMessage message = messageCodec.buildInitGameMessage(
            localPlayerId, "ALL", payload);

    sendBluetoothMessage(message, "同步游戏状态 currentPlayer:" + gameState.getCurrentPlayerId());
}
```

```94:102:app/src/main/java/com/example/cardgame/network/NetworkGameBridge.java
if (payload.getGameState() != null) {
    HermesLog.log("BRIDGE handleInitGame hasGameState=true");
    GameState syncedState = payload.getGameState();

    invokeEngineMethod(
            "rebuildGameState",
            new Class[]{GameState.class},
            syncedState
    );
```

### 4.5 多对一依赖

#### 4.5.1 多个模块调用 `GameEngine`

涉及模块：应用启动、控制器、蓝牙通信 / 网络桥接、测试代码。

```22:23:app/src/main/java/com/example/cardgame/CardGameApplication.java
gameEngine = new GameEngine();
gameActionHandler = new GameController(gameEngine);
```

```32:32:app/src/main/java/com/example/cardgame/controller/GameController.java
private final GameEngine gameEngine;
```

```28:35:app/src/main/java/com/example/cardgame/network/NetworkGameBridge.java
private final GameEngine gameEngine;
private final BluetoothMessageCodec messageCodec;
...
public NetworkGameBridge(GameEngine gameEngine, BluetoothMessageCodec messageCodec) {
    this.gameEngine = gameEngine;
```

```66:69:app/src/main/java/com/example/cardgame/network/BluetoothGateway.java
public BluetoothGateway(Context context, GameEngine gameEngine) {
    this.connectionManager = new BluetoothConnectionManager(context);
    this.messageCodec = new BluetoothMessageCodec();
    this.networkGameBridge = new NetworkGameBridge(gameEngine, messageCodec);
```

#### 4.5.2 多个模块读写 `GameState`

涉及模块：`GameEngine`, `DealManager`, `TurnManager`, `SettlementManager`, `GameController`, `BluetoothGateway`, `NetworkGameBridge`, DTO。

```16:18:app/src/main/java/com/example/cardgame/engine/TurnManager.java
public void switchPlayer(GameState gameState) {
    List<Player> players = gameState.getPlayers();
    String currentId = gameState.getCurrentPlayerId();
```

```43:51:app/src/main/java/com/example/cardgame/engine/SettlementManager.java
public void checkAndSettle(GameState gameState) {
    if (gameState.isGameOver()) {
        return;
    }

    for (Player player : gameState.getPlayers()) {
        if (player.getHandCards().isEmpty()) {
            gameState.setGameOver(true);
            gameState.setWinnerId(player.getPlayerId());
```

```3:11:app/src/main/java/com/example/cardgame/dto/PlayResult.java
import com.example.cardgame.model.GameState;

public class PlayResult {

    private boolean success;
    private String message;
    private GameState gameState;
```

```3:11:app/src/main/java/com/example/cardgame/dto/PassResult.java
import com.example.cardgame.model.GameState;

public class PassResult {

    private boolean success;
    private String message;
    private GameState gameState;
```

#### 4.5.3 多个模块调用规则校验 / 牌型识别类

涉及模块：`GameEngine`, `GameController`, `AIPlayer`, `RuleEngine`, `PlayValidator`。

```71:79:app/src/main/java/com/example/cardgame/engine/GameEngine.java
PlayValidator.ValidationResult validationResult =
        ruleEngine.validatePlay(selectedCards, lastPlayCards, isFirstRound, isFirstTurn);
...
PatternRecognizer.PatternInfo patternInfo = ruleEngine.recognizePattern(selectedCards);
```

```344:348:app/src/main/java/com/example/cardgame/controller/GameController.java
boolean isFirstRound = gameEngine.isFirstRound();
boolean isFirstTurn = state.isOpeningTurn();
List<Card> lastPlay = gameEngine.getLastPlayCards();

boolean hasAnyValid = playValidator.hasAnyValidPlay(player, lastPlay, isFirstRound, isFirstTurn);
```

```21:25:app/src/main/java/com/example/cardgame/ai/AIPlayer.java
public AIPlayer(String playerId) {
    this.playerId = playerId;
    this.hand = new ArrayList<>();
    this.patternRecognizer = new PatternRecognizer();
    this.playValidator = new PlayValidator();
}
```

---

## 5. 现有通信方式汇总

| 通信方式 | 使用场景 | 代码事实 |
|---|---|---|
| 直接方法调用 | UI 调控制器；控制器调引擎；控制器调蓝牙；蓝牙网关调 bridge；引擎调规则/回合/结算 | `GameActivity` 调 `gameActionHandler.submitPlay()` / `passTurn()`；`GameController` 调 `gameEngine.playCards()` / `passTurn()` |
| 静态全局入口 | Activity 获取共享的游戏控制器、蓝牙控制器、游戏引擎 | `CardGameApplication` 静态字段 `gameEngine`, `gameActionHandler`, `bluetoothActionHandler` |
| 共享 `GameState` 对象 | 游戏状态推进、UI 数据派生、蓝牙开局同步、操作结果返回 | `GameEngine` 持有 `GameState`；`PlayResult` / `PassResult` 包含 `GameState`；`BluetoothGateway.syncGameState()` 发送 `GameState` |
| 回调接口 / Runnable | 控制层通知 UI 刷新；倒计时 UI；蓝牙事件；蓝牙消息接收；Adapter 点击 | `GameController.setUiRefreshCallback(Runnable)`；`CountdownUICallback`；`BluetoothEventListener`；`BluetoothMessageListener`；`CardAdapter.OnItemClickListener` |
| 轮询刷新 | 蓝牙游戏界面、蓝牙房间列表周期性读取控制器状态刷新 UI | `GameActivity.bluetoothRefreshRunnable` 每秒 `refreshUI()`；`RoomLobbyActivity.refreshBluetoothStateRunnable` 每秒 `refreshBluetoothState()` |
| DTO / ViewData | 控制层向 UI 提供只读展示数据 | `GameController.getGameViewData()` 返回 `GameViewData`；`BluetoothController.getBluetoothViewData()` 返回 `BluetoothViewData` |
| 蓝牙消息 + JSON payload | 跨设备同步开局、出牌、过牌、游戏结束、玩家加入/离开 | `BluetoothMessageCodec` 使用 `Gson` 编码/解码 `InitGamePayload`, `PlayActionPayload`, `PassActionPayload`, `GameOverPayload` |
| 反射调用 | 蓝牙 bridge 将消息转为对 `GameEngine` 方法的调用 | `NetworkGameBridge.invokeEngineMethod()` 通过方法名调用 `rebuildGameState`, `executeRemotePlay`, `executeRemotePass` |

相关代码位置：

```28:43:app/src/main/java/com/example/cardgame/CardGameApplication.java
public static GameActionHandler getGameActionHandler() {
    return gameActionHandler;
}

public static GameEngine getGameEngine() {
    return gameEngine;
}

public static synchronized BluetoothActionHandler getBluetoothActionHandler(Context context) {
    ...
    return bluetoothActionHandler;
}
```

```51:71:app/src/main/java/com/example/cardgame/network/NetworkGameBridge.java
public void handleMessage(BluetoothMessage message) {
    if (message == null || message.getMessageType() == null) {
        notifyError("Invalid bluetooth message", null);
        return;
    }

    switch (message.getMessageType()) {
        case INIT_GAME:
            handleInitGame(message);
            break;

        case PLAY_ACTION:
            handlePlayAction(message);
            break;
```

```252:255:app/src/main/java/com/example/cardgame/network/NetworkGameBridge.java
private boolean invokeEngineMethod(String methodName, Class<?>[] parameterTypes, Object... args) {
    try {
        Method method = gameEngine.getClass().getMethod(methodName, parameterTypes);
        method.invoke(gameEngine, args);
```
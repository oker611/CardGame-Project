# Interface Contract

## 1. 目的

本文件用于统一第三周开发中的模块交接方式，明确：

- UI 调谁
- Controller 调谁
- Logic / Engine 暴露什么
- Rule 返回什么
- 各层之间传递什么数据

本周开发中，不允许成员私自修改已冻结接口。
若发现接口不足，应先提出，再统一修改。

---

## 2. 总体架构

本项目采用以下分层调用结构：

```text
UI -> Controller -> Engine -> Rule
                  -> DTO/ViewData -> UI
```

说明：

- UI 层只负责展示和点击事件
- Controller 层负责把 UI 输入转成逻辑调用
- Engine 层负责游戏流程
- Rule 层负责规则判断
- DTO 层负责模块交接数据

## 3. 调用边界规则

### 3.1 UI 层

UI 层只能：

- 展示数据
- 获取用户输入
- 调用 GameActionHandler
- 接收 GameViewData

UI 层禁止：

- 直接修改 GameState
- 直接调用 RuleEngine
- 直接操作 Player.handCards

### 3.2 Controller 层

Controller 层负责：

- 接收 UI 请求
- 调用 GameEngine
- 将 GameState 转成 GameViewData

Controller 层禁止：

- 自己写规则判断
- 直接实现牌型识别

### 3.3 Engine 层

Engine 层负责：

- 游戏初始化
- 发牌
- 切换回合
- 调用规则校验
- 应用出牌结果
- 更新 GameState

Engine 层禁止：

- 直接写 Android UI 逻辑

### 3.4 Rule 层

Rule 层负责：

- 牌型识别
- 合法性判断
- 压制关系判断

Rule 层禁止：

- 切换当前玩家
- 修改 UI
- 直接执行结算

## 4. UI 对外统一接口

UI 层通过 GameActionHandler 进行操作。

### 4.1 GameActionHandler

```java
public interface GameActionHandler {
    void startNewGame();
    PlayResult submitPlay(List<String> selectedCardIds);
    PassResult passTurn();
    void toggleCardSelection(String cardId);
    GameViewData getGameViewData();
}
```

#### 方法说明

**startNewGame()**

- 作用：初始化一局新游戏，创建初始 GameState
- 调用方：GameActivity

**submitPlay(List<String> selectedCardIds)**

- 作用：提交当前玩家已选择的牌，触发出牌校验与执行
- 输入：selectedCardIds：当前被选中的牌 id 列表
- 返回：PlayResult
- 说明：UI 不能直接传 List<Card>，统一用 cardId 交接，避免 UI 持有可变对象乱改

**passTurn()**

- 作用：当前玩家执行过牌
- 返回：PassResult

**toggleCardSelection(String cardId)**

- 作用：切换某张牌的选中状态
- 说明：UI 点击手牌后调用，真正的选中状态由系统统一管理

**getGameViewData()**

- 作用：获取当前用于展示的页面数据
- 返回：GameViewData

## 5. Controller 与 Engine 统一接口

### 5.1 GameEngine

```java
public interface GameEngine {
    void initializeGame(List<Player> players, RuleConfig ruleConfig);
    void dealCards();
    PlayResult playCards(String playerId, List<String> selectedCardIds);
    PassResult pass(String playerId);
    boolean isGameOver();
    String getWinnerId();
    GameState getGameState();
}
```

**initializeGame(List<Player> players, RuleConfig ruleConfig)**

- 作用：初始化玩家与规则配置，创建初始 GameState

**dealCards()**

- 作用：执行洗牌、发牌、标记首出玩家

**playCards(String playerId, List<String> selectedCardIds)**

- 作用：当前玩家出牌
- 内部流程：根据 selectedCardIds 找到牌 → 构造 Play → 调用规则校验 → 校验成功后更新 GameState → 切换回合或结算
- 返回：PlayResult

**pass(String playerId)**

- 作用：当前玩家过牌，更新 Pass 状态，判断是否全员 Pass
- 返回：PassResult

**isGameOver()**

- 作用：返回当前对局是否结束

**getWinnerId()**

- 作用：如果结束，返回获胜玩家 id

**getGameState()**

- 作用：返回当前完整游戏状态
- 说明：仅供 Controller 使用，UI 不能直接依赖该对象做修改

## 6. Engine 对 Rule 的统一接口

### 6.1 RuleEngine

```java
public interface RuleEngine {
    ValidationResult validatePlay(
        Play currentPlay,
        Play lastPlay,
        RuleConfig ruleConfig,
        boolean isOpeningTurn
    );

    CardPattern recognizePattern(List<Card> cards);
}
```

**validatePlay(...)**

- 作用：判断当前出牌是否合法
- 输入：currentPlay、lastPlay、ruleConfig、isOpeningTurn
- 输出：ValidationResult，至少包含：是否合法、提示信息、当前牌型

**recognizePattern(List<Card> cards)**

- 作用：识别当前牌的牌型
- 输出：CardPattern

## 7. 联机模块占位接口

第三周不要求完整蓝牙联机，但必须定义未来可接入接口。

### 7.1 MultiplayerGateway

```java
public interface MultiplayerGateway {
    void sendPlayAction(Play play);
    void sendPassAction(String playerId);
    void syncGameState(GameState gameState);
}
```

说明：

- 第三周先只保留接口
- 不实现完整网络逻辑
- 后续蓝牙模块直接接这里

## 8. AI 模块占位接口

### 8.1 AiStrategy

```java
public interface AiStrategy {
    List<String> choosePlay(GameState gameState, String playerId);
    boolean shouldPass(GameState gameState, String playerId);
}
```

说明：

- 第三周 AI 只做简单策略
- 不要求最优解

## 9. DTO 结构约定

### 9.1 ValidationResult

```java
public class ValidationResult {
    private boolean valid;
    private String message;
    private CardPattern pattern;
}
```

- valid：是否合法
- message：错误或提示信息
- pattern：识别出的牌型

### 9.2 PlayResult

```java
public class PlayResult {
    private boolean success;
    private String message;
    private GameState gameState;
}
```

- success：出牌是否成功
- message：结果说明
- gameState：更新后的状态

### 9.3 PassResult

```java
public class PassResult {
    private boolean success;
    private String message;
    private GameState gameState;
}
```

### 9.4 PlayerViewData

```java
public class PlayerViewData {
    private String playerId;
    private String playerName;
    private int remainingCardCount;
    private boolean currentTurn;
    private boolean passed;
}
```

### 9.5 GameViewData

```java
public class GameViewData {
    private String currentPlayerId;
    private String currentPlayerName;
    private List<PlayerViewData> players;
    private List<String> selectedCardIds;
    private String lastPlayText;
    private boolean gameOver;
    private String winnerName;
}
```

说明：

- UI 页面统一从这里取展示数据
- UI 不直接读 GameState 的内部细节

## 10. 核心流程约定

第三周统一主流程：

1. startNewGame()
2. -> initializeGame(...)
3. -> dealCards()
4. -> getGameViewData()
5. -> 用户选牌(toggleCardSelection)
6. -> submitPlay(...)
7. -> validatePlay(...)
8. -> 更新GameState
9. -> 切换玩家 / passTurn()
10. -> isGameOver()
11. -> getWinnerId()

## 11. 模块交接清单

### 11.1 M2（UI）需要依赖

- GameActionHandler
- GameViewData
- PlayerViewData

### 11.2 M3（游戏逻辑）需要提供

- GameEngine
- DealManager
- TurnManager
- SettlementManager

### 11.3 M5（规则）需要提供

- RuleEngine
- PatternRecognizer
- PlayValidator
- ValidationResult

### 11.4 M6（文档与集成）需要维护

- 本文件
- 命名规范
- DTO 定义一致性
- 联调时接口是否跑偏

## 12. 联调规则

- 不允许未同步就擅自改接口方法名
- 不允许一个接口今天返回 boolean，明天改成 String 而不通知
- 若发现字段不够，先提 issue 或群里说明，再统一调整
- UI / 逻辑 / 规则三方联调时，以本文件为准

## 13. 当前冻结接口范围

第三周冻结以下内容：

- 核心类名
- GameActionHandler
- GameEngine
- RuleEngine
- DTO 名称与主要字段名

若确实需要修改，必须先同步负责人统一修改文档，再改代码。
```

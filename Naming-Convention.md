# Naming Convention

## 1. 目的
为了保证多人协作时代码可读、可维护、可交接，本项目统一采用英文命名规范。
所有成员在编写代码、接口、类图、文档映射字段时，都必须遵守本规范。

---

## 2. 总体规则

### 2.1 文档、代码语言约定
- 文档：中文为主，关键术语附英文
- 代码：纯英文命名
- 注释：英文为主，必要时可补充中文说明
- 严禁使用拼音命名
- 严禁中英混合命名

### 2.2 基本要求
- 命名要表达真实含义
- 不使用无意义缩写
- 同一概念全项目保持同一写法
- 不允许同一个对象在不同文件中使用不同名称

---

## 3. 命名格式

### 3.1 类名
采用 **PascalCase**

示例：
- `GameEngine`
- `RuleEngine`
- `PlayValidator`
- `GameController`
- `Player`
- `Card`
- `GameState`

错误示例：
- `gameEngine`
- `game_engine`
- `YouXiYinQing`

---

### 3.2 接口名
采用 **PascalCase**，可用业务语义命名，不强制加 `I`

示例：
- `RuleEngine`
- `GameActionHandler`
- `MultiplayerGateway`

---

### 3.3 方法名
采用 **camelCase**

示例：
- `dealCards()`
- `submitPlay()`
- `passTurn()`
- `validatePlay()`
- `recognizePattern()`
- `getCurrentPlayer()`

错误示例：
- `DealCards()`
- `deal_cards()`
- `chupai()`

---

### 3.4 变量名
采用 **camelCase**

示例：
- `currentPlayer`
- `selectedCards`
- `lastPlay`
- `playerList`
- `gameState`

错误示例：
- `CurrentPlayer`
- `selected_cards`
- `dangQianPlayer`

---

### 3.5 常量名
采用 **全大写 + 下划线**

示例：
- `MAX_PLAYER_COUNT`
- `INITIAL_CARD_COUNT`
- `THREE_OF_DIAMONDS`

---

### 3.6 布尔变量
优先使用具有判断意义的命名

示例：
- `isGameOver`
- `isOpeningTurn`
- `isAiControlled`
- `isValid`

---

## 4. 项目核心术语冻结表

以下术语为本项目统一术语，不得随意替换。

| 中文         | 英文统一写法               |
|--------------|----------------------------|
| 玩家         | `Player`                   |
| 纸牌         | `Card`                     |
| 花色         | `Suit`                     |
| 点数         | `Rank`                     |
| 牌型         | `CardPattern`              |
| 一次出牌     | `Play`                     |
| 游戏状态     | `GameState`                |
| 房间         | `Room`                     |
| 房间配置     | `RoomConfig`               |
| 发牌         | `dealCards`                |
| 出牌         | `playCards` / `submitPlay` |
| 过牌         | `passTurn`                 |
| 校验出牌     | `validatePlay`             |
| 识别牌型     | `recognizePattern`         |
| 当前玩家     | `currentPlayer`            |
| 上一手牌     | `lastPlay`                 |
| 首轮         | `openingTurn`              |
| 结算         | `settleGame` / `SettlementManager` |

---

## 5. 核心类命名表

### 5.1 Model 层
- `Card`
- `Suit`
- `Rank`
- `CardPattern`
- `Play`
- `Player`
- `GameState`
- `RoomConfig`

### 5.2 Engine 层
- `GameEngine`
- `TurnManager`
- `DealManager`
- `SettlementManager`
- `SelectionManager`

### 5.3 Rule 层
- `RuleEngine`
- `PatternRecognizer`
- `PlayValidator`
- `RuleConfig`

### 5.4 Controller 层
- `GameController`
- `GameActionHandler`
- `GameStateMapper`

### 5.5 DTO 层
- `ValidationResult`
- `PlayResult`
- `PassResult`
- `GameViewData`
- `PlayerViewData`

### 5.6 UI 层
- `MainActivity`
- `GameActivity`
- `HandCardAdapter`
- `GameViewModel`

### 5.7 AI 层
- `SimpleAiPlayer`
- `AiStrategy`

---

## 6. 核心方法命名表

### 6.1 游戏流程
- `initializeGame()`
- `startNewGame()`
- `dealCards()`
- `playCards()`
- `submitPlay()`
- `passTurn()`
- `isGameOver()`
- `getWinnerId()`

### 6.2 规则判断
- `validatePlay()`
- `recognizePattern()`
- `canBeatLastPlay()`

### 6.3 UI交互
- `toggleCardSelection()`
- `getGameViewData()`
- `updateGameView()`

### 6.4 AI
- `choosePlay()`
- `choosePass()`

---

## 7. DTO 字段命名建议

### 7.1 ValidationResult
- `valid`
- `message`
- `pattern`

### 7.2 PlayResult
- `success`
- `message`
- `gameState`

### 7.3 PassResult
- `success`
- `message`
- `gameState`

### 7.4 GameViewData
- `currentPlayerId`
- `currentPlayerName`
- `players`
- `selectedCardIds`
- `lastPlayText`
- `gameOver`
- `winnerName`

---

## 8. 注释规范

### 8.1 原则
- 注释说明“为什么”，不是重复“做了什么”
- 简单代码不强制写注释
- 复杂逻辑必须写注释

### 8.2 推荐写法
```java
// Check whether the selected cards can beat the previous play
boolean canBeatLastPlay(Play currentPlay, Play lastPlay) {
    ...
}

// Determine the first player based on the three of diamonds
private String findOpeningPlayer(List<Player> players) {
    ...
}
```

### 8.3 不推荐写法
```java
// 出牌
// 这里判断一下
// temp
```

---

## 9. 禁止事项

以下情况禁止出现：
- 拼音命名
  - `chupai()`
  - `fapai()`
- 中英混合
  - `play牌()`
  - `deal手牌()`
- 无意义命名
  - `temp`
  - `a`
  - `x1`
- 同义词混用
  - 一处写 `playCards()`，另一处写 `submitCards()`，但表示同一件事

---

## 10. 执行规则
- 新建类、方法、字段前，先查本规范
- 若现有术语表中没有对应术语，先提议补充，再使用
- 发现命名冲突，不要私自改接口，先同步负责人
- 每次提交前，检查是否有违反命名规范的地方

---

## 11. 当前阶段特别说明

第三周为高强度开发阶段，允许局部实现不完整，但：
- 命名必须统一
- 接口名必须统一
- 数据对象名必须统一

命名不统一会直接导致联调失败，因此优先级高于局部功能快写。
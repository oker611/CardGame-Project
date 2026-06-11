# 锄大地卡牌游戏 — UML 七大图 AI 建模提示词

> 使用方式：将每张图的提示词单独复制给 GPT（或支持 PlantUML/Mermaid 的 AI），让 AI 生成对应 UML 图。
> 推荐输出格式：PlantUML 源码 或 Mermaid 源码，可直接在 ProcessOn 导入渲染。

---

## 图一：用例图 (Use Case Diagram)

### 提示词

```
请根据以下完整信息，生成一张标准的 UML 用例图（Use Case Diagram），使用 PlantUML 语法输出。

## 系统名称
锄大地蓝牙联机卡牌游戏系统 (Big Two / Choh Dai Di)

## 参与者 (Actors)
1. 人类玩家 (Human Player) — 游戏的主要操作者，通过 Android 触屏交互
2. AI玩家 (AI Player) — 由系统自动控制的电脑对手，无需人工干预
3. 远程玩家 (Remote Player) — 通过蓝牙联机加入的其他真实玩家
4. 蓝牙设备 (Bluetooth Device) — 提供蓝牙通信能力的 Android 设备硬件
5. Vivo蓝心大模型 (Vivo LLM) — 云端大语言模型服务，用于对手风格分析

## 用例列表

### 一、游戏启动与房间管理
1. UC-01 开始游戏 (Start Game) — 玩家选择规则后启动一局新游戏
2. UC-02 创建蓝牙房间 (Create Bluetooth Room) — 作为HOST创建4人对战房间，等待其他玩家蓝牙加入
3. UC-03 搜索蓝牙设备 (Search Bluetooth Devices) — 搜索附近可加入的蓝牙主机设备
4. UC-04 加入蓝牙房间 (Join Bluetooth Room) — 作为CLIENT连接到已创建的蓝牙房间
5. UC-05 查看规则 (View Rules) — 查看南方规则和北方规则的详细差异
6. UC-06 选择规则类型 (Select Rule Type) — 在南方规则/北方规则之间切换
7. UC-07 设置房间属性 (Configure Room Props) — 配置记牌器、透视、牌型提示等道具开关

### 二、游戏对局核心
8. UC-08 进行游戏 (Play Game) — 玩家参与完整的一局锄大地游戏流程
9. UC-09 发牌 (Deal Cards) — 系统自动洗牌并将52张牌均分给4名玩家（各13张）
10. UC-10 出牌 (Play Cards) — 玩家选择手牌中的若干张牌并按规则出牌
11. UC-11 过牌 (Pass) — 玩家无法或选择不出牌时跳过当前回合
12. UC-12 查看游戏状态 (View Game State) — 查看各玩家剩余牌数、当前出牌、上家出牌等信息
13. UC-13 游戏结算 (Game Settlement) — 系统判定胜负并展示排名

### 三、AI 辅助功能
14. UC-14 AI自动出牌 (AI Auto Play) — AI玩家根据策略自动决策出牌或过牌
15. UC-15 AI难度选择 (Select AI Difficulty) — 人类玩家可选择AI难度：简单/中等/困难/智能模式
16. UC-16 AI策略风格选择 (Select AI Strategy Style) — 简单模式下选择AI风格：普通/激进/保守
17. UC-17 查看牌型提示 (View Pattern Hint) — 开启道具后显示当前手牌中可组成的牌型
18. UC-18 使用记牌器 (Use Card Tracker) — 开启道具后追踪已出牌的历史记录
19. UC-19 使用透视 (Use See-Through) — 开启道具后可查看对手剩余牌数
20. UC-20 对手风格分析 (Analyze Opponent Style) — 调用Vivo蓝心大模型分析人类玩家的出牌风格
21. UC-21 自适应AI对抗 (Adaptive AI Counter-Play) — 系统根据分析出的人类风格自动调整AI策略

### 四、蓝牙通信
22. UC-22 同步游戏状态 (Sync Game State) — HOST向所有CLIENT同步完整游戏状态
23. UC-23 同步出牌动作 (Sync Play Action) — 将本地出牌动作广播给所有远程玩家
24. UC-24 同步过牌动作 (Sync Pass Action) — 将本地过牌动作广播给所有远程玩家
25. UC-25 同步游戏结束 (Sync Game Over) — 广播游戏结束消息给所有远程玩家
26. UC-26 心跳保活 (Heartbeat) — 定期发送心跳消息检测蓝牙连接是否存活
27. UC-27 断线重连 (Reconnect) — 蓝牙连接断开后自动尝试重新连接并恢复游戏状态

### 用例关系
- UC-01 "开始游戏" include UC-09 "发牌"
- UC-08 "进行游戏" extend UC-10 "出牌"
- UC-08 "进行游戏" extend UC-11 "过牌"
- UC-10 "出牌" include UC-22/23/24 (蓝牙模式下需同步)
- UC-13 "游戏结算" 触发 UC-25 "同步游戏结束"
- UC-20 "对手风格分析" 由 Vivo蓝心大模型 提供支持
- UC-21 "自适应AI对抗" extend UC-14 "AI自动出牌"
- UC-17/18/19 依赖 UC-07 "设置房间属性" 开启
- UC-02 "创建蓝牙房间" 和 UC-04 "加入蓝牙房间" 都依赖 UC-03 "搜索蓝牙设备"
- UC-27 "断线重连" extend UC-26 "心跳保活" (心跳超时触发重连)
- UC-27 "断线重连" include UC-22 "同步游戏状态" (重连后需全量同步)

## 参与者与用例的关联
- 人类玩家 关联: UC-01~08, UC-10~13, UC-15~19
- AI玩家 关联: UC-14, UC-21
- 远程玩家 关联: UC-10, UC-11 (通过网络远程执行)
- 蓝牙设备 关联: UC-02~04, UC-22~27
- Vivo蓝心大模型 关联: UC-20

## 系统边界
将所有用例框在"锄大地蓝牙联机游戏系统"边界内。参与者位于边界外。

## 输出要求
1. 使用 PlantUML 语法生成
2. 用例用椭圆表示，参与者用人形图标
3. include/extend 关系用虚线箭头标注
4. 系统边界用矩形框表示
5. 每个用例标注编号和名称
6. 参与者按上述分类排列在系统边界两侧
```

---

## 图二：类图 (Class Diagram)

### 提示词

```
请根据以下完整信息，生成一张标准的 UML 类图（Class Diagram），使用 PlantUML 语法。要求包含所有类的属性、方法、以及类之间的关系（继承、实现、依赖、关联、聚合、组合）。

## 项目包结构

项目根包：com.example.cardgame

分为以下顶层模块包：
- model (领域模型)
- engine (游戏核心引擎)
- rule (规则引擎)
- ai (AI决策系统)
- controller (控制器)
- event (事件总线)
- network (蓝牙网络通信)
- network.payload (网络消息载荷)
- llm (大语言模型集成)
- llm.model (LLM数据模型)
- dto (数据传输对象/视图数据)
- util (工具类)
- ui (Android UI Activity/Adapter)

---

## 一、model 包 — 领域模型

### 1. Suit 枚举
- 值: DIAMONDS(♦, 权重0), CLUBS(♣, 权重1), HEARTS(♥, 权重2), SPADES(♠, 权重3)
- 字段: -displayName: String, -symbol: String, -weight: int
- 方法: +getDisplayName(): String, +getSymbol(): String, +getWeight(): int

### 2. Rank 枚举
- 值: THREE(3, 权重3), FOUR(4, 4), FIVE(5, 5), SIX(6, 6), SEVEN(7, 7), EIGHT(8, 8), NINE(9, 9), TEN(10, 10), JACK(J, 11), QUEEN(Q, 12), KING(K, 13), ACE(A, 14), TWO(2, 15)
- 字段: -displayName: String, -weight: int
- 方法: +getDisplayName(): String, +getWeight(): int

### 3. Card 类
- 字段: -cardId: String, -suit: Suit, -rank: Rank
- 方法: +getCardId(): String, +getSuit(): Suit, +getRank(): Rank, +getDisplayText(): String, +getRankWeight(): int, +getSuitWeight(): int, +isThreeOfDiamonds(): boolean, +equals(Object): boolean, +hashCode(): int, +toString(): String
- 关联: Card 依赖 Suit 和 Rank

### 4. Player 类
- 字段: -playerId: String, -playerName: String, -handCards: List<Card>, -passed: boolean, -type: PlayerType, -consecutiveNoPlayCount: int
- 方法: 所有 getter/setter, +addCard(Card), +addCards(List<Card>), +removeCard(Card): boolean, +removeCardById(String): boolean, +findCardsByIds(List<String>): List<Card>, +findCardById(String): Card, +hasCard(String): boolean, +getRemainingCardCount(): int, +clearPassStatus(), +getRandomCards(int): List<Card>, +resetConsecutiveNoPlayCount(), +incrementConsecutiveNoPlayCount()
- 关联: Player 包含 List<Card> (聚合关系), 依赖 PlayerType

### 5. PlayerType 枚举
- 值: HUMAN, AI, REMOTE

### 6. GameState 类
- 字段: -players: List<Player>, -currentPlayerId: String, -lastPlay: Play, -openingTurn: boolean, -gameOver: boolean, -winnerId: String, -lastWinnerId: String, -consecutivePassCount: int, -lastPlayByPlayer: Map<String,List<Card>>, -allPlayedCards: List<Card>
- 方法: 所有 getter/setter, +addPlayer(Player), +getPlayerById(String): Player, +getCurrentPlayer(): Player, +clearAllPassStatus(), +areAllOtherPlayersPassed(String): boolean, +findOpeningPlayer(): Player, +isFirstRound(): boolean, +isFirstTurnOfRound(): boolean, +updateLastPlayByPlayer(String, List<Card>), +incrementConsecutivePassCount(), +resetConsecutivePassCount()
- 关联: GameState 包含 List<Player> (聚合), 关联 Play

### 7. Play 类
- 字段: -playerId: String, -cards: List<Card>, -pattern: CardPattern
- 方法: 所有 getter/setter, +isEmpty(): boolean, +getCardCount(): int, +containsThreeOfDiamonds(): boolean
- 关联: Play 关联 List<Card> 和 CardPattern

### 8. CardPattern 枚举
- 值: INVALID, SINGLE, PAIR, TRIPLE, QUADRUPLE, STRAIGHT, FLUSH, FULL_HOUSE, IRON_BRANCH, STRAIGHT_FLUSH

### 9. HumanStyleProfile 类 (implements Serializable)
- 字段: -playerId: String, -styleLabel: String, -aggressivenessScore: double, -conservativenessScore: double, -bluffingScore: double, -gamesAnalyzed: int, -lastUpdated: long
- 静态常量: STYLE_AGGRESSIVE="激进", STYLE_CONSERVATIVE="保守", STYLE_BALANCED="均衡", STYLE_BLUFFER="虚张声势"
- 方法: 所有 getter/setter, +isAggressive(): boolean, +isConservative(): boolean, +isBluffer(): boolean, +getCounterTactic(): String, +incrementGamesAnalyzed()

### 10. DeviceInfo 类
- 字段: -deviceName: String, -deviceAddress: String, -deviceType: int(0=手机/1=主机/2=其他), -paired: boolean, -signalStrength: int(0=弱/1=中/2=强)
- 方法: 所有 getter

### 11. RemotePlayerInfo 类
- 字段: -playerId: String, -playerName: String, -deviceName: String, -deviceAddress: String, -playerType: PlayerType=REMOTE, -connected: boolean
- 方法: 所有 getter/setter

---

## 二、engine 包 — 游戏核心引擎

### 12. GameEngine 类
- 字段: -gameState: GameState, -ruleConfig: RuleConfig, -ruleEngine: RuleEngine, -dealManager: DealManager, -turnManager: TurnManager, -settlementManager: SettlementManager, -allPlayedCards: List<Card>
- 方法: +initializeGame(List<Player>, RuleConfig), +dealCards(), +playCards(String, List<String>): PlayResult, +passTurn(String): PassResult, +isGameOver(): boolean, +getWinnerId(): String, +getGameState(): GameState, +getLastPlayCards(): List<Card>, +isFirstRound(): boolean, +isFirstTurnOfCurrentRound(): boolean, +getCurrentPlayerId(): String, +getPlayerHand(String): List<Card>, +rebuildGameState(GameState), +rebuildGameState(List<Card>,List<Card>,String), +rebuildGameStateMulti(Map<String,List<Card>>,String), +executeRemotePlay(Play): PlayResult, +executeRemotePass(String): PassResult, +configureBluetoothPlayerTypes(String,String), +configureBluetoothPlayerTypesMulti(Map), +getAllPlayedCards(): List<Card>, +clearRuleCache()
- 关联: GameEngine 持有 RuleEngine, DealManager, TurnManager, SettlementManager (组合关系); 依赖 GameState, Play, Player, 等 model 类

### 13. DealManager 类
- 方法: +dealCards(GameState), -generateFullDeck(): List<Card> (生成52张牌C_1~C_52), -validateDealing(List<Player>), -identifyOpeningPlayer(GameState), -printDealLogs(GameState)

### 14. TurnManager 类
- 方法: +switchPlayer(GameState) (按循环顺序切换到下一玩家，发布TurnChangedEvent)

### 15. SettlementManager 类
- 方法: +checkGameOver(GameState): boolean, +settleGame(GameState), +checkAndSettle(GameState)

---

## 三、rule 包 — 规则引擎

### 16. RuleEngine 接口
- 方法: +recognizePattern(List<Card>): PatternInfo, +validatePlay(List<Card>,List<Card>,boolean,boolean): ValidationResult, +clearCache(), +getCacheStats(): String

### 17. ConfigurableRuleEngine 类 (implements RuleEngine)
- 字段: -recognizer: PatternRecognizer, -validator: PlayValidator
- 构造: +ConfigurableRuleEngine(RuleConfig)
- 方法: 实现 RuleEngine 接口所有方法

### 18. RuleConfig 类 (Builder模式, 不可变)
- 字段(不可变): +rankWeights: Map<Rank,Integer>, +suitWeights: Map<Suit,Integer>, +requiredOpeningRank: Rank, +requiredOpeningSuit: Suit, +allowedPatterns: Set<PatternType>, +fiveCardPriority: Map<PatternType,Integer>, +passResetThreshold: int
- 静态预设: SOUTHERN (南方规则-方块3必须首出), NORTHERN (北方规则-无首轮限制, 花色颠倒, 禁用铁支)
- 内部类: Builder (构建器模式)
- 方法: 工厂方法 +standardRankWeights(), +standardSuitWeights(), +reversedSuitWeights(), +standardFiveCardPriority(), +northernFiveCardPriority(), +northernAllowedPatterns()

### 19. PatternRecognizer 类
- 内部枚举 PatternType: SINGLE, PAIR, TRIPLE, QUADRUPLE, STRAIGHT, FLUSH, FULL_HOUSE, IRON_BRANCH, STRAIGHT_FLUSH, INVALID
- 内部类 PatternInfo: -type: PatternType, -compareValue: int (用于压牌比较, 数值越大牌越大)
- 构造: +PatternRecognizer(RuleConfig)
- 方法: +recognizePattern(List<Card>): PatternInfo (根据张数和规则识别牌型)
- 私有方法: -getRankWeight(Rank): int, -getSuitWeight(Suit): int, -getCardScore(Card): int, -isFlush(List<Card>): boolean, -getStraightScore(List<Card>): int, -getIronBranchScore(List<Card>): int, -getFullHouseScore(List<Card>): int, -getStraightBase(Rank): int
- 牌型识别逻辑: 1张=SINGLE, 2张同点=PAIR, 3张同点=TRIPLE, 4张同点=QUADRUPLE, 5张按优先级: 同花顺>铁支>葫芦>同花>顺子

### 20. PlayValidator 类
- 内部类 ValidationResult: +valid: boolean, +reason: String
- 构造: +PlayValidator(RuleConfig)
- 方法: +validatePlay(List<Card>,List<Card>,boolean,boolean): ValidationResult (校验出牌合法性), +isValidPattern(List<Card>): boolean, +hasAnyValidPlay(Player,List<Card>,boolean,boolean): boolean
- 校验逻辑: 1)Pass校验(首轮首出不能Pass) 2)牌型识别 3)首轮必须包含方块3(南方规则) 4)无上家直接合法 5)有上家必须牌型相同且更大 6)五张牌型可跨类型压制(按优先级)
- 私有方法: -containsRequiredOpeningCard(List<Card>): boolean, -isFiveCardPattern(PatternType): boolean, -getFiveCardPriority(PatternType): int, -generateCombinations(List<Card>,int): List<List<Card>>, -combine(...): void

---

## 四、ai 包 — AI决策系统

### 21. AIDecisionStrategy 接口
- 方法: +decidePlay(Player, GameState): List<Card>, +recordPlayFailure(), +resetFailCount()

### 22. AIDifficulty 枚举
- 值: GREEDY, MONTE_CARLO, ADAPTIVE

### 23. GreedyAIDecisionStrategy 类 (implements AIDecisionStrategy)
- 字段: -config: RuleConfig, -patternRecognizer: PatternRecognizer, -playValidator: PlayValidator, -consecutiveFailCount: int, -aggressivenessFactor: double(=1.0), -defenseFactor: double(=1.0), -style: Style
- 内部枚举 Style: NORMAL, AGGRESSIVE, DEFENSIVE
- 构造: +GreedyAIDecisionStrategy(RuleConfig), +GreedyAIDecisionStrategy(RuleConfig, Style)
- 方法: +decidePlay(Player,GameState): List<Card>, -generateAllValidPlays(List<Card>): List<List<Card>>, -isValidPattern(List<Card>): boolean, -canBeat(...): boolean, -getPlayCompareValue(...): int, +setStyle(Style), +setAggressivenessFactor(double), +setDefenseFactor(double)

### 24. AggressiveAIDecisionStrategy 类 (extends GreedyAIDecisionStrategy)
- 构造: +AggressiveAIDecisionStrategy(RuleConfig) -> 设置 aggressivenessFactor=1.3, defenseFactor=0.7

### 25. DefensiveAIDecisionStrategy 类 (extends GreedyAIDecisionStrategy)
- 构造: +DefensiveAIDecisionStrategy(RuleConfig) -> 设置 aggressivenessFactor=0.7, defenseFactor=1.3

### 26. NormalAIDecisionStrategy 类 (extends GreedyAIDecisionStrategy)
- 构造: +NormalAIDecisionStrategy(RuleConfig) -> 设置 aggressivenessFactor=1.0, defenseFactor=1.0

### 27. MonteCarloAIDecisionStrategy 类 (implements AIDecisionStrategy)
- 常量: NUM_SAMPLES=10, TOP_K_CANDIDATES=4, DECISION_TIMEOUT_MS=1500
- 字段: -candidateGenerator: CandidateGenerator, -opponentHandSampler: OpponentHandSampler, -monteCarloSimulator: MonteCarloSimulator, -phaseManager: PhaseManager, -ruleEngine: RuleEngine, -cardTracker: CardTracker, -profile: AIPlayerProfile, -opponentProfiles: Map<String,AIPlayerProfile>, -consecutiveFailCount: int, -consecutivePassRounds: int, -lastProbingType: String, -probingRoundCount: int, -aggressivenessFactor: double, -defenseFactor: double
- 方法: +decidePlay(Player,GameState): List<Card> (多阶段策略决策：残局强制压牌、多轮试探、开局保守、中盘谨慎、一锤定音、拆牌逼迫、炸弹优先等), +getCardTracker(): CardTracker, +setProfile(AIPlayerProfile), +getProfile(): AIPlayerProfile, +getOpponentProfile(String): AIPlayerProfile, +setOpponentProfile(String,AIPlayerProfile), +setAggressivenessFactor(double), +setDefenseFactor(double), +recordPlayFailure(), +resetFailCount(), +resetPassCounter()
- 私有方法: -calculateDynamicSamples(int): int (手牌>8→100次, 4~8→200次, ≤3→400次), -findBestBeatCard(...): Card, -findBestBomb(...): Card, -getSmallestValidPlay(...): List<Card>, -areOpponentBigCardsDepleted(): boolean, -shouldForceBreakOpponentPattern(...): boolean, -shouldPlayBigPatternNow(...): boolean, -isEndGamePhase(...): boolean, -findMultiRoundProbingPlay(...): List<Card>, -findMaxCombinationPlay(...): List<Card>, -getBestBigPattern(List<Card>): List<Card> (同花顺>铁支>葫芦>同花>顺子), -canBeatBigPattern(...): boolean, +各种私有牌型检测方法

### 28. AdaptiveAIDecisionStrategy 类 (implements AIDecisionStrategy)
- 字段: -monteCarloStrategy: MonteCarloAIDecisionStrategy, -humanStyleProfile: HumanStyleProfile, -humanPlayerId: String
- 方法: +decidePlay(Player,GameState): List<Card> (委托给monteCarloStrategy), +setHumanStyleProfile(HumanStyleProfile), +getHumanStyleProfile(): HumanStyleProfile, +getCurrentTacticDescription(): String, +setHumanPlayerId(String), -applyStyleToFactors() (根据人类风格调整aggressivenessFactor和defenseFactor: 激进→1.2/0.8, 保守→1.5/0.5, 诈唬→1.3/0.7, 均衡→1.1/0.9)

### 29. AIEventListener 类 (implements GameEventListener)
- 字段: -gameController: GameController, -gameEngine: GameEngine, -strategy: AIDecisionStrategy, -handler: Handler, -isHost: boolean
- 方法: +onEvent(GameEvent) (处理TurnChangedEvent触发AI决策+2.2秒延迟, 处理GameOverEvent取消回调), +unregister(), -generateHint(List<Card>,Player,GameState): String, -getMinOpponentHandSize(GameState,Player): int

### 30. AIPlayerProfile 类
- 常量: LEVEL_WEAK, LEVEL_NORMAL, LEVEL_STRONG
- 字段: -level: int, -opponentAggressiveness: double, -opponentDefensive: boolean, -styleAnalyzed: boolean, -keepBigPattern: boolean, -midAggression: double, -earlyPatience: double, -lateAggression: double
- 方法: 所有 getter/setter

### 31. CandidateGenerator 类
- 构造: +CandidateGenerator(RuleEngine, int, PhaseManager, AIPlayerProfile)
- 方法: +generate(List<Card>, Play, boolean, boolean): List<Play>

### 32. OpponentHandSampler 类
- 内部类 World: 对手手牌分布的世界状态
- 方法: +sampleWorlds(Player, GameState, CardTracker, int): List<World>

### 33. MonteCarloSimulator 类
- 方法: +evaluate(Play, Player, GameState, List<World>): double, +setOpponentProfiles(Map<String,AIPlayerProfile>)

### 34. PhaseManager 类
- 内部枚举 GamePhase: EARLY, MID, LATE
- 方法: +getCurrentPhase(Player, GameState): GamePhase, +adjustScore(double, Play, GamePhase, Player, GameState, List<Card>): double, +findBeatForBigTwoPattern(Player, Play): List<Card>

### 35. FastGameSimulator 类
- 方法: +simulate(GameState, Player, Play): void (快速模拟游戏到结束)

### 36. GameStateCloner 类
- 方法: +clone(GameState): GameState (深拷贝游戏状态)

### 37. HumanStyleAnalyzer 类
- 接口 StyleAnalysisCallback: +onAnalysisComplete(HumanStyleProfile), +onAnalysisFailed(String)
- 方法: +analyzeStyleAsync(String, List<String>, HumanStyleProfile), +setCallback(StyleAnalysisCallback), +shutdown()

### 38. AIPlayer 类 (旧版AI，已基本被策略模式取代)
- 字段: -playerId: String, -hand: List<Card>, -config: RuleConfig, -patternRecognizer: PatternRecognizer, -playValidator: PlayValidator
- 方法: +choosePlay(List<Card>,boolean,boolean): List<Card>, +setHand(List<Card>), +getHand(): List<Card>

---

## 五、event 包 — 事件总线系统

### 39. GameEvent 抽象类
- 字段: -timestamp: long
- 方法: +getTimestamp(): long

### 40. CardPlayedEvent 类 (extends GameEvent)
- 字段: -playerId: String, -playedCardIds: List<String>
- 方法: +getPlayerId(): String, +getPlayedCardIds(): List<String>

### 41. PlayerPassedEvent 类 (extends GameEvent)
- 字段: -playerId: String
- 方法: +getPlayerId(): String

### 42. TurnChangedEvent 类 (extends GameEvent)
- 字段: -newCurrentPlayerId: String, -reason: String
- 方法: +getNewCurrentPlayerId(): String, +getReason(): String

### 43. GameOverEvent 类 (extends GameEvent)
- 字段: -winnerId: String
- 方法: +getWinnerId(): String

### 44. GameEventListener 接口
- 方法: +onEvent(GameEvent)

### 45. EventBus 类 (单例, 线程安全)
- 字段: -listeners: List<GameEventListener> (使用 CopyOnWriteArrayList)
- 方法: +getInstance(): EventBus (静态Holder模式), +register(GameEventListener), +unregister(GameEventListener), +post(GameEvent)

---

## 六、controller 包 — 控制器层

### 46. GameActionHandler 接口
- 方法: +startNewGame(), +submitPlay(List<String>): PlayResult, +passTurn(): PassResult, +toggleCardSelection(String), +getGameViewData(): GameViewData, +setSelectedRuleType(String), +setBluetoothActionHandler(BluetoothActionHandler), +setBluetoothMode(boolean,boolean,String), +triggerNextAction(), +setUiRefreshCallback(Runnable)

### 47. GameController 类 (implements GameActionHandler)
- 字段: -gameEngine: GameEngine, -ruleConfig: RuleConfig, -selectedCardIds: List<String>, -myPlayerId: String, -bluetoothMode: boolean, -hostMode: boolean, -selectedRuleType: String, -bluetoothActionHandler: BluetoothActionHandler, -aiEventListener: AIEventListener, -aiStrategy: AIDecisionStrategy, -styleAnalyzer: HumanStyleAnalyzer, -memoryManager: CrossGameMemoryManager, -adaptiveAI: AdaptiveAIDecisionStrategy, -currentHumanActions: List<String>, -appContext: Context, -opponentStyleAnalyzer: OpponentStyleAnalyzer, -styleAnalysisSummary: String, -playValidator: PlayValidator, -activeCountdowns: Map<String,CountDownTimer>
- 内部接口 CountdownUICallback: +showCountdown(), +updateCountdown(int), +hideCountdown()
- 方法: 实现GameActionHandler接口所有方法, +initAdaptiveAI(Context), +cleanupAdaptiveAI(), +setAIDifficulty(AIDifficulty), +aiPlayCards(List<Card>): PlayResult, +getCardTracker(): CardTracker, +getCurrentPlayerHand(): List<Card>, +triggerOpponentAnalysis(), +analyzeOpponentStyles(), +getStyleAnalysisSummary(): String, +updateAiHint(String), +setCountdownCallback(CountdownUICallback)
- 私有方法: -resolveRuleConfig(): RuleConfig, -ensureRuleConfigReady(): RuleConfig, -playerNameFor(...): String, -recordPlayToTracker(String,List<Card>), -analyzeHumanStyleAndAdapt(), -checkAndStartNoPlayCountdown(Player), -startNoPlayCountdown(Player), -forcePass(Player), -cancelCountdown(Player), -reorderPlayersForSelf(...): List<PlayerViewData>, -initAIEventListener(), -getAIDifficulty(): String, -getSelectedAIStrategy(): String

### 48. BluetoothActionHandler 接口
- 方法: +createBluetoothRoom(String), +searchBluetoothDevices(), +connectToDevice(String,String), +disconnectBluetooth(), +sendLocalPlay(Play), +sendLocalPass(String), +syncGameState(GameState), +sendGameOver(String,String), +getBluetoothViewData(): BluetoothViewData, +getRemotePlayerIds(): List<String>, +getPlayerNamesById(): Map<String,String>, +readyForGame(), +notifyAiPlayerAdded(String,int), +hasRealClients(): boolean, +loadBondedDevices()

### 49. BluetoothController 类 (implements BluetoothActionHandler, BluetoothEventListener)
- 字段: -bluetoothGateway: BluetoothGateway, -bluetoothViewData: BluetoothViewData, -eventRelay: BluetoothEventRelay, -appContext: Context
- 方法: 实现所有BluetoothActionHandler和BluetoothEventListener方法, 包括: +onConnected(...), +onServerReady(), +onPlayerAssigned(...), +onPlayerJoined(...), +onPlayerLeft(...), +onAllPlayersReady(), +onDisconnected(String), +onMessageSent(...), +onMessageReceived(...), +onError(...), +onGameOver(...)

### 50. BluetoothEventRelay 类 (implements GameEventListener)
- 字段: -gateway: BluetoothGateway, -gameEngine: GameEngine, -registered: boolean
- 方法: +register(), +unregister(), +onEvent(GameEvent) (处理CardPlayedEvent→sendPlayAction, PlayerPassedEvent→sendPassAction, GameOverEvent→sendGameOver, 跳过REMOTE玩家防回声)

---

## 七、network 包 — 蓝牙网络通信

### 51. MultiplayerGateway 接口
- 方法: +sendPlayAction(Play), +sendPassAction(String), +syncGameState(GameState)

### 52. BluetoothGateway 类 (implements MultiplayerGateway, BluetoothMessageListener)
- 常量: CLIENT_PLAYER_IDS={"P2","P3","P4"}, MAX_CLIENTS=3, HEARTBEAT_INTERVAL_SECONDS=5, HEARTBEAT_TIMEOUT_SECONDS=15, ACK_TIMEOUT_MS=3000, ACK_MAX_RETRIES=3, RECONNECT_PENDING_TIMEOUT_MS=10000
- 字段: -connectionManager: BluetoothConnectionManager, -messageCodec: BluetoothMessageCodec, -networkGameBridge: NetworkGameBridge, -appContext: Context, -eventListener: BluetoothEventListener, -localPlayerId: String, -deviceToPlayerId: Map, -clientChannels: Map<String,SenderReceiverPair>, -playerIdToDevice: Map, -playerNamesById: Map, -role: String, -communicationReady: boolean, -acceptingClients: boolean, -roomFinalized: boolean, -heartbeatExecutor: ScheduledExecutorService, -lastHeartbeatByAddress: ConcurrentHashMap, -pendingByChannel: ConcurrentHashMap, -pendingReconnections: ConcurrentHashMap, -cachedGameState: GameState, -reconnecting: boolean, -hostDeviceAddress: String
- 内部类: SenderReceiverPair (sender+receiver), PendingMessage, PendingReconnect
- 方法: HOST模式: +startAsHost(String,String), CLIENT模式: +connectAsClient(String,String,String), 设备搜索: +searchDevices(): List<BluetoothDeviceInfo>, +getBondedDevices(): List<BluetoothDeviceInfo>, 消息发送: +sendPlayAction(Play), +sendPassAction(String), +syncGameState(GameState), +sendGameOver(String,String), 状态查询: +isConnected(): boolean, +isHost(): boolean, +getRemotePlayerIds(): List<String>, +getPlayerNamesById(): Map, +readyForGame(), +notifyAiPlayerAdded(String,int), +hasRealClients(): boolean, +getConnectedClientCount(): int, +disconnect()
- 私有方法: HOST: -broadcastPlayerJoined(...), -sendExistingPlayersSnapshot(...), -forwardToOtherClients(BluetoothMessage), 心跳: -startHeartbeat(), -stopHeartbeat(), -updateHeartbeatTimestamp(String), 重连: -startReconnectListener(), -handleChannelDisconnected(String), -handleReconnectMessage(BluetoothMessage), -startClientReconnect(), -handleReconnectAckMessage(BluetoothMessage), ACK: -sendAckFor(BluetoothMessage), -handleAckMessage(BluetoothMessage), -ackRetryCheck(), -needsAck(MessageType): boolean

### 53. BluetoothConnectionManager 类
- 常量: SERVICE_NAME="CardGameBluetoothService", SERVICE_UUID="a5c93a6e-6c0f-4a21-8e6d-9dd3b8a3d7c1"
- 字段: -context: Context, -bluetoothAdapter: BluetoothAdapter, -serverSocket: BluetoothServerSocket, -acceptThread: Thread, -accepting: boolean, -clientConnections: Map<String,ClientConnection>, 单连接兼容: -bluetoothSocket, -inputStream, -outputStream, -connectedDeviceName, -connectedDeviceAddress, -connected: boolean
- 内部类 ClientConnection: +deviceAddress, +deviceName, +socket, +inputStream, +outputStream, +close()
- 方法: 服务端: +startServer(), +waitForNextClient(): String, +waitForAllClients(int): List<String>, +acceptRawConnection(): ClientConnection, +closeServerSocket(), +interruptAccept(), +resumeAccept(), +isServerSocketOpen(): boolean, 客户端: +connectToDevice(String), 设备发现: +discoverJoinableMobileDevices(): List<BluetoothDeviceInfo>, +isBluetoothAvailable(), +isBluetoothEnabled(), +getBondedJoinableDevices(): List<BluetoothDeviceInfo>, 查询: +getConnection(String): ClientConnection, +getInputStream(), +getOutputStream(), +getConnectedDeviceName(), +getConnectedDeviceAddress(), +isConnected(), +getConnectionCount(), 清理: +closeConnection(String), +close(), +closeSocketQuietly(BluetoothSocket)
- 私有方法: -addBondedMobileDevices(Map), -toJoinableDeviceInfo(...): BluetoothDeviceInfo, -isLikelyPhoneOrTablet(...): boolean (过滤非手机/平板设备), -connectWithFallback(BluetoothDevice): BluetoothSocket (安全RFCOMM→不安全RFCOMM降级), -connectSocketWithTimeout(BluetoothSocket,long)

### 54. BluetoothMessage 类
- 字段: -messageId: String, -protocolVersion: int, -messageType: MessageType, -senderPlayerId: String, -receiverPlayerId: String, -timestamp: long, -payloadJson: String, -errorMessage: String, -sequenceNumber: int
- 方法: 所有 getter, -setSequenceNumber(int)

### 55. MessageType 枚举
- 值: INIT_GAME, PLAY_ACTION, PASS_ACTION, GAME_OVER, HEARTBEAT, ERROR, JOIN, JOIN_ACK, PLAYER_JOINED, PLAYER_LEFT, ACK, RECONNECT, RECONNECT_ACK

### 56. BluetoothMessageCodec 类
- 方法: +decode(String): BluetoothMessage, +buildInitGameMessage(...): BluetoothMessage, +buildPlayActionMessage(...): BluetoothMessage, +buildPassActionMessage(...): BluetoothMessage, +buildGameOverMessage(...): BluetoothMessage, +buildHeartbeatMessage(...): BluetoothMessage, +buildJoinMessage(...): BluetoothMessage, +buildJoinAckMessage(...): BluetoothMessage, +buildPlayerJoinedMessage(...): BluetoothMessage, +buildAckMessage(...): BluetoothMessage, +buildReconnectMessage(...): BluetoothMessage, +buildReconnectAckMessage(...): BluetoothMessage, +decodeInitGamePayload(String): InitGamePayload, +decodePlayActionPayload(String): PlayActionPayload, +decodePassActionPayload(String): PassActionPayload, +decodeGameOverPayload(String): GameOverPayload, +decodeJoinPayload(String): JoinPayload, +decodePlayerLeftPayload(String): PlayerLeftPayload, +decodeAckPayload(String): AckPayload, +decodeReconnectPayload(String): ReconnectPayload, +decodeErrorPayload(String): ErrorPayload

### 57. BluetoothSender 类
- 构造: +BluetoothSender(OutputStream, BluetoothMessageCodec)
- 方法: +sendMessage(BluetoothMessage), +isActive(): boolean, +stop()

### 58. BluetoothReceiver 类
- 构造: +BluetoothReceiver(InputStream, BluetoothMessageCodec, BluetoothMessageListener)
- 方法: +startListening(), +stopListening(), -handleRawMessage(String)

### 59. BluetoothMessageListener 接口
- 方法: +onMessageReceived(BluetoothMessage), +onReceiveError(Exception)

### 60. BluetoothEventListener 接口
- 方法: +onConnected(String,String), +onServerReady(), +onPlayerAssigned(String,int), +onPlayerJoined(String,String,int), +onPlayerLeft(String,String), +onAllPlayersReady(), +onDisconnected(String), +onMessageSent(MessageType,String), +onMessageReceived(MessageType,String), +onError(String,Exception), +onGameOver(String,String)

### 61. NetworkGameBridge 类
- 字段: -gameEngine: GameEngine, -messageCodec: BluetoothMessageCodec, -appContext: Context, -eventListener: BluetoothEventListener, -localPlayerId: String, -remotePlayerIds: List<String>
- 方法: +setBluetoothEventListener(BluetoothEventListener), +setPlayerContext(String,List<String>), +handleMessage(BluetoothMessage) (路由到对应handler), -handleInitGame(BluetoothMessage), -handlePlayAction(BluetoothMessage), -handlePassAction(BluetoothMessage), -handleGameOver(BluetoothMessage), -handleErrorMessage(BluetoothMessage), -configurePlayerTypes(), -invokeEngineMethod(String,Class[],Object...): boolean (反射调用GameEngine方法), -applyRoomPropSettings(InitGamePayload)

### 62. BluetoothDeviceInfo 类
- 字段: -deviceName: String, -deviceAddress: String, -bonded: boolean, -deviceType: int, -signalStrength: int, -joinableCandidate: boolean
- 方法: 所有 getter, +isJoinableCandidate(): boolean

---

## 八、network.payload 包 — 消息载荷

### 63. InitGamePayload — 字段: playerHandCards, playerOrder, currentPlayerId, gameState, cardCounts, cardTrackerEnabled, seeThroughEnabled, patternHintEnabled

### 64. PlayActionPayload — 字段: playerId, selectedCardIds, play

### 65. PassActionPayload — 字段: playerId

### 66. GameOverPayload — 字段: winnerId, winnerName

### 67. JoinPayload — 字段: playerName, assignedPlayerId, slotIndex

### 68. PlayerLeftPayload — 字段: playerId, playerName

### 69. AckPayload — 字段: acknowledgedMessageId

### 70. ErrorPayload — 字段: errorMessage, errorCode

### 71. ReconnectPayload — 字段: playerId, playerName

---

## 九、dto 包 — 视图数据

### 72. GameViewData 类
- 字段: -currentPlayerId, -currentPlayerName, -players: List<PlayerViewData>, -selectedCardIds, -myHandCards, -lastPlayText, -lastPlayCards, -gameOver: boolean, -winnerName, -playerLastPlayCards: Map<String,List<String>>, -allPlayedCards: List<Card>
- 方法: 所有 getter

### 73. PlayerViewData 类
- 字段: -playerId, -playerName, -remainingCardCount: int, -currentTurn: boolean, -passed: boolean, -isHuman: boolean
- 方法: 所有 getter

### 74. PlayResult 类
- 字段: -success: boolean, -message: String, -gameState: GameState
- 方法: 所有 getter/setter

### 75. PassResult 类
- 字段: -success: boolean, -message: String, -gameState: GameState
- 方法: 所有 getter/setter

### 76. ValidationResult 类 (rule包内PlayValidator的内部类)
- 字段: +valid: boolean, +reason: String

### 77. BluetoothViewData 类
- 字段: bluetoothAvailable, bluetoothEnabled, connected, connecting, hosting, role, localPlayerId, remotePlayerId, assignedPlayerId, assignedSlotIndex, connectedDeviceName, connectedDeviceAddress, statusText, errorMessage, devices: List<BluetoothDeviceViewData>, connectedDevices列表, lastSentMessageType, lastSentSummary, lastReceivedMessageType, lastReceivedSummary
- 方法: 所有 getter/setter, +clearSessionState(), +clearErrorMessage(), +clearConnectedDevices(), +addConnectedDevice(...), +removeConnectedDeviceByPlayerId(String)

### 78. BluetoothDeviceViewData 类
- 字段: deviceName, deviceAddress, bonded, connecting, statusHint

---

## 十、llm 包 — 大语言模型集成

### 79. VivoLLMClient 类
- 常量: API_URL="https://api-ai.vivo.com.cn/v1/chat/completions", APP_KEY
- 字段: -client: OkHttpClient, -gson: Gson
- 方法: +chat(List<ChatMessage>): String (调用Volc-DeepSeek-V3.2模型, reasoningEffort=minimal, stream=false)

### 80. LLMAnalyzer 类
- 方法: +summarizeOpponentStyle(String): String (将出牌历史发送给LLM分析风格)

### 81. OpponentStyleAnalyzer 类
- 字段: -executor: ExecutorService, -llmAnalyzer: LLMAnalyzer
- 方法: +analyzeAndUpdate(String, CardTracker, AIPlayerProfile) (异步分析对手风格并更新profile)

### 82. ChatMessage 类 — 字段: role, content

### 83. DeepSeekRequest 类 — 字段: model, messages, reasoningEffort, stream

### 84. DeepSeekResponse 类 — 字段: choices[]

---

## 十一、util 包 — 工具类

### 85. CardTracker 类
- 字段: -playedCards: Set<Card>, -playedBy: Map<Card,String>, -opponentHistory: Map<String,List<String>>, -rankPlayedCount: Map<Rank,Integer>, -suitPlayedCount: Map<Suit,Integer>
- 方法: +onCardPlayed(Card,String), +onCardsPlayed(List<Card>,String), +recordPlay(String,String), +getHistorySummary(String): String, +getRemainingCards(List<Card>): List<Card>, +isPlayed(Card): boolean, +getPlayedCards(): Set<Card>, +getPlayedBy(Card): String, +reset(), +getPlayedCount(): int, +getRemainingCountByRank(Rank): int, +getRemainingCountBySuit(Suit): int, +getRemainingProbability(Card): double, +getHighProbabilityCards(List<Card>,int): List<Card>, +getDistributionSummary(): String

### 86. CrossGameMemoryManager 类
- 方法: +saveHumanStyleProfile(String, HumanStyleProfile), +loadHumanStyleProfile(String): HumanStyleProfile

### 87. CardComparator 类
### 88. HermesLog 类 — 日志收集
### 89. Logger 类 — 日志输出
### 90. BluetoothPermissionHelper 类 — 蓝牙权限检查

---

## 十二、ui 包 — Android界面

### 91. MainActivity — 主菜单页面
### 92. GameActivity — 游戏主界面 (持有GameActionHandler, 处理出牌/过牌按钮, 倒计时UI, 游戏结束对话框, AI提示显示, 道具面板)
### 93. RoomLobbyActivity — 蓝牙房间大厅
### 94. RoomSelectionActivity — 房间选择页面
### 95. RoomSettingsActivity — 房间设置页面
### 96. SearchDeviceActivity — 蓝牙设备搜索页面
### 97. RulesActivity — 规则查看页面
### 98. CardAdapter — 手牌RecyclerView适配器
### 99. DeviceAdapter — 蓝牙设备列表适配器
### 100. RankingAdapter — 排名列表适配器

### 101. CardGameApplication — Application入口 (静态持有GameEngine, GameActionHandler, BluetoothActionHandler)

---

## 类之间关系汇总

### 继承关系 (extends)
- AggressiveAIDecisionStrategy → GreedyAIDecisionStrategy
- DefensiveAIDecisionStrategy → GreedyAIDecisionStrategy
- NormalAIDecisionStrategy → GreedyAIDecisionStrategy
- CardPlayedEvent → GameEvent
- PlayerPassedEvent → GameEvent
- TurnChangedEvent → GameEvent
- GameOverEvent → GameEvent

### 实现关系 (implements)
- ConfigurableRuleEngine → RuleEngine
- GreedyAIDecisionStrategy → AIDecisionStrategy
- MonteCarloAIDecisionStrategy → AIDecisionStrategy
- AdaptiveAIDecisionStrategy → AIDecisionStrategy
- GameController → GameActionHandler
- BluetoothController → BluetoothActionHandler, BluetoothEventListener
- BluetoothEventRelay → GameEventListener
- AIEventListener → GameEventListener
- BluetoothGateway → MultiplayerGateway, BluetoothMessageListener
- HumanStyleProfile → Serializable

### 依赖关系 (uses)
- GameEngine → RuleEngine, PlayValidator, PatternRecognizer, DealManager, TurnManager, SettlementManager
- GameController → GameEngine, PlayValidator, AIDecisionStrategy, CardTracker, CrossGameMemoryManager, HumanStyleAnalyzer
- PatternRecognizer → RuleConfig
- PlayValidator → PatternRecognizer, RuleConfig
- GameViewData → PlayerViewData
- BluetoothGateway → BluetoothConnectionManager, BluetoothMessageCodec, NetworkGameBridge, BluetoothSender, BluetoothReceiver
- NetworkGameBridge → GameEngine, BluetoothMessageCodec
- BluetoothController → BluetoothGateway, BluetoothEventRelay

### 聚合/组合关系
- GameState 包含 List<Player> (聚合)
- Player 包含 List<Card> (聚合)
- Play 包含 List<Card> (聚合)
- GameEngine 持有 DealManager, TurnManager, SettlementManager (组合)
- BluetoothGateway 持有 BluetoothConnectionManager, BluetoothMessageCodec, NetworkGameBridge (组合)

## 输出要求
1. 使用 PlantUML 语法生成
2. 按包名分组 (用 package 或 namespace 标注)
3. 所有类、接口、枚举完整呈现
4. 继承用空心三角实线, 实现用空心三角虚线
5. 关联用实线箭头, 依赖用虚线箭头
6. 聚合用空心菱形, 组合用实心菱形
7. 区分 abstract class (斜体), interface (<<interface>>), enum (<<enumeration>>)
8. 字段可见性: -private, +public, #protected
```

---

## 图三：对象图 (Object Diagram)

### 提示词

```
请根据以下信息，生成锄大地卡牌游戏在"游戏开局发牌后第一轮出牌前"时刻的对象图（Object Diagram），使用 PlantUML 语法。

## 场景描述
南方规则（Southern Rules）4人对局刚刚完成发牌，还没开始出牌。52张牌已通过DealManager均分，每人13张。P1持有方块3（♦3），被DealManager识别为首发玩家（openingPlayer）。当前轮到P1出牌。

## 需要呈现的具体对象及其属性值

### 1. gameEngine : GameEngine
- ruleConfig = RuleConfig.SOUTHERN
- allPlayedCards = [] (空列表)

### 2. gameState : GameState
- openingTurn = true
- gameOver = false
- winnerId = null
- lastWinnerId = null
- currentPlayerId = "P1"
- lastPlay = null
- consecutivePassCount = 0
- lastPlayByPlayer = {} (空Map)
- allPlayedCards = []

### 3-6. 四位玩家对象
#### player1 : Player
- playerId = "P1"
- playerName = "Alice"
- type = HUMAN
- passed = false
- consecutiveNoPlayCount = 0
- handCards = [♦3, ♣4, ♣7, ♦8, ♣9, ♦10, ♥J, ♠J, ♥Q, ♠Q, ♣K, ♥A, ♠2]   (13张示例手牌)

#### player2 : Player
- playerId = "P2"
- playerName = "Bob"
- type = AI
- passed = false
- consecutiveNoPlayCount = 0
- handCards = [♠3, ♥4, ♦5, ♠7, ♥8, ♠9, ♥10, ♣J, ♦J, ♦Q, ♠K, ♣A, ♥2]   (13张)

#### player3 : Player
- playerId = "P3"
- playerName = "Cindy"
- type = AI
- passed = false
- consecutiveNoPlayCount = 0
- handCards = [♣3, ♦4, ♠4, ♣5, ♥6, ♣8, ♦9, ♥9, ♣10, ♠Q, ♥K, ♦A, ♦2]   (13张)

#### player4 : Player
- playerId = "P4"
- playerName = "David"
- type = AI
- passed = false
- consecutiveNoPlayCount = 0
- handCards = [♥3, ♠5, ♦6, ♣6, ♠6, ♥7, ♦7, ♠8, ♣Q, ♦K, ♠A, ♣2, ♠2]   (13张)

### 7. dealManager : DealManager
- (无显著实例字段)

### 8. turnManager : TurnManager
- (无显著实例字段)

### 9. settlementManager : SettlementManager
- (无显著实例字段)

### 10. ruleEngine : ConfigurableRuleEngine
- 持有的config = RuleConfig.SOUTHERN
- 持有的recognizer : PatternRecognizer
- 持有的validator : PlayValidator

### 11. ruleConfig : RuleConfig (SOUTHERN)
- requiredOpeningRank = THREE
- requiredOpeningSuit = DIAMONDS
- passResetThreshold = 3
- allowedPatterns = [SINGLE, PAIR, TRIPLE, QUADRUPLE, STRAIGHT, FLUSH, FULL_HOUSE, IRON_BRANCH, STRAIGHT_FLUSH]
- rankWeights: THREE=0, FOUR=1, FIVE=2, SIX=3, SEVEN=4, EIGHT=5, NINE=6, TEN=7, JACK=8, QUEEN=9, KING=10, ACE=11, TWO=12
- suitWeights: DIAMONDS=0, CLUBS=1, HEARTS=2, SPADES=3
- fiveCardPriority: STRAIGHT=1, FLUSH=2, FULL_HOUSE=3, IRON_BRANCH=4, STRAIGHT_FLUSH=5

## 对象之间的链接关系
1. gameEngine 链接到 gameState (持有引用)
2. gameEngine 链接到 dealManager, turnManager, settlementManager (组合)
3. gameEngine 链接到 ruleEngine (持有引用)
4. gameEngine 链接到 ruleConfig (持有引用)
5. gameState 链接到 player1, player2, player3, player4 (players列表)
6. gameState.currentPlayerId 指向 player1
7. ruleEngine 链接到 ruleConfig (通过recognizer和validator)
8. player1 链接到其手牌中的13张 Card 对象
9. player1.handCards[0] 是一张 cardId="C_1", suit=DIAMONDS, rank=THREE 的牌

## 示例牌对象（展示前3张）
### card_C1 : Card
- cardId = "C_1"
- suit = DIAMONDS
- rank = THREE

### card_C2 : Card
- cardId = "C_2"
- suit = CLUBS
- rank = FOUR

### card_C3 : Card
- cardId = "C_3"
- suit = CLUBS
- rank = SEVEN

## 输出要求
1. 使用 PlantUML object 语法
2. 对象命名格式：objectName : ClassName
3. 对象内字段按"字段名 = 值"格式列出
4. 链接关系标注名称（如 "持有", "players[0]", "handCards[0]"）
5. 用不同的视觉分组区分：GameEngine核心组、Player组、规则引擎组
6. 颜色或框区分关键对象（如首发玩家P1高亮）
```

---

## 图四：时序图 (Sequence Diagram)

### 提示词

```
请根据以下完整流程信息，生成锄大地卡牌游戏的**本地出牌完整流程**的时序图（Sequence Diagram），使用 PlantUML 语法。

## 场景
南方规则，4人游戏中，轮到人类玩家P1出牌，P1选中2张牌（对子8）点击"出牌"按钮。这是游戏的第三轮（非首轮），上家P4出了一张单牌♦K。

## 参与者及其生命线
1. **Player (Human)** — 人类玩家
2. **GameActivity** — Android UI界面
3. **GameController** — 游戏控制器
4. **GameEngine** — 游戏核心引擎
5. **RuleEngine / ConfigurableRuleEngine** — 规则引擎
6. **PatternRecognizer** — 牌型识别器
7. **PlayValidator** — 出牌校验器
8. **SettlementManager** — 结算管理器
9. **TurnManager** — 回合管理器
10. **EventBus** — 全局事件总线
11. **BluetoothEventRelay** — 蓝牙事件中继（可选，蓝牙模式时）
12. **BluetoothGateway** — 蓝牙网关（可选）
13. **GameState / Player** — 领域模型（被动对象）
14. **AIEventListener** — AI事件监听器

## 完整调用流程（按时间线）

### 阶段1：UI触发
1. Player 点击屏幕上的"出牌"按钮
2. GameActivity.btn_play.onClick() 触发
3. GameActivity 调用 gameActionHandler.submitPlay(["♣8", "♠8"]) (传入选中的牌ID列表)

### 阶段2：Controller预处理
4. GameController.submitPlay(["♣8", "♠8"]) 开始执行
5. GameController 从 GameEngine 获取 GameState: getGameState()
6. GameController 从 GameState 获取 currentPlayer (getCurrentPlayer())
7. GameController 检查 currentPlayer.getPlayerId() 是否等于 myPlayerId("P1") → true
8. GameController 通过 Player.findCardsByIds() 将UI字符串转换为Card对象列表
9. 转换结果：selectedCards = [Card(id="C_16", suit=CLUBS, rank=EIGHT), Card(id="C_45", suit=SPADES, rank=EIGHT)]

### 阶段3：引擎出牌
10. GameController 调用 gameEngine.playCards("P1", ["C_16", "C_45"])
11. GameEngine 检查 playerId=="P1" == currentPlayerId → true
12. GameEngine 检查 selectedCardIds 非空 → true
13. GameEngine 获取上家出牌：getLastPlayCards() → [Card(suit=DIAMONDS, rank=KING)]
14. GameEngine 判断 isFirstRound() → false (非首轮)
15. GameEngine 判断 isFirstTurnOfCurrentRound() → false (有上家)

### 阶段4：规则校验
16. GameEngine 调用 ruleEngine.validatePlay(selectedCards, lastPlayCards, false, false)
17. RuleEngine/PlayValidator.validatePlay() 开始执行
18. PlayValidator 先检查非Pass (cards非空) → OK
19. PlayValidator 调用 recognizer.recognizePattern(selectedCards) 识别牌型
20. PatternRecognizer 分析：2张牌，同点数(EIGHT) → 识别为 PAIR, compareValue = (rankWeight 3)*10 + suitWeight 3 = 33
21. PlayValidator 检查非首轮无需包含方块3 → OK
22. PlayValidator 获取上家牌型: recognizer.recognizePattern(lastPlayCards) → SINGLE
23. PlayValidator 检测牌型不匹配 (PAIR vs SINGLE) → 校验失败
24. 返回 ValidationResult(false, "必须出与上家相同的牌型（当前=PAIR, 上家=SINGLE）")

### 阶段4b：重新选择——改为出单张
25. 人类玩家重新选择只出单张 ♥A
26. 同上的步骤1-23重复，但此时 selectedCards = [Card(suit=HEARTS, rank=ACE)]
27. PatternRecognizer 识别为 SINGLE, compareValue = 11*10+2 = 112
28. PlayValidator 识别上家也是 SINGLE(♦K = 10*10+0 = 100)
29. 比较 compareValue: 112 > 100 → true
30. 返回 ValidationResult(true, "合法")

### 阶段5：状态更新
31. GameEngine 创建 Play对象：new Play("P1", selectedCards, CardPattern.SINGLE)
32. GameEngine 从 Player(P1).handCards 中移除选中的牌: removeIf
33. GameEngine 将打出的牌加入 allPlayedCards
34. GameEngine 更新 GameState: setLastPlay(currentPlay)
35. GameEngine 更新 Player(P1): setPassed(false), setLastWinnerId("P1")
36. GameEngine 重置: resetConsecutivePassCount()
37. GameEngine 检查 openingTurn → false (已不是)
38. GameEngine 调用 updateLastPlayByPlayer("P1", selectedCards)

### 阶段6：结算检查
39. GameEngine 调用 settlementManager.checkAndSettle(gameState)
40. SettlementManager 遍历所有玩家的 handCards，检查是否有人空手
41. 假设无人空手 → gameOver 保持 false

### 阶段7：回合切换
42. GameEngine 调用 turnManager.switchPlayer(gameState) (因为游戏未结束)
43. TurnManager 查找当前玩家索引 (P1=0) → 下一玩家 P2 (索引1)
44. TurnManager 设置 gameState.setCurrentPlayerId("P2")
45. TurnManager 设置 gameState.setOpeningTurn(false)
46. TurnManager 发布事件: EventBus.getInstance().post(new TurnChangedEvent("P2", "PLAY"))

### 阶段8：事件发布与处理
47. GameEngine 发布出牌事件: EventBus.getInstance().post(new CardPlayedEvent("P1", ["C_40"]))
48. EventBus 通知所有已注册的 GameEventListener:
   a. AIEventListener.onEvent() — 检查收到的是 CardPlayedEvent，但当前玩家不是AI，跳过
   b. BluetoothEventRelay.onEvent() — 检查玩家类型非REMOTE，创建Play对象调用 gateway.sendPlayAction(lastPlay)
49. BluetoothEventRelay 调用 gateway.syncGameState(state) (Host模式下同步)
50. EventBus 通知 TurnChangedEvent：
   a. AIEventListener.onEvent() — 检测到新玩家P2是AI类型，延迟2200ms后调用 strategy.decidePlay(P2, gameState)

### 阶段9：返回结果与UI刷新
51. GameEngine 返回 createPlayResult(true, "PLAY_OK", gameState) 给 GameController
52. GameController 调用 recordPlayToTracker("P1", cards) 记录到记牌器
53. GameController 记录人类出牌动作到 currentHumanActions 列表
54. GameController 清空 selectedCardIds
55. GameController 重置玩家 consecutiveNoPlayCount
56. GameController 取消倒计时: cancelCountdown(player)
57. GameController 通过 Handler.postDelayed 延迟100ms调用 triggerNextAction()
58. GameController 返回 PlayResult(success=true, message="PLAY_OK", gameState) 给 GameActivity
59. GameActivity 收到成功结果，显示 Toast "PLAY_OK"
60. GameActivity 调用 refreshUI() 刷新界面（更新手牌、牌数、出牌区域等）

## 输出要求
1. 使用 PlantUML 时序图语法
2. 每条消息用序号标注步骤
3. 区分同步调用（实线箭头→）和异步调用（开放箭头-->> ）
4. 返回值用虚线箭头（-->）
5. 使用 alt/else 表示条件分支（如牌型校验成功/失败）
6. 用 activate/deactivate 标注对象激活期
7. 用 note 标注关键决策点
8. 参与者在顶部水平排列
9. 按逻辑分组用 box 区分：UI层、控制器层、引擎层、规则层、事件层、网络层
```

---

## 图五：通信图 (Communication Diagram)

### 提示词

```
请根据以下信息，生成锄大地卡牌游戏的**AI自动出牌决策流程**的通信图（Communication Diagram），使用 PlantUML 语法。

## 场景
游戏进行到中盘阶段，轮到AI玩家P2出牌。上家P1刚出了一张单牌♠5。P2手牌剩余8张，处于EARLY到MID过渡阶段。P2使用MonteCarloAIDecisionStrategy决策出牌。

## 通信图中的对象节点及其角色

### 节点1：eventBus : EventBus (单例)
- 发布 TurnChangedEvent("P2", "PLAY") 触发AI决策

### 节点2：aiListener : AIEventListener
- 收到 TurnChangedEvent，检测P2类型为AI，isHost=true
- delay 2200ms 后开始决策

### 节点3：gameState : GameState
- 提供：getCurrentPlayer()=P2, getLastPlay()=单牌♠5, isOpeningTurn()=false
- 提供：getPlayers() 列表及各玩家手牌数

### 节点4：aiPlayer : Player (P2)
- 提供：getHandCards()=8张牌, getType()=AI
- 接收：decidePlay的返回值（出牌列表）

### 节点5：strategy : MonteCarloAIDecisionStrategy
- 核心决策对象，协调所有子模块
- 持有 candidateGenerator, opponentHandSampler, monteCarloSimulator, phaseManager, cardTracker, ruleEngine, profile

### 节点6：candidateGenerator : CandidateGenerator
- 从手牌生成所有候选出牌动作（枚举单张/对子/三张/炸弹/五张牌型）
- 过滤出能压过♠5的合法出牌
- 保留TOP_K_CANDIDATES(4)个候选

### 节点7：opponentHandSampler : OpponentHandSampler
- 根据 cardTracker 的已出牌信息
- 利用剩余牌池随机采样其他3个对手的手牌
- 生成 NUM_SAMPLES(10) 个 World 对象

### 节点8：monteCarloSimulator : MonteCarloSimulator
- 对每个候选动作在每个World中模拟游戏到结束
- 使用 opponentProfiles 调整对手行为
- 返回每个候选的期望得分

### 节点9：phaseManager : PhaseManager
- 判断当前游戏阶段 (getCurrentPhase)
- 对蒙特卡洛评分进行阶段调整 (adjustScore)

### 节点10：ruleEngine : ConfigurableRuleEngine
- 提供牌型识别(recognizePattern)和出牌校验(validatePlay)

### 节点11：cardTracker : CardTracker
- 提供已出牌历史(playedCards)
- 提供牌的分布统计(rankPlayedCount, suitPlayedCount)

### 节点12：gameEngine : GameEngine
- 接收最终决策结果，执行 playCards 或 passTurn

### 节点13：controller : GameController
- 协调AI出牌和UI更新

## 通信链路（编号的消息传递序列）

1. EventBus → AIEventListener: post(TurnChangedEvent("P2"))
2. AIEventListener → GameState: 获取 getCurrentPlayer() 确认仍是P2
3. AIEventListener → strategy: decidePlay(aiPlayer, gameState)
4. strategy → gameState: 获取 getLastPlay(), isOpeningTurn(), getPlayers()
5. strategy → [内部阶段检测]
   - 5a. 残局强制压牌检查：getMinOpponentHandSize() ≤ 2 ?
   - 5b. 消极游戏检测：consecutivePassRounds ≥ 2 ?
   - 5c. 兜底逻辑：consecutiveFailCount ≥ 2 ?
6. strategy → candidateGenerator: generate(hand, lastPlay, isFirstRound, isFirstTurn)
7. candidateGenerator → ruleEngine: recognizePattern() 对所有候选
8. candidateGenerator → ruleEngine: validatePlay() 过滤合法候选
9. candidateGenerator → strategy: 返回 topK 候选动作列表
10. strategy → cardTracker: updateCardTracker(gameState) 更新记牌器
11. strategy → opponentHandSampler: sampleWorlds(aiPlayer, gameState, cardTracker, dynamicSamples)
12. opponentHandSampler → cardTracker: getPlayedCards() 获取已出牌
13. opponentHandSampler → strategy: 返回 List<World> 采样世界
14. strategy → monteCarloSimulator: evaluate(每个candidate, aiPlayer, gameState, worlds)
15. monteCarloSimulator → [内部快速模拟]: 对每个World用greedyPolicy模拟到终局
16. monteCarloSimulator → strategy: 返回 scores Map<Play, Double>
17. strategy → phaseManager: adjustScore(rawScore, candidate, phase, aiPlayer, gameState, handAfterPlay)
18. phaseManager → strategy: 返回 adjustedScore
19. strategy → [评分决策逻辑]
    - 张数奖励: (cardCount-1)*50
    - 组合牌奖励: +20
    - 对子压对子奖励: +80
    - 组合牌压单张奖励: +60~80
    - 紧急模式调整
    - 自适应因子调整: applyAdaptiveFactors()
    - 选择最高分候选为 bestPlay
20. strategy → [最终检查]: ruleEngine.validatePlay(bestPlay, lastPlay, isFirstRound, isFirstTurn)
21. strategy → AIEventListener: 返回 List<Card> (选中的出牌) 或 null (过牌)
22. AIEventListener → controller: aiPlayCards(cards) 或 gameEngine.passTurn(playerId)
23. controller → gameEngine: playCards("P2", cardIds) 或 passTurn("P2")
24. gameEngine → [执行状态更新/结算/回合切换]
25. gameEngine → EventBus: post(CardPlayedEvent 或 PlayerPassedEvent)
26. gameEngine → EventBus: post(TurnChangedEvent("P3"))
27. AIEventListener → controller: updateAiHint(hint) 更新AI提示到UI

## 输出要求
1. 使用 PlantUML 通信图语法（或使用 object + 链接标注消息）
2. 对象节点用圆角矩形标注
3. 链接上标注消息编号和方向
4. 消息编号与上述步骤一致
5. 对复杂的内部决策（如蒙特卡洛模拟）可用 note 补充说明
6. 用不同颜色区分：决策核心(红色)、数据提供(蓝色)、外部执行(绿色)
```

---

## 图六：状态图 (State Machine Diagram)

### 提示词

```
请根据以下完整信息，生成锄大地卡牌游戏的**游戏全局状态机图**（State Machine Diagram），使用 PlantUML 语法。描述从游戏创建到游戏结束的完整生命周期状态转换。

## 状态机概述
本状态机描述一局锄大地游戏的全局生命周期，从游戏初始化到游戏结束的全过程。

## 顶层复合状态

### 【复合状态1：游戏准备 (Game Preparation)】

#### 子状态 S1.1: 规则配置 (Rule Configuration)
- 进入动作：设置规则类型（南方/北方）
- 内部行为：配置道具开关（记牌器/透视/牌型提示）、配置AI难度（EASY/MEDIUM/HARD/ADAPTIVE）、配置AI策略风格（NORMAL/AGGRESSIVE/DEFENSIVE）

#### 子状态 S1.2: 等待玩家就绪 (Waiting for Players)
- 单机模式：直接创建4个玩家(P1=HUMAN, P2/P3/P4=AI)
- 蓝牙模式(HOST)：等待3个CLIENT通过蓝牙加入，分配P2/P3/P4身份
- 蓝牙模式(CLIENT)：等待HOST分配身份和同步游戏状态
- 触发事件：所有4个玩家就绪 → 进入"发牌"状态

### 【复合状态2：游戏进行中 (Game In Progress)】

#### 子状态 S2.1: 发牌 (Dealing)
- 进入动作：DealManager.generateFullDeck() 生成52张牌
- 行为：Collections.shuffle(deck) 洗牌 → 按顺序分配给4个玩家各13张
- 行为：identifyOpeningPlayer() 找出持有♦3的玩家
- 退出动作：设置 currentPlayerId = openingPlayer, openingTurn = true
- 自动转换 → S2.2 "等待出牌"

#### 子状态 S2.2: 等待出牌 (Waiting for Play)
- 此状态下等待当前玩家的操作
- 当前玩家为 HUMAN 时：
  - 启动倒计时检测：checkAndStartNoPlayCountdown()
  - 如果玩家无任何合法牌可出：3秒倒计时后自动forcePass
  - 如果玩家有合法牌：等待玩家点击"出牌"或"过牌"
- 当前玩家为 AI 时：
  - AIEventListener 在 TurnChangedEvent 后延迟2.2秒自动决策
  - 调用 strategy.decidePlay() 获取出牌列表
- 当前玩家为 REMOTE 时：
  - 等待蓝牙网络收到远程玩家的出牌/过牌消息
- 自转换：如果倒计时到期且无合法牌 → 自动触发 passTurn()

#### 子状态 S2.3: 处理出牌 (Processing Play)
- 触发：当前玩家提交出牌 (submitPlay / aiPlayCards / executeRemotePlay)
- 行为序列：
  1. 规则校验 (PlayValidator.validatePlay)
     - 首轮首出必须包含♦3 (南方规则)
     - 牌型必须合法 (recognizePattern)
     - 如有上家必须牌型相同且更大 (或五张牌型跨类型压制)
  2. 校验失败 → 返回错误消息，留在 S2.2 "等待出牌"
  3. 校验通过 → 继续
  4. 从玩家手牌移除打出的牌
  5. 更新 GameState: setLastPlay(), setLastWinnerId(), resetConsecutivePassCount()
  6. 记录到 allPlayedCards
  7. 发布 CardPlayedEvent 到 EventBus
  8. 蓝牙模式下：BluetoothEventRelay 发送 PlayAction + syncGameState
  9. 结算检查 (SettlementManager.checkAndSettle)
- 转换：[玩家手牌为空] → S3 "游戏结束"
- 转换：[玩家手牌非空] → S2.4 "回合切换"

#### 子状态 S2.4: 处理过牌 (Processing Pass)
- 触发：当前玩家提交过牌 (passTurn / executeRemotePass)
- 前置条件检查：
  - 首轮首出不能Pass（isOpeningTurn && isFirstTurn → 拒绝）
  - 新回合起始玩家不能Pass（lastPlay==null → 拒绝）
- 行为序列：
  1. 设置 player.passed = true
  2. 更新 GameState: updateLastPlayByPlayer(playerId, null)
  3. incrementConsecutivePassCount()
  4. 发布 PlayerPassedEvent
  5. 蓝牙模式下：发送 PassAction + syncGameState
  6. 检查 consecutivePassCount：
- 转换条件 [consecutivePassCount < 3]：
  → S2.4 "回合切换" (普通过牌切换)
- 转换条件 [consecutivePassCount >= 3]：
  行为：setLastPlay(null), clearAllPassStatus(), clearAllLastPlayRecords(), resetConsecutivePassCount()
  如果 lastWinnerId 非空且非首轮：currentPlayerId = lastWinnerId
  否则：正常 switchPlayer
  → S2.2 "等待出牌" (清空桌面，新回合开始)

#### 子状态 S2.5: 回合切换 (Switching Turn)
- 进入动作：TurnManager.switchPlayer(gameState)
- 行为：找到当前玩家索引 → (currentIndex+1)%4 → 设置下一个玩家
- 发布 TurnChangedEvent(newPlayerId, "PLAY")
- 设置 openingTurn = false
- 转换：
  - nextPlayer 非空 → S2.2 "等待出牌"
  - nextPlayer 已 passed (全部其他玩家都Pass场景) → S2.4 "处理过牌"

#### 子状态 S2.6: 新回合开始 (New Round Start)
- 触发：连续3人过牌，桌面清空
- 上一轮的赢家（lastWinnerId）获得新一轮的出牌权
- lastPlay 被清空，所有玩家 pass 状态被清除
- lastPlayByPlayer 记录被清空
- 转换 → S2.2 "等待出牌"

### 【复合状态3：游戏结束 (Game Over)】

#### 子状态 S3.1: 判定胜者 (Determine Winner)
- 进入：SettlementManager 发现某玩家手牌为空
- 行为：setGameOver(true), setWinnerId(winnerPlayerId)
- 发布 GameOverEvent(winnerId)

#### 子状态 S3.2: 结算展示 (Settlement Display)
- 行为：计算所有玩家剩余牌数排名
- 蓝牙模式(HOST)：sendGameOver(winnerId, winnerName) 广播
- UI展示：弹出游戏结束对话框，显示排名
- 道具记牌器重置

#### 子状态 S3.3: 游戏终止 (Terminated)
- 进入：用户关闭游戏结束对话框或选择新游戏
- 行为：cleanup() 清理AI事件监听器、取消倒计时、清理资源
- 转换 → S1.1 "规则配置" (重新开始)

## 状态转换汇总表

| 源状态 | 事件/条件 | 目标状态 |
|--------|----------|---------|
| S1.1 规则配置 | 用户点击"开始游戏" | S1.2 等待玩家就绪 |
| S1.2 等待玩家就绪 | 4人全部就绪 (蓝牙: 3个CLIENT到齐; 单机: 直接) | S2.1 发牌 |
| S2.1 发牌 | 发牌完成 + 找到openingPlayer | S2.2 等待出牌 |
| S2.2 等待出牌 | 玩家提交出牌(校验通过) | S2.3 处理出牌 |
| S2.2 等待出牌 | 玩家提交过牌(非首轮首出) | S2.4 处理过牌 |
| S2.2 等待出牌 | 倒计时超时(无合法牌) | S2.4 处理过牌(auto forcePass) |
| S2.3 处理出牌 | [玩家手牌为空] | S3.1 判定胜者 |
| S2.3 处理出牌 | [玩家手牌非空] | S2.5 回合切换 |
| S2.4 处理过牌 | [consecutivePassCount < 3] | S2.5 回合切换 |
| S2.4 处理过牌 | [consecutivePassCount >= 3] | S2.6 新回合开始 |
| S2.5 回合切换 | nextPlayer存在 | S2.2 等待出牌 |
| S2.6 新回合开始 | lastWinnerId有出牌权 | S2.2 等待出牌 |
| S3.1 判定胜者 | 胜者已确定 | S3.2 结算展示 |
| S3.2 结算展示 | 用户点击"再来一局" | S1.1 规则配置 |

## 输出要求
1. 使用 PlantUML 状态图语法 (state 或 state-diagram)
2. 使用 state 复合状态嵌套
3. 每个转换标注 [guard条件] / 触发事件
4. 进入/退出动作使用 entry/exit 标注
5. 内部行为使用 do/ 标注
6. 使用颜色区分三大顶层状态
7. 用 <<choice>> 标注条件分支节点
8. 初始状态用 [*] --> 表示, 终止状态用 --> [*] 表示
9. 蓝牙/单机模式的分支用条件判断标注
```

---

## 图七：活动图 (Activity Diagram)

### 提示词

```
请根据以下完整信息，生成锄大地卡牌游戏的**完整出牌活动图**（Activity Diagram），使用 PlantUML 语法。涵盖从玩家轮到出牌到下一玩家回合的完整活动流。

## 活动名称
锄大地出牌活动流程 (Play Card Activity Flow)

## 活动图分区（泳道 Swimlanes）

### 泳道1：Human Player (人类玩家)
### 泳道2：GameActivity (UI)
### 泳道3：GameController
### 泳道4：GameEngine
### 泳道5：RuleEngine (规则引擎)
### 泳道6：AI Player (AI玩家)
### 泳道7：EventBus (事件总线)
### 泳道8：BluetoothGateway (蓝牙网关)

## 完整活动流程（带分支和并发）

### 【开始】→ 轮到某玩家出牌

#### 节点1：[GameEngine] 设置 currentPlayerId
- 活动：确定当前轮到哪个玩家

#### 节点2：{判断玩家类型} (决策节点)
- 分支 HUMAN：→ 节点3
- 分支 AI：→ 节点20
- 分支 REMOTE：→ 节点30

---

## 分支A：人类玩家出牌流程

### 节点3：[GameController] checkAndStartNoPlayCountdown()
- 活动：调用 PlayValidator.hasAnyValidPlay() 检查玩家是否有合法牌可出
- 输出：hasValidPlay (boolean)

### 节点4：{是否有合法牌可出？} (决策节点)
- [有合法牌] → 节点5
- [无合法牌] → 节点15

### 节点5：[GameActivity] 等待玩家操作
- 活动：显示出牌按钮和过牌按钮
- 玩家可以：
  - 点击手牌 CardView 选中/取消选中 (toggleCardSelection)
  - 查看牌型提示（道具开启时）
  - 查看记牌器信息

### 节点6：[Human Player] 选择操作
- 决策：{玩家的操作}
  - 点击 [出牌按钮] → 节点7
  - 点击 [过牌按钮] → 节点14

### 节点7：[GameActivity] 收集选中的牌ID
- 活动：从 selectedCardIds 列表获取选中的牌ID字符串列表
- 活动：btn_play.onClick() → gameActionHandler.submitPlay(selectedCardIds)

### 节点8：[GameController] submitPlay()
- 活动：获取 GameState 和 currentPlayer
- 活动：验证是否为当前玩家的回合
- 活动：将UI字符串通过 Player.findCardsByIds 转换为 Card 对象列表
- 活动：调用 gameEngine.playCards(playerId, cardIds)

### 节点9：[GameEngine] playCards()
- 活动：获取上家出牌 getLastPlayCards()
- 活动：判断 isFirstRound() 和 isFirstTurnOfCurrentRound()
- 活动：调用 ruleEngine.validatePlay(selectedCards, lastPlayCards, isFirstRound, isFirstTurn)

### 节点10：[RuleEngine] 校验出牌合法性
- 子活动1：PatternRecognizer.recognizePattern(selectedCards) → 识别牌型和比较值
- 子活动2：{牌型是否合法？}
  - [牌型为INVALID] → 返回 ValidationResult(false, "不支持的牌型")
  - [牌型合法] → 继续
- 子活动3：{是否首轮首出？}
  - [是首轮首出且不包含♦3] → 返回 false "首轮必须出♦3"
  - [南方规则无限制或包含必出牌] → 继续
- 子活动4：{是否有上家出牌？}
  - [无上家(本轮首出)] → 返回 true "合法"
  - [有上家] → 继续
- 子活动5：比较牌型
  - {当前牌型 vs 上家牌型}
  - [完全相同且 compareValue 更大] → true "合法"
  - [都是五张牌型且当前优先级更高] → true "高级牌型压制"
  - [否则] → false "必须出相同牌型且更大"

### 节点11：{校验结果？} (决策节点)
- [校验失败] → 返回 Error消息给UI，留在节点5
- [校验通过] → 节点12

### 节点12：[GameEngine] 更新游戏状态
- 活动：创建 Play 对象
- 活动：从 Player.handCards 移除打出的牌
- 活动：记录到 allPlayedCards
- 活动：setLastPlay(currentPlay), setLastWinnerId(playerId)
- 活动：resetConsecutivePassCount()
- 活动：updateLastPlayByPlayer(playerId, cards)
- 活动：调用 SettlementManager.checkAndSettle(gameState)

### 节点13：[GameEngine] 发布事件
- 活动：EventBus.post(new CardPlayedEvent(playerId, cardIds))
- 活动：EventBus.post(new TurnChangedEvent(nextPlayerId)) (如果未结束)
- 并行活动（蓝牙模式）：BluetoothEventRelay 检测 CardPlayedEvent → gateway.sendPlayAction() + gateway.syncGameState()
- 转到 节点40 (结算与回合切换)

---

### 节点14：[Human Player] 过牌操作
- 活动：点击 [过牌按钮] → gameActionHandler.passTurn()

### 节点14a：[GameEngine] passTurn()
- 前置检查：{是否新回合首出？}
  - [isOpeningTurn || lastPlay为空] → 拒绝Pass "新回合首出不能过牌"
  - [非首出] → 继续
- 活动：设置 player.passed = true
- 活动：incrementConsecutivePassCount()
- 活动：发布 PlayerPassedEvent
- 转到 节点40 (结算与回合切换)

---

### 节点15：[GameController] 无合法牌倒计时
- 活动：{consecutiveNoPlayCount == 0?}
  - [是] → 启动3秒倒计时 CountDownTimer
  - [否] → 直接 forcePass (连续无牌可出)
- 倒计时行为：
  - 每秒更新 UI: countdownCallback.updateCountdown(seconds)
  - 超时 → forcePass(player)
- forcePass 行为：
  - player.incrementConsecutiveNoPlayCount()
  - gameEngine.passTurn(player.getPlayerId())
  - 如果 passTurn 失败 → 回退计数

---

## 分支B：AI玩家出牌流程

### 节点20：[EventBus] TurnChangedEvent 发布
- 活动：EventBus 发布 TurnChangedEvent(newPlayerId="P2"|"P3"|"P4")

### 节点21：[AIEventListener] 接收事件
- 活动：onEvent(TurnChangedEvent) 
- 检查：{当前玩家类型是AI?}
  - [否] → 结束
  - [是] → 继续
- 检查：{isHost?} (蓝牙CLIENT端AI由HOST驱动)
  - [非Host] → 结束
  - [是Host] → 继续

### 节点22：[AIEventListener] 延迟决策
- 活动：handler.postDelayed(2200ms)
- 延迟期间再次确认：{当前玩家是否仍然是该AI?}
  - [回合已切换] → 跳过
  - [仍是该AI] → 调用 strategy.decidePlay(aiPlayer, gameState)

### 节点23：[AIDecisionStrategy] decidePlay()
- 子活动：根据难度选择策略分支
  - EASY → GreedyAIDecisionStrategy (含风格变体 NORMAL/AGGRESSIVE/DEFENSIVE)
  - MEDIUM/HARD → MonteCarloAIDecisionStrategy
  - ADAPTIVE → AdaptiveAIDecisionStrategy (内含 MonteCarlo + HumanStyleProfile调整)

### 节点24：[MonteCarloAIDecisionStrategy] 决策核心 (MEDIUM/HARD/ADAPTIVE)
- 子活动1：{残局强制压牌检测}
  - 对手最小手牌 ≤ 2 → findAnyValidBeatPlay() 强制压牌 → 返回出牌
- 子活动2：{消极游戏检测}
  - consecutivePassRounds ≥ 2 → 强制出最大单张 → 返回出牌
- 子活动3：{兜底逻辑}
  - consecutiveFailCount ≥ 2 → 强制过牌 → 返回null
- 子活动4：{开局保守策略}
  - 手牌 > 11 → getSmallestValidPlay() → 返回最小合法出牌
- 子活动5：{中盘谨慎策略}
  - 手牌 6~11 → getSmallestValidPlay() → 返回最小合法出牌
- 子活动6：{蒙特卡洛模拟}
  - candidateGenerator.generate(hand, lastPlay) → 候选列表
  - opponentHandSampler.sampleWorlds() → 世界采样
  - monteCarloSimulator.evaluate() → 期望得分
  - phaseManager.adjustScore() → 阶段调整
  - applyAdaptiveFactors() → 自适应因子调整
  - 选择最高分候选 → 返回出牌

### 节点25：{AI决策结果？}
- [有关联牌(非null/非空)] → AIEventListener调用 controller.aiPlayCards(cards)
- [无牌可出(null/空)] → AIEventListener调用 engine.passTurn(playerId)

### 节点26：[GameController] aiPlayCards()
- 活动：转换 Card对象为 cardId列表 → gameEngine.playCards()
- 活动：{出牌是否成功？}
  - [成功] → recordPlayToTracker, resetConsecutiveNoPlayCount, cancelCountdown
  - [失败] → 自动 fallback: gameEngine.passTurn() → UI提示

---

## 分支C：远程玩家出牌流程

### 节点30：[BluetoothGateway] 等待远程消息
- 活动：BluetoothReceiver 在后台线程持续接收数据
- 活动：handleRawMessage() → decode() → onMessageReceived()

### 节点31：[BluetoothGateway] 消息路由
- 活动：{消息类型判断}
  - PLAY_ACTION → NetworkGameBridge.handlePlayAction()
  - PASS_ACTION → NetworkGameBridge.handlePassAction()
  - 其他游戏消息 + HOST模式 → forwardToOtherClients() 转发

### 节点32：[NetworkGameBridge] handlePlayAction
- 活动：decodePlayActionPayload → 提取 Play 对象
- 活动：invokeEngineMethod("executeRemotePlay", Play) → gameEngine.executeRemotePlay(play)
- 活动：gameEngine.playCards(remotePlayerId, remoteCardIds)
- 活动：{需要ACK?} → sendAckFor(originalMessage)

### 节点33：[NetworkGameBridge] handlePassAction
- 活动：decodePassActionPayload → 提取 playerId
- 活动：invokeEngineMethod("executeRemotePass", playerId) → gameEngine.executeRemotePass(playerId)
- 活动：gameEngine.passTurn(playerId)

### 节点34：[BluetoothEventRelay] 事件守卫
- 活动：当 EventBus 发布 CardPlayedEvent/PlayerPassedEvent 时
- 检查：{玩家类型是REMOTE?}
  - [是] → 跳过 (防止回声循环)
  - [否] → 正常转发：gateway.sendPlayAction() / sendPassAction() + syncGameState()

---

## 合并点：结算与回合切换

### 节点40：[SettlementManager] 结算检查
- 活动：checkAndSettle(gameState)
- 遍历所有玩家：{某玩家 handCards.isEmpty()?}
  - [是] → setGameOver(true), setWinnerId(winner), 发布 GameOverEvent
  - [蓝牙HOST] → gateway.sendGameOver(winnerId, winnerName)
  - → 节点41 (显示游戏结束)
  - [否] → 节点42 (回合切换)

### 节点41：[GameActivity] 游戏结束处理
- 活动：showGameOverDialog(data) — 显示排名对话框
- 活动：cleanup() — 清理AI监听器、取消倒计时、重置资源
- → 【结束】

### 节点42：[TurnManager] 回合切换
- 活动：switchPlayer(gameState)
- 活动：{检查 consecutivePassCount}
  - [consecutivePassCount >= 3] →
    - setLastPlay(null)
    - clearAllPassStatus()
    - clearAllLastPlayRecords()
    - resetConsecutivePassCount()
    - currentPlayerId = lastWinnerId (上次出牌者获下一轮出牌权)
  - [consecutivePassCount < 3] →
    - 正常循环: currentPlayerId = (currentIndex+1) % playerCount
- 活动：EventBus.post(new TurnChangedEvent(nextPlayerId))
- → 回到 节点1 (下一玩家回合)

### 节点43：[GameActivity] UI刷新
- 单机模式：GameController.notifyUiRefresh() → GameActivity.refreshUI()
- 蓝牙模式：GameActivity.bluetoothRefreshRunnable 每秒轮询 refreshUI()
- 刷新内容：手牌列表、各玩家牌数、上家出牌、当前玩家标识、出牌区域等

## 输出要求
1. 使用 PlantUML 活动图语法（使用 |泳道名| 定义泳道）
2. 所有活动节点用圆角矩形标注
3. 决策节点用菱形 {条件} 标注
4. 开始用实心圆，结束用牛眼符号
5. 并发分支用 fork/bar (如事件发布同时进行蓝牙同步)
6. 合并用 join
7. 关键数据对象传递用对象流标注
8. 对复杂子活动（如蒙特卡洛决策）可使用子活动节点 (或 rake symbol)
9. 按上述8个泳道垂直分区
10. 泳道按调用层次排列（上层在上）
```

---

## 使用说明

以上7个提示词涵盖了UML七大建模图：
1. **用例图** — 系统功能全景
2. **类图** — 静态结构与代码映射
3. **对象图** — 运行时实例快照
4. **时序图** — 出牌流程交互时序
5. **通信图** — AI决策通信链路
6. **状态图** — 游戏生命周期状态机
7. **活动图** — 出牌活动完整流程

每个提示词包含：
- 100% 的项目信息（所有类名、方法名、字段名、枚举值、常量、参数）
- 完整的逻辑流程（包括所有条件分支和边界情况）
- 明确的 PlantUML 输出格式要求
- 具体的布局和样式指导

建议使用方法：
1. 将每个提示词单独复制给支持 PlantUML 的 GPT 模型
2. 将生成的 PlantUML 代码复制到 ProcessOn（支持PlantUML导入）
3. 在 ProcessOn 中微调布局和样式
4. 导出为 PNG/SVG 使用

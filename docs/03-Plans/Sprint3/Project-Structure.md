app/
└── src/main/java/com/example/cardgame/
    │
    ├── model/                 # 数据模型（纯数据）
    │   ├── Card.java
    │   ├── Suit.java
    │   ├── Rank.java
    │   ├── CardPattern.java
    │   ├── Play.java
    │   ├── Player.java
    │   └── GameState.java
    │
    ├── engine/                # 游戏流程（核心逻辑）
    │   ├── GameEngine.java
    │   ├── TurnManager.java
    │   ├── DealManager.java
    │   └── SettlementManager.java
    │
    ├── rule/                  # 规则系统（判断合法性）
    │   ├── RuleEngine.java
    │   ├── PatternRecognizer.java
    │   └── PlayValidator.java
    │
    ├── controller/            # UI 与逻辑桥接层（必须通过这里交互）
    │   ├── GameController.java
    │   └── GameActionHandler.java
    │
    ├── dto/                   # 数据传输对象（模块交接）
    │   ├── ValidationResult.java
    │   ├── PlayResult.java
    │   ├── PassResult.java
    │   ├── GameViewData.java
    │   └── PlayerViewData.java
    │
    ├── ui/                    # 界面层（只负责展示和点击）
    │   ├── GameActivity.java
    │   ├── MainActivity.java
    │   ├── adapter/
    │   │   └── HandCardAdapter.java
    │   └── viewmodel/
    │       └── GameViewModel.java
    │
    ├── ai/                    # AI占位（简单策略）
    │   ├── SimpleAiPlayer.java
    │   └── AiStrategy.java
    │
    ├── network/               # 联机占位接口
    │   └── MultiplayerGateway.java
    │
    ├── util/                  # 工具类
    │   ├── Logger.java
    │   └── CardUtils.java
    │
    └── test/                  # 测试入口 / main运行
        └── GameEngineTest.java

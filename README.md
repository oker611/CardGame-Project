# 锄大地 — 蓝牙联机卡牌游戏

基于 Android 的多人锄大地（Big Two）卡牌游戏，集成三层 AI 决策引擎和大语言模型对手风格分析。5 人团队，10 周迭代，5 个 Sprint 完整交付。

**语言：** Java | **平台：** Android | **Min SDK：** 21 | **目标 SDK：** 34
**测试：** 123 用例 0 失败 | **接口：** 14 个 | **设计模式：** 6+1 种

---

## 游戏规则

锄大地是四人扑克牌游戏，每人 13 张牌，持有 ♦3 者首出。牌型按强弱分为：

| 牌型 | 说明 | 张数 |
|------|------|------|
| 单张 (Single) | 单牌比点数 | 1 |
| 对子 (Pair) | 同点数两张 | 2 |
| 三张 (Triple) | 同点数三张 | 3 |
| 顺子 (Straight) | 连续点数五张 | 5 |
| 同花 (Flush) | 同花色五张 | 5 |
| 葫芦 (Full House) | 三张+对子 | 5 |
| 铁支 (Iron Branch) | 四张+单张 | 5 |
| 同花顺 (Straight Flush) | 同花顺子五张 | 5 |

五张牌型跨类型压制优先级：同花顺 > 铁支 > 葫芦 > 同花 > 顺子。出牌时必须出比上家更大的同牌型（或跨牌型压制）。

**双规则体系：**
- **南方规则**：首轮♦3必须出，花色权重 ♦ > ♣ > ♥ > ♠
- **北方规则**：无首轮限制，花色颠倒，禁用铁支

支持记牌器、牌型提示、透视三种可选道具。连续 3 人过牌自动清空桌面，上轮赢家获出牌权。

---

## AI 决策体系

三层递进架构，工厂模式统一创建，支持运行时切换：

### 第一层：贪心策略 (Greedy — EASY)
枚举所有合法出牌组合，贪心选择最小能压牌的牌型。三种风格变体通过 `aggressivenessFactor` 和 `defenseFactor` 调整行为：

| 变体 | 风格 | 行为 |
|------|------|------|
| Normal | 均衡 | 按比较值从小到大选择 |
| Aggressive | 激进 | 优先出手大牌抢占牌权 |
| Defensive | 保守 | 保留大牌，被动跟牌 |

### 第二层：蒙特卡洛策略 (Monte Carlo — MEDIUM / HARD)
不完全信息博弈的期望收益评估，内部拆为五个独立组件：

```
CandidateGenerator  → 启发式生成候选动作 (TOP_K=4 截断)
OpponentHandSampler → 剩余牌池随机采样 10 个 World
MonteCarloSimulator → 候选×World 快速贪心推演到终局
PhaseManager        → EARLY/MID/LATE 三阶段修正评分
CardTracker         → 已出牌统计 (各节点本地计算)
```

动态模拟次数 100-400，1.5 秒超时控制。残局阶段炸弹优先、强制压牌、多轮类型试探等高级策略。

### 第三层：自适应策略 (Adaptive — ADAPTIVE)
包装蒙特卡洛策略，接入 `HumanStyleAnalyzer` 分析人类玩家出牌历史：

1. 收集人类玩家出牌序列 → 发送至 Vivo 蓝心大模型
2. LLM 返回风格标签：激进 / 保守 / 均衡
3. 自动调整蒙特卡洛的进攻/防守因子
4. 风格档案通过 `CrossGameMemoryManager` 持久化到 SharedPreferences

LLM 调用在独立单线程 ExecutorService 中异步执行，不介入实时出牌循环。LLM 不可用时自动降级为本地规则 fallback 分析。

---

## 蓝牙通信

基于 Android Bluetooth RFCOMM，支持安全/不安全双降级策略（兼容小米/华为等国产 ROM）。

### 架构

```
BluetoothGateway (外观, 统一入口)
  ├── RoomController        — 房间管理 (玩家槽位/名称/设备映射)
  ├── ReliabilityManager    — 心跳保活 + ACK 重试 + 超时检测
  ├── ReconnectionHandler   — 断线重连 (指数退避 2/4/8/16/30s)
  ├── NetworkGameBridge     — 游戏消息路由 (INIT_GAME/PLAY/PASS/GAME_OVER)
  ├── BluetoothConnectionManager — 连接管理 (扫描/配对/多路连接)
  ├── BluetoothSender       — JSON 消息发送
  └── BluetoothReceiver     — JSON 消息接收 (序列号校验)
```

### 消息协议

16 种 MessageType，JSON 编码 (Gson)：

| 类型 | 方向 | 说明 |
|------|------|------|
| INIT_GAME | HOST→ALL | 开局手牌 + GameState 同步 |
| PLAY_ACTION | ALL | 出牌同步 |
| PASS_ACTION | ALL | 过牌同步 |
| GAME_OVER | HOST→ALL | 游戏结束通知 |
| HEARTBEAT | ALL↔ALL | 5s 间隔心跳 |
| ACK | ALL | 关键消息确认 |
| JOIN / JOIN_ACK | CLIENT↔HOST | 加入房间握手 |
| PLAYER_JOINED / PLAYER_LEFT | HOST→ALL | 玩家进出广播 |
| RECONNECT / RECONNECT_ACK | CLIENT↔HOST | 断线重连握手 |
| ERROR | ALL | 错误通知 |

### 可靠性机制

- **心跳保活**：5s 间隔，逐通道独立计时，15s 无响应判定超时
- **ACK 可靠投递**：INIT_GAME / PLAY_ACTION / PASS_ACTION / GAME_OVER 需确认，3s 超时最多 3 次重试
- **断线重连**：指数退避 2/4/8/16/30s，HOST 端 serverSocket 持续监听，CLIENT 端自动重连并恢复完整 GameState
- **回声防护**：`BluetoothEventRelay` 检查 `player.getType() != REMOTE` 防止循环转发

---

## 项目结构

```
app/src/main/java/com/example/cardgame/
├── ai/            7 种 AI 策略 + 蒙特卡洛 5 组件 + 风格分析器
├── controller/    GameController, BluetoothController, BluetoothEventRelay
├── dto/           PlayResult, PassResult, GameViewData, PlayerViewData
├── engine/        GameEngine, DealManager, TurnManager, SettlementManager
├── event/         EventBus (IEventBus), 4 类事件
├── llm/           LLMAnalyzer (ILLMAnalyzer), VivoLLMClient (IVivoLLMClient)
├── model/         Card, Player, GameState, Play, HumanStyleProfile
├── network/       BluetoothGateway + 7 子组件, NetworkGameBridge
├── rule/          RuleEngine, PatternRecognizer (IPatternRecognizer), PlayValidator (IPlayValidator)
├── ui/            GameActivity, RoomLobbyActivity, RoomSettingsActivity, SearchDeviceActivity
└── util/          CardTracker, CrossGameMemoryManager, HermesLog

app/src/test/      16 个测试类 | 122 单元测试
app/src/androidTest/ 1 个 Instrumentation 测试
```

---

## 设计模式

| 模式 | 应用位置 | 说明 |
|------|---------|------|
| **Strategy** | `AIDecisionStrategy` | 7 种实现统一接口，运行时切换 |
| **Observer** | `IEventBus` + `GameEventListener` | UI / AI / 蓝牙解耦，4 类事件 |
| **Factory** | `AIStrategyFactory` | 集中管理 AI 策略依赖注入和组装 |
| **Adapter** | `BluetoothEventRelay` | 事件→蓝牙消息转换 |
| | `AdaptiveAIDecisionStrategy` | 包装蒙特卡洛 + 风格因子调整 |
| **Builder** | `RuleConfig.Builder` | 不可变规则配置，SOUTHERN/NORTHERN 预设 |
| **Singleton** | `EventBus` | Holder 静态内部类，同时注入 `IEventBus` |
| **Template Method** | `GreedyAIDecisionStrategy.decidePlay()` | 统一决策骨架，子类覆写选择逻辑 |

---

## 快速开始

### 环境要求

- Android Studio Hedgehog 及以上
- Java 8+
- Android SDK 21+
- 一台 Android 实体机或模拟器 (蓝牙功能需实体机)

### 构建运行

```bash
git clone https://github.com/oker611/CardGame-Project.git
cd CardGame-Project

# LLM 风格分析功能需要 Vivo API 密钥 (可选，不影响游戏核心功能)
# 在 gradle.properties 中配置：VIVO_APP_KEY=your_key

./gradlew assembleDebug       # 构建 Debug APK
./gradlew test                # 运行 122 个单元测试
./gradlew installDebug        # 安装到已连接设备/模拟器
```

### 测试

```bash
./gradlew test                              # 单元测试 (122 用例)
./gradlew connectedAndroidTest              # Instrumentation 测试 (需模拟器)
```

测试覆盖：

| 模块 | 测试文件 | 用例 |
|------|---------|------|
| AI | AIStrategyFactoryTest, AIDIConstructorTest, PatternAnalyzerTest, PhaseManagerTest | 40 |
| Engine | GameEngineTest, GameEngineEdgeCaseTest, DealManagerTest, TurnManagerTest, SettlementManagerTest | 16 |
| Rule | PlayValidatorTest, RuleConfigTest | 21 |
| Network | BluetoothMessageCodecTest, ReliabilityManagerTest, RoomControllerTest | 24 |
| Util | CardTrackerTest | 20 |
| Infra | ExampleUnitTest, ExampleInstrumentedTest | 2 |

---

## 文档

```
docs/
├── 01-Requirements/    需求文档 (用例模型, Vision, 补充规约, 术语表)
├── 02-Design/          设计文档 (领域模型, SSD, 操作契约, 类图, 包图, 状态机图)
├── 03-Plans/           迭代计划 (Sprint 1-5)
├── 04-Refactoring/     重构报告 (设计模式汇总, 事件框架, 各模块评估报告)
├── 05-Model-Refinement/ 模型精化日志
└── 06-Defense/         答辩材料

根目录:
├── ARCHITECTURE.md      架构文档 (当前接口清单/DI 图/代码质量指标)
├── docs/02-Design/AI-Strategy-Early-Design.md  AI 策略早期设计文档
├── docs/02-Design/AI-Strategy-Implementation.md AI 策略实现文档
├── docs/UML-7-Diagrams-Prompts.md  7 类 PlantUML 生成 Prompt
└── docs/Defense-Materials.md       答辩准备材料 (分工/创新点/Q&A)
```

---

## 许可证

MIT License

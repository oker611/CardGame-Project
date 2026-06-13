# 锄大地 — 蓝牙联机卡牌游戏

基于 Android 的多人卡牌游戏，集成三层 AI 决策引擎和大语言模型对手风格分析。

**语言：** Java | **平台：** Android | **Min SDK：** 21 | **目标 SDK：** 34

## 核心特性

- **锄大地规则引擎** — 10 种牌型识别，南方/北方双规则，首轮♦3强制，跨牌型压制
- **三层 AI 体系** — 贪心策略(3 风格变体) → 蒙特卡洛模拟 → 自适应策略(分析人类风格并自动调整)
- **蓝牙四人联机** — HOST + 3 CLIENT，16 种消息类型，心跳保活，ACK 可靠投递，断线重连
- **LLM 集成** — 接入 Vivo 蓝心大模型，异步分析对手风格，跨游戏持久化
- **事件驱动架构** — EventBus 解耦 UI / AI / 蓝牙三大模块，14 个接口支撑 DI 体系
- **道具系统** — 记牌器、牌型提示、透视三种可选辅助

## 项目结构

```
app/src/main/java/com/example/cardgame/
├── ai/            AI 决策 (7 种策略 + 蒙特卡洛组件)
├── controller/    控制器 (GameController, BluetoothController, EventRelay)
├── dto/           数据传输对象
├── engine/        游戏引擎 (发牌/回合/结算)
├── event/         事件总线 (4 类事件)
├── llm/           大模型分析 (Vivo LLM 客户端)
├── model/         领域模型 (Card, Player, GameState)
├── network/       蓝牙通信 (网关/桥接/房间/可靠性)
├── rule/          规则引擎 (牌型识别/出牌校验)
├── ui/            界面层 (GameActivity, RoomLobby 等)
└── util/          工具类 (记牌器/跨游戏记忆)
```

## 设计模式

| 模式 | 应用 |
|------|------|
| Strategy | `AIDecisionStrategy` → 7 种实现 |
| Observer | `IEventBus` + `GameEventListener` |
| Factory | `AIStrategyFactory` |
| Adapter | `BluetoothEventRelay`, `AdaptiveAIDecisionStrategy` |
| Builder | `RuleConfig.Builder` |
| Singleton | `EventBus` (Holder 模式, 同时支持 `IEventBus` 注入) |
| Template Method | `GreedyAIDecisionStrategy.decidePlay()` |

## 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Java 8 |
| UI | Android View + Material Design |
| 通信 | Bluetooth RFCOMM |
| 网络 | OkHttp 4.12 (LLM API) |
| 序列化 | Gson 2.10 |
| 测试 | JUnit 4 (123 用例, 0 失败) |
| CI/CD | GitHub Actions |

## 快速开始

```bash
git clone https://github.com/oker611/CardGame-Project.git
cd CardGame-Project

# 配置 LLM API 密钥 (gradle.properties)
# VIVO_APP_KEY=your_key

./gradlew assembleDebug      # 构建
./gradlew test                # 单元测试 (123 用例)
./gradlew installDebug        # 安装到设备
```

## 文档

```
docs/
├── 01-Requirements/    需求文档 (用例模型, Vision, 补充规约, 术语表)
├── 02-Design/          设计文档 (领域模型, SSD, 操作契约, 类图, 包图, 状态机图)
├── 03-Plans/           迭代计划 (Sprint 1-5)
├── 04-Refactoring/     重构报告 (设计模式汇总, 事件框架, 各模块评估)
├── 05-Model-Refinement/ 模型精化日志
└── 06-Defense/         答辩材料
```

## 许可证

MIT License

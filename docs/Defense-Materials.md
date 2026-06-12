# 锄大地蓝牙联机卡牌游戏 — 答辩准备材料

> 答辩形式：腾讯会议线上答辩 | 时长：15分钟（8分钟讲解 + 7分钟提问）
> 适用时间：2026年6月10日-11日

---

## 一、项目概要信息

| 项 | 内容 |
|---|------|
| **项目名称** | 锄大地蓝牙联机卡牌游戏 (Big Two / Choh Di Di) |
| **项目类型** | Android 移动端多人卡牌游戏 |
| **技术栈** | Java + Android SDK + Bluetooth RFCOMM + OkHttp + Gson |
| **AI 集成** | Vivo 蓝心大模型 (DeepSeek-V3.2) + 蒙特卡洛树搜索 + 遗传算法参数优化 |
| **代码规模** | 约 100 个 Java 类，覆盖 11 个功能包，14 个接口，123 个测试用例 |
| **开发周期** | 5 个 Sprint（约 10 周） |
| **团队规模** | 5 人 |

---

## 二、任务分工总览

### 团队成员与角色

| 姓名 | 角色 | 主要职责领域 |
|------|------|------------|
| **盛进** | 组长 / 架构师 | 项目管理、领域建模、UML设计一致性、Git仓库管理、文档体系、系统顺序图、操作契约 |
| **陈政昊** | 核心引擎开发 | GameEngine、TurnManager、SettlementManager、DealManager、规则引擎(PatternRecognizer/PlayValidator)、AI基础框架、核心BUG修复 |
| **曾奕琦** | AI系统开发 | AIPlayer基础实现、AI合法出牌改造、AI复杂牌型识别、自动Pass逻辑、LLM对手风格分析接入、MonteCarlo AI策略 |
| **张瀚月** | UI/UX开发 | GameActivity、手牌展示、UI布局、C位视角、牌面资源修复、连续Pass重置桌面、日志系统、补充页面、用例图绘制 |
| **傅钧烨** | 蓝牙/网络开发 | BluetoothConnectionManager、BluetoothGateway、BluetoothEventRelay、NetworkGameBridge、蓝牙消息协议、4人联机、设备发现、断线重连、心跳保活、ACK可靠投递、联机占位接口、开发环境搭建 |

### Sprint 阶段分工明细

| Sprint | 阶段 | 核心任务 | 主要贡献者 |
|--------|------|---------|-----------|
| Sprint 1 | Inception | 用例图、Vision文档、Glossary术语表、Supplementary Spec、开发环境搭建、GitHub仓库 | 全员分工协作 |
| Sprint 2 | Elaboration | 领域模型图、SSD系统顺序图、操作契约、类图设计、出牌逻辑代码结构、发牌逻辑实现 | 盛进(模型)+陈政昊(代码)+全员 |
| Sprint 3 | Construction | 核心数据模型落地、游戏主流程实现、规则系统初稿、发牌系统、基础UI页面、UI-逻辑连接、联机接口占位、简单AI占位、联调日志 | 按模块并行开发 |
| Sprint 4 | Stabilization | PatternRecognizer完善、PlayValidator完善、AI合法出牌改造、连续Pass重置、UML代码一致性核对、蓝牙对战最小闭环 | 规则+AI+蓝牙并行 |
| Sprint 5 | Feature Polish | 4人蓝牙联机、C位视角修复、AI复杂牌型、Pass逻辑修复、顺子排序修复、UI资源修复、自动Pass、房间邀请 | BUG修复+功能完善 |

---

## 三、软件总体特征

### 3.1 核心游戏功能

**(1) 完整锄大地规则引擎**
- 支持10种牌型识别：单张(SINGLE)、对子(PAIR)、三张(TRIPLE)、炸弹(QUADRUPLE)、顺子(STRAIGHT)、同花(FLUSH)、葫芦(FULL_HOUSE)、铁支(IRON_BRANCH)、同花顺(STRAIGHT_FLUSH)、无效(INVALID)
- 五张牌型跨类型压制机制（优先级：同花顺5 > 铁支4 > 葫芦3 > 同花2 > 顺子1）
- 南方规则(♦3首出必选) / 北方规则(无首轮限制+花色颠倒+禁用铁支) 双规则体系
- 首轮必出方块3强制校验、同牌型compareValue比较、连续3人Pass清空桌面
- 规则引擎采用 **Builder模式 + 不可变配置(RuleConfig)**，支持任意自定义规则扩展

**(2) 4人多人对战**
- 单机模式：1 Human + 3 AI
- 蓝牙联机模式：1 HOST + 3 CLIENT (支持任意分布的Human/AI/Remote组合)
- 完整的蓝牙消息协议（16种MessageType）：INIT_GAME, PLAY_ACTION, PASS_ACTION, GAME_OVER, HEARTBEAT, ERROR, JOIN, JOIN_ACK, PLAYER_JOINED, PLAYER_LEFT, ACK, RECONNECT, RECONNECT_ACK
- 心跳保活机制（5秒间隔，15秒超时检测，逐通道追踪）
- ACK可靠投递机制（3秒超时×3次重试）
- 断线重连机制（指数退避：2/4/8/16/30秒，HOST端serverSocket持续监听，CLIENT端自动重连+状态恢复）

**(3) 事件驱动架构**
- 全局 EventBus（单例，CopyOnWriteArrayList 保证线程安全）
- 4类事件：CardPlayedEvent, PlayerPassedEvent, TurnChangedEvent, GameOverEvent
- UI刷新、AI决策、蓝牙同步三大模块均通过事件解耦
- BluetoothEventRelay 守卫模式防止 REMOTE 玩家回声循环

**(4) 无牌可出倒计时功能**
- 人类玩家无合法牌时3秒自动倒计时
- 连续无牌可出直接自动过牌
- CountDownTimer + CountdownUICallback 实现 UI 分离

### 3.2 创新点

**(1) 三层AI决策体系（贪心→蒙特卡洛→自适应）**

| AI层级 | 难度 | 核心算法 | 特点 |
|--------|------|---------|------|
| 贪心AI (Greedy) | EASY | 枚举所有合法出牌+贪心选择最小牌 | 3种风格变体(Normal/Aggressive/Defensive)，通过aggressivenessFactor和defenseFactor调整行为 |
| 蒙特卡洛AI (Monte Carlo) | MEDIUM/HARD | 对手手牌随机采样+蒙特卡洛推演+FSM阶段修正 | 10个World采样，4候选截断，动态模拟次数(100~400)，1.5秒超时 |
| 自适应AI (Adaptive) | ADAPTIVE | 蒙特卡洛 + HumanStyleProfile自适应因子 | 分析人类玩家风格(激进/保守/均衡/虚张声势) → 自动调整aggressiveness/defense因子 |

蒙特卡洛AI 包含高级策略层：
- 残局强制压牌（对手手牌≤2）
- 多轮类型试探（PAIR→TRIPLE→STRAIGHT切换）
- 开局保守策略（手牌>11出小牌）
- 中盘谨慎策略（手牌6-11出最小压牌）
- 一锤定音（手牌≤5且对手大牌耗尽）
- 拆牌逼迫策略
- 出2后连贯出牌
- 炸弹优先（残局阶段）

**(2) AI大语言模型集成**

- 接入 **Vivo 蓝心大模型**（Volc-DeepSeek-V3.2），通过 OkHttp 调用 REST API
- 实现对手风格分析（OpponentStyleAnalyzer）：将玩家出牌历史发送给LLM，返回风格标签（激进/保守/均衡）
- 实现人类风格学习（HumanStyleAnalyzer）：分析人类玩家的出牌动作序列，生成 HumanStyleProfile
- 跨游戏记忆（CrossGameMemoryManager）：通过 SharedPreferences 持久化风格档案，实现"越玩越聪明"
- **关键设计**：所有LLM调用均为离线异步（单线程ExecutorService），不介入实时出牌循环，不影响对局延迟

**(3) AI代码引擎使用**

- **GitHub Copilot / Claude Code** 辅助代码生成：用于复杂AI策略的初始实现、重构、BUG修复
- AI辅助代码审查：通过AI分析代码逻辑一致性、发现潜在BUG
- AI辅助UML建模：利用AI分析代码生成PlantUML类图、时序图等
- 典型人机协作流程：人工设计架构 → AI生成初始代码 → 人工审查修正 → AI辅助测试 → 人工最终验证

**(4) 蓝牙通信的完整工程化实现**

- 基于 Android Bluetooth RFCOMM 协议
- 多路连接管理（BluetoothConnectionManager支持多客户端并发）
- 安全/不安全RFCOMM双降级策略（兼容国产ROM）
- RFCOMM稳定等待（300ms延迟）
- Socket连接超时控制（8秒，独立线程+join）
- JSON消息编解码（Gson + BluetoothMessageCodec）
- 手牌隔离（HOST端按设备构建过滤后的GameState，仅暴露目标客户端自身手牌）

---

## 四、分阶段实验报告

### Sprint 1：Inception（需求启动）

**实验目标**：完成用例建模与需求文档，搭建开发环境

**实验内容**：
1. 确定系统参与者（人类玩家、AI玩家、蓝牙设备）
2. 梳理核心用例：开始游戏、发牌、出牌、过牌、查看游戏状态、游戏结算
3. 使用 ProcessOn 绘制 UML 用例图（包含 Actor、Use Case、include/extend 关系）
4. 编写 Vision 文档、Glossary 术语表（11个术语）、Supplementary Spec（7条补充需求）
5. 搭建 Android Studio + Java 开发环境
6. 建立 GitHub 仓库并完成团队权限配置

**课程知识应用**：
- 软件工程需求分析方法（FURPS+）
- UML 用例建模（参与者识别、用例粒度控制、关系建模）
- 敏捷开发 Scrum 方法论（Sprint计划、任务卡片、验收标准）

**难点突破**：
- 用例粒度控制：初期用例过于细化，通过"核心流程优先、扩展流程后补"原则重新梳理
- 术语统一：通过建立 Glossary 文档确保团队使用一致的领域语言

---

### Sprint 2：Elaboration（细化设计）

**实验目标**：完成领域建模与系统设计

**实验内容**：
1. 提取核心领域实体（Player、Card、Game、Room），建立领域模型图
2. 绘制系统顺序图（SSD）—— 出牌流程、发牌流程
3. 编写操作契约（Operation Contract）—— Play Cards、Deal Cards 操作的输入/输出/前置条件/后置条件
4. 从领域模型细化为类图（包含属性、方法、关系）
5. 设计出牌逻辑代码结构（类+方法骨架）
6. 实现发牌逻辑（洗牌+发牌+识别首发玩家）

**课程知识应用**：
- 领域驱动设计（DDD）：实体识别、值对象、聚合根
- GRASP 模式：信息专家（Player持有handCards）、控制器（GameController）、创建者（DealManager）
- UML 类图、顺序图的设计原则
- 操作契约的 pre/post-condition 形式化描述

**难点突破**：
- 实体关系建模：Player-GameState-Card 的聚合关系确定——GameState 聚合 Player、Player 聚合 List<Card>
- 发牌算法验证：4人×13张=52张的分配正确性通过 validateDealing 强制断言

---

### Sprint 3：Construction（核心构建）

**实验目标**：实现游戏核心逻辑与基础UI

**实验内容**：
1. **核心数据模型落地**（傅钧烨主导）：Card、Player、Play、GameState、Suit、Rank 完整实现
2. **游戏主流程实现**（陈政昊主导）：GameEngine（出牌/过牌/状态管理）、TurnManager（循环切换）、SettlementManager（判定胜负）
3. **规则系统初稿**（曾奕琦主导）：PatternRecognizer（牌型识别）、PlayValidator（出牌校验）、RuleEngine接口
   - 支持单张、对子验证
   - 首轮方块3强制规则
   - 压过上家规则（同牌型+点数更大）
4. **发牌系统**（陈政昊主导）：DealManager（52张牌生成+洗牌+分配+首发识别）
5. **基础UI页面**（张瀚月主导）：GameActivity（手牌展示+当前玩家显示+出牌按钮+Pass按钮）
6. **UI-逻辑连接**（张瀚月主导）：GameController + GameActionHandler接口 + GameViewData映射
7. **联机接口占位**（傅钧烨主导）：MultiplayerGateway接口（sendPlayAction/sendPassAction/syncGameState）
8. **简单AI占位**（曾奕琦主导）：AIPlayer类，基础托管策略
9. **联调与日志**（盛进主导）：发牌/回合/出牌/结束日志输出

**课程知识应用**：
- 设计模式：策略模式（AIDecisionStrategy接口）、Builder模式（RuleConfig构建器）、单例模式（EventBus）
- 面向对象分析设计（OOAD）：封装、继承、多态
- MVC架构：Model(GameState/Card/Player) — View(GameActivity) — Controller(GameController)
- 接口隔离原则：GameActionHandler、BluetoothActionHandler、MultiplayerGateway 接口分离

**难点突破**：
- 规则引擎可扩展性：通过 RuleConfig 配置 + RuleEngine 接口解耦，实现了 SOUTHERN/NORTHERN 双规则
- GameState 状态一致性：通过"所有修改都在 GameEngine 中完成 + 返回 PlayResult/PassResult 包含最新 state"保证

---

### Sprint 4：Stabilization（稳定交付）

**实验目标**：规则系统完善 + 蓝牙最小闭环 + UML一致性核对

**实验内容**：
1. **规则系统完善**（陈政昊+曾奕琦）：PatternRecognizer 扩展支持全部10种牌型、PlayValidator 完善校验逻辑
2. **AI合法出牌改造**（曾奕琦）：AI调用PlayValidator找最小能压的牌，无牌则Pass
3. **连续Pass重置桌面**（张瀚月）：连续3人Pass→清空桌面→出牌权转给上赢家
4. **事件总线框架**（盛进+全员）：
   - 创建 EventBus + 4种GameEvent
   - GameActivity接入事件（UI自动刷新）
   - 新建BluetoothEventRelay（蓝牙事件中继）
   - 新建AIEventListener（AI事件驱动）
   - 设计原则：只增不删，不改已有代码
5. **蓝牙对战最小闭环**（傅钧烨+陈政昊联调）：
   - 蓝牙连接底层（扫描、建立连接、获取IO流）
   - 角色分配逻辑（HOST/CLIENT）
   - JSON消息协议定义（InitGamePayload等8种载荷）
   - GameEngine暴露rebuildGameState/executeRemotePlay/executeRemotePass
   - 开局手牌同步、动作同步、游戏结束同步
   - 冒烟测试（两台手机完整一局）
6. **UML与代码一致性核对**（盛进主导）：对照类图检查模型实体、方法一致性

**课程知识应用**：
- 观察者模式：EventBus + GameEventListener → 解耦UI/AI/蓝牙三大模块
- 适配器模式：BluetoothEventRelay 作为 GameEngine 事件到蓝牙消息的适配器
- 软件重构：事件驱动改造遵循"只增不删"原则，验证新路径后逐步切除旧路径
- 集成测试：蓝牙联调的双人逐条验收方法

**难点突破**：
- 蓝牙回声循环：BluetoothEventRelay 通过检查 player.getType()==REMOTE 守卫，防止远程玩家的出牌再次触发蓝牙发送
- 状态同步一致性：CLIENT端通过 rebuildGameState 完全接受HOST状态，HOST端在每个动作后 syncGameState

---

### Sprint 5：Feature Polish（功能完善与Bug修复）

**实验目标**：完善多人联机、AI升级、BUG修复、体验优化

**实验内容**：
1. **蓝牙4人联机**（傅钧烨）：多路连接管理（Map<deviceAddress, SenderReceiverPair>）、消息广播与转发、角色动态分配(P2/P3/P4)、AI玩家补齐
2. **C位视角修复**（张瀚月）：UI按playerId轮转排列，每人都以自己为底部C位
3. **AI复杂牌型**（曾奕琦）：AI支持顺子/同花/葫芦/铁支/同花顺的识别与压牌
4. **蒙特卡洛AI**（曾奕琦+陈政昊）：CandidateGenerator（启发式候选生成+截断）、OpponentHandSampler（剩余牌池随机采样）、MonteCarloSimulator（World模拟推演）、PhaseManager（FSM阶段管理）
5. **自适应AI系统**（曾奕琦）：HumanStyleAnalyzer（分析人类出牌序列）、AdaptiveAIDecisionStrategy（根据HumanStyleProfile调整aggressiveness/defense因子）、VivoLLMClient（调用蓝心大模型分析对手风格）
6. **道具系统**（全员协作）：记牌器CardTracker、牌型提示PropPatternHint、透视PropSeeThrough
7. **UI优化**：手牌选中动画、出牌区域动画、游戏结束对话框排名展示、AI提示文本栏
8. **BUG修复**：Pass多绕一圈、顺子排序反转、方块A显示、草2白边、手牌滑动不居中、卡片阴影

**课程知识应用**：
- 状态模式/有限状态机：PhaseManager（EARLY → MID → LATE阶段转换）
- 蒙特卡洛方法：不完全信息博弈的期望收益评估
- 策略模式：Greedy/Normal/Aggressive/Defensive/Adaptive/MonteCarlo 六种AI策略
- 模板方法模式：AIDecisionStrategy接口 → decidePlay模板
- 适配器模式：AdaptiveAIDecisionStrategy 包装 MonteCarloAIDecisionStrategy + HumanStyleProfile

**难点突破**：
- 蒙特卡洛性能：通过动态样本数(100/200/400)、候选截断(TOP_K=4)、1.5秒超时控制、单线程Executor确保不阻塞主线程
- 蓝牙4人连接的可靠性：心跳机制逐路检测（活跃链路不掩盖失联链路）、ACK确认重试、断线重连指数退避
- LLM安全集成：异步调用 + 独立线程池 + 结果仅用于非实时风格调整，避免影响对局

---

## 五、实验结果录屏展示计划

### 演示流程（预计3分钟）

| 序号 | 演示内容 | 时长 |
|------|---------|------|
| 1 | 启动APP → 主菜单界面 → 选择规则（南方/北方）| 15秒 |
| 2 | 练习场模式：人机对战完整一局（展示出牌/过牌/牌型识别/自动Pass/游戏结算）| 45秒 |
| 3 | 房间设置：AI难度切换(EASY/MEDIUM/HARD/ADAPTIVE)、道具开关 | 15秒 |
| 4 | 蓝牙联机：HOST创建房间 → CLIENT搜索+加入 → 4人开局 | 30秒 |
| 5 | 蓝牙对局：展示跨设备出牌同步、AI自动出牌、记牌器 | 30秒 |
| 6 | 智能模式：展示对手风格分析Toast提示 → 自适应AI策略调整 | 15秒 |
| 7 | 游戏结束排名展示 + 再来一局 | 10秒 |

### 关键演示点

- **规则引擎正确性**：首轮♦3强制、同牌型压牌、五张牌型跨类型压制、连续3Pass清空桌面
- **AI智能程度**：残局强制压牌、开局保守出小牌、炸弹优先、蒙特卡洛决策耗时<1.5秒
- **蓝牙稳定性**：4人房间同步、心跳检测、断线重连恢复
- **LLM集成**：异步风格分析 → Toast通知"对手风格：激进，AI采用防守反击策略"

---

## 六、答辩Q&A 预备问题

### 核心标准类问题

#### Q1: 如何保证软件质量？测试策略是什么？
**A**:
- 单元测试：123 个用例覆盖引擎/规则/AI/网络/工具层，0 失败
- 集成测试：蓝牙联调冒烟测试（两台/四台手机完整对局）
- Instrumentation 测试：Android 模拟器端到端验证
- CI/CD：GitHub Actions 在 push/PR 时自动 build + test
- 验收测试：每次 Sprint 结束后按验收标准逐条检查
- 代码审查：GitHub Pull Request → 团队成员 Code Review → 合并
- AI 辅助质量保障：使用 Claude Code 进行代码审查、架构审计、BUG 修复

#### Q2: 项目中的设计模式应用有哪些？
**A**:
- **策略模式**：AIDecisionStrategy 接口 → 7 种实现（Greedy/MonteCarlo/Adaptive + Normal/Aggressive/Defensive 风格变体）
- **观察者模式**：IEventBus + GameEventListener，解耦 UI/AI/蓝牙三大模块
- **工厂模式**：AIStrategyFactory 集中管理 7 种策略的依赖注入和构建
- **Builder 模式**：RuleConfig.Builder，支持南方/北方双规则 + 自定义规则
- **单例模式**：EventBus（Holder 静态内部类线程安全实现，同时支持 IEventBus 注入）
- **适配器模式**：BluetoothEventRelay（事件→蓝牙消息），AdaptiveAIDecisionStrategy（包装 MonteCarlo + 风格学习）
- **模板方法模式**：GreedyAIDecisionStrategy.decidePlay() 定义统一决策骨架
- **状态模式**：PhaseManager（EARLY/MID/LATE 三阶段状态机）

共 14 个接口支撑完整 DI 体系。

#### Q3: 项目中如何应用UML建模？和最终代码的对应关系？
**A**:
- Sprint 1：用例图 → 对应最终实现的核心用例（出牌/过牌/发牌/游戏结算）
- Sprint 2：领域模型图 → 对应 model 包（Card/Player/GameState/Play/Suit/Rank）
- Sprint 2：类图 → 对应约100个Java类的完整结构和关系
- Sprint 2：系统顺序图(SSD) → 对应 GameActivity → GameController → GameEngine → RuleEngine 调用链
- Sprint 3-5：迭代中通过UML一致性核对确保设计=实现

#### Q4: 如何应用本门课程的知识进行软件分析、设计和建模？
**A**:
- **分析阶段**：用例建模（识别Actor和Use Case）、领域建模（提取实体和关系）、补充规约（非功能需求）
- **设计阶段**：GRASP模式（信息专家、控制器、创建者）、GoF设计模式（策略/观察者/Builder/单例）、MVC架构分层
- **建模阶段**：ProcessOn绘制 UML 用例图/类图/顺序图/状态图/活动图/通信图，PlantUML生成七大图
- **实现阶段**：面向对象原则（单一职责、开闭原则、接口隔离、依赖倒置）、敏捷Scrum（Sprint计划/任务卡片/每日立会）
- **测试阶段**：单元测试(JUnit)、集成测试(蓝牙联调)、验收测试(Sprint验收标准)

#### Q5: 蓝牙通信的可靠性是如何保证的？
**A**:
- 消息协议：16种MessageType + JSON Payload，结构化数据传输
- ACK确认机制：INIT_GAME/PLAY_ACTION/PASS_ACTION/GAME_OVER需要ACK确认，3秒超时×3次重试
- 心跳保活：5秒间隔发送HEARTBEAT，15秒无响应判定超时，逐通道追踪（活跃链路不掩盖失联链路）
- 断线重连：指数退避(2/4/8/16/30秒)，HOST端持续监听serverSocket，CLIENT端发送RECONNECT消息恢复状态
- 回声防护：BluetoothEventRelay检查玩家类型，REMOTE玩家的动作不触发二次蓝牙发送
- 连接降级：安全RFCOMM → 不安全RFCOMM自动降级（兼容国产ROM）

#### Q6: AI系统的具体实现层次？
**A**（三层递进）:
1. **底层层—候选生成+对手建模**：CandidateGenerator生成所有合法组合并截断到TOP4，OpponentHandSampler基于已出牌信息对剩余牌池随机采样生成10个World
2. **中间层—蒙特卡洛推演**：对每个候选×每个World进行快速贪心模拟到终局，计算期望排名得分，PhaseManager根据EARLY/MID/LATE阶段调整评分
3. **顶层层—自适应风格**：HumanStyleAnalyzer分析人类出牌历史→生成HumanStyleProfile（激进/保守/均衡/虚张声势）→AdaptiveAIDecisionStrategy自动调整蒙特卡洛的aggressivenessFactor和defenseFactor
4. **离线辅助**：Vivo蓝心大模型分析对手风格、遗传算法离线优化参数（50个体×200局×50代）

#### Q7: 项目中有哪些创新点？（评分标准中的加分项）
**A**:
1. **AI大语言模型集成**：接入Vivo蓝心大模型进行对手风格智能分析，实现"千人千面"的自适应AI
2. **AI代码引擎使用**：使用Claude Code/GitHub Copilot辅助开发全流程（代码生成→审查→BUG修复→UML生成），显著提升开发效率
3. **蒙特卡洛树搜索应用**：在不完全信息博弈中实现有效的期望收益评估
4. **遗传算法参数优化**：AI策略参数的自动化寻优
5. **事件驱动架构 + 依赖注入**：14 接口 DI 体系 + IEventBus 观察者模式，实现 UI/AI/蓝牙松耦合
6. **蓝牙可靠通信协议栈**：ACK 确认 + 心跳保活 + 断线重连的完整工程化实现
7. **代码质量工程化**：122 单元测试 + 0 反射 + 0 泛化异常 + 0 静态可变状态

### 针对组员个人的预备问题

#### 傅钧烨（蓝牙/网络）
- **Q**: 蓝牙4人联机的HOST端是如何管理多个客户端连接的？
- **A**: 使用 Map<deviceAddress, SenderReceiverPair> 管理多路连接；deviceToPlayerId 映射设备地址到玩家ID(P2/P3/P4)；广播时遍历所有通道，单播时按playerId查找对应通道；转发时排除发送者(forwardToOtherClients)

#### 陈政昊（核心引擎）
- **Q**: GameEngine如何处理连续3人Pass的逻辑？
- **A**: 每次Pass时 incrementConsecutivePassCount()，当 count>=3 时：setLastPlay(null), clearAllPassStatus(), clearAllLastPlayRecords(), resetConsecutivePassCount()；出牌权转给 lastWinnerId（上一轮最后出牌的玩家）；发布 TurnChangedEvent 通知新回合

#### 曾奕琦（AI系统）
- **Q**: 蒙特卡洛AI如何处理"对手出小牌但不跟牌"的消极游戏问题？
- **A**: 通过 consecutivePassRounds 计数器检测，当连续2轮桌面清空且仍有AI手牌>5时，判定为消极游戏，强制出最大单张打破僵局。同时残局阶段(对手≤2张)通过findAnyValidBeatPlay()从单张、对子、三张、炸弹、五张牌型逐层寻找任何能压的合法出牌

#### 张瀚月（UI/UX）
- **Q**: C位视角是如何实现的？
- **A**: GameController.getGameViewData() 中通过 reorderPlayersForSelf() 方法，找到 myPlayerId 的索引位置，以该位置为起点重新排列 players 列表(循环移位)。UI层按重排后的顺序渲染：第0位=自己(底部C位)，第1位=左侧，第2位=上方，第3位=右侧

#### 盛进（组长）
- **Q**: 如何保证UML设计与最终代码的一致性？
- **A**: Sprint 4 设置了专门的"UML与代码一致性核对"任务卡片，对照Sprint 2的领域模型图和类图，逐一检查 model 包中实体是否缺失/多余，类名/方法/关系是否一致。产出差异清单+用例追溯汇总表。后续通过事件总线重构保持架构清晰

---

## 七、答辩总结词

本次实验，我们团队5人历时10周、5个Sprint迭代，完成了锄大地蓝牙联机卡牌游戏的完整开发。

**核心收获**：

1. **软件工程全流程实践**：从需求分析(用例建模/Vision) → 系统设计(领域模型/类图/顺序图/操作契约) → 编码实现(Java/Android) → 测试交付(单元测试/蓝牙联调/验收)，完整经历了一个软件项目的生命周期。

2. **UML建模与代码的紧密结合**：我们深刻体会到了"先建模后编码"的价值——Sprint 2的类图直接指导了Sprint 3的数据模型实现，系统顺序图指导了调用链设计，避免了走弯路。

3. **设计模式的实战应用**：策略模式让AI系统开闭原则扩展、观察者模式让三大模块解耦、Builder模式让规则配置灵活——设计模式在真实项目中的价值远超课本上的理解。

4. **AI工具赋能开发**：我们将大语言模型（Vivo蓝心）集成进产品做对手风格分析，使用AI代码引擎（Claude Code/Copilot）辅助开发全流程，是"AI时代的软件工程"的一次完整实践。

5. **团队协作**：通过Sprint计划、任务卡片、GitHub协作、Code Review等敏捷实践，让5个人在并行开发中保持方向一致、质量可控。

**展望**：后续可继续完善积分段位排位系统、实时文字/表情交流、更多AI策略（强化学习/深度学习），以及跨平台（iOS/Web）扩展。谢谢各位老师！

# 开发任务卡片

##说明
- 卡片2/3/4 的“类结构骨架”必须在周四前完成
- 不要求完整实现，但必须可编译 + 可调用

## 卡片1：命名规范与接口文档冻结
- 负责人：M6 盛进
- 协作：全员

**内容**
- 建立 Naming-Convention.md
- 建立 Interface-Contract.md
- 冻结核心类名、方法名、DTO 名

**验收**
- 所有人开发前看到同一份规范
- 不再出现随意命名

---

## 卡片2：核心数据模型落地
- 负责人：M1 全员参与
- 主导：傅钧烨
- 协作：陈政昊、曾奕琦

**内容**
- 落地 Card / Player / Play / GameState
- 与类图保持一致

**验收**
- 所有 model 类编译通过
- 字段命名统一

---

## 卡片3：游戏主流程实现
- 负责人：M3 陈政昊
- 协作：曾奕琦

**内容**
- GameEngine
- TurnManager
- SettlementManager

**验收**
- 可以初始化游戏
- 可以轮转玩家
- 可以判断结束

---

## 卡片4：规则系统初稿
- 负责人：M5 曾奕琦
- 协作：陈政昊

**内容**
- PatternRecognizer
- PlayValidator
- RuleEngine
- 本周规则范围
  - 单张
  - 对子
  - 首轮方块3
  - 压过上家
  - Pass

**验收**
- 有 validatePlay() 可调用
- 有 recognizePattern() 可调用

---

## 卡片5：发牌系统与初始化
- 负责人：M3 陈政昊
- 协作：傅钧烨

**内容**
- DealManager
- shuffle
- deal
- mark first player

**验收**
- 每位玩家 13 张牌
- 正确找到方块3持有者

---

## 卡片6：基础 UI 页面
- 负责人：M2 张瀚月
- 协作：盛进

**内容**
- GameActivity
- 手牌展示
- 当前玩家显示
- 出牌按钮
- Pass 按钮

**验收**
- 页面可展示核心信息
- 能触发 controller 方法

---

## 卡片7：UI-逻辑连接
- 负责人：M6 张瀚月
- 协作：傅钧烨、陈政昊

**内容**
- GameController
- GameActionHandler
- GameViewData 映射

**验收**
- UI 点击可驱动逻辑
- 状态更新能回显到 UI

**说明**
- GameController 和 GameActionHandler 由该卡片统一实现
- UI 层只能调用该接口，不允许绕过

---

## 卡片8：联机模块占位接口
- 负责人：M4 傅钧烨
- 协作：盛进

**内容**
- 先不做完整蓝牙
- 先定义后续可接入接口
```java
public interface MultiplayerGateway {
    void sendPlayAction(Play play);
    void sendPassAction(String playerId);
    void syncGameState(GameState gameState);
}
```

**验收**
- 有接口文档
- 后续可扩展，不影响本周逻辑

---

## 卡片9：简单 AI 占位
- 负责人：M5 曾奕琦
- 协作：傅钧烨

**内容**
- 简单托管策略
- 没有更优解，只求能出牌

**验收**
- AI 能参与测试局

---

## 卡片10：联调与日志
- 负责人：M6 盛进
- 协作：全员

**内容**
- 输出发牌结果
- 输出玩家回合
- 输出出牌结果
- 输出结束结果

**验收**
- 演示时能看到清晰运行过程

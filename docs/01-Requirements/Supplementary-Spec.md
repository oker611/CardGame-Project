# Supplementary Specification

> FURPS+ 分类的非功能性需求与补充规约

## 功能性 (Functionality)

1. 支持总数为 4 的玩家游戏，玩家类型支持 Human / AI / Remote 三种
2. 支持玩家使用 AI 托管自动出牌，提供 EASY / MEDIUM / HARD / ADAPTIVE 四种难度
3. 支持单机模式和蓝牙联机模式切换
4. 支持南方规则（♦3首出必选）和北方规则（无首轮限制+花色颠倒）双规则体系
5. 支持记牌器、牌型提示、透视三种可选道具

## 可用性 (Usability)

6. 人类玩家无合法牌时 3 秒自动倒计时过牌
7. 出牌和过牌操作提供即时 Toast 反馈
8. 游戏结束时弹出排名对话框，展示各玩家剩余牌数

## 可靠性 (Reliability)

9. 蓝牙心跳保活机制：5 秒间隔发送 HEARTBEAT，15 秒无响应判定超时
10. ACK 可靠投递：关键消息（INIT_GAME / PLAY_ACTION / PASS_ACTION / GAME_OVER）需确认，3 秒超时最多 3 次重试
11. 断线重连：CLIENT 端自动重连，指数退避（2/4/8/16/30 秒），HOST 端 serverSocket 持续监听

## 性能 (Performance)

12. 蒙特卡洛 AI 决策超时限制 1.5 秒，动态模拟次数 100-400
13. LLM 风格分析在独立单线程 ExecutorService 中异步执行，不介入实时出牌循环

## 可支持性 (Supportability)

14. 兼容 Android 4.4 (API 19) 及以上版本
15. 蓝牙 RFCOMM 支持安全/不安全双降级策略，兼容主流国产 ROM（小米、华为等）
16. 蒙特卡洛策略内部参数可通过遗传算法离线优化（50 个体 × 200 局 × 50 代）

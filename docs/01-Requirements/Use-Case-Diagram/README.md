# Use Case Diagram

## 生成说明

用例图使用 ProcessOn 或 PlantUML 生成。

### PlantUML 渲染命令
```bash
# 安装 plantuml (需要 Java)
# 在 VS Code 中安装 PlantUML 插件即可预览 .puml 文件
```

### 预期输出
- `use-case-diagram.png` — 包含 4 个 Actor (Human Player, AI Player, Bluetooth Device, Vivo LLM) + 21 个 Use Case

### 用例清单 (基于 UML-7-Diagrams-Prompts.md)
- UC-01 开始游戏, UC-02 出牌, UC-03 过牌, UC-04 查看游戏状态
- UC-05 游戏结算, UC-06 选择规则, UC-07 选择AI难度
- UC-08 切换道具, UC-09 发牌, UC-10 回合管理
- UC-11 创建蓝牙房间, UC-12 搜索蓝牙设备, UC-13 加入蓝牙房间
- UC-14 蓝牙出牌同步, UC-15 蓝牙过牌同步, UC-16 游戏结束同步
- UC-17 HOST分配角色, UC-18 断线重连
- UC-19 心跳保活, UC-20 对手风格分析, UC-21 自适应AI对抗

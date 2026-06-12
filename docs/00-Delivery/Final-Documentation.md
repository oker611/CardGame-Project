# Final Documentation — 锄大地蓝牙联机卡牌游戏

> 最终交付文档索引 | 更新时间：2026-06-12

## 文档导航

| 阶段 | 目录 | 核心文件 |
|------|------|---------|
| 第1周 Inception | `01-Requirements/` | Use-Case-Model, Vision, Supplementary-Spec, Glossary |
| 第2周 Elaboration | `02-Design/` | Domain-Model, SSD, Operation-Contracts, Class-Diagram, Package-Diagrams, State-Machine-Diagrams |
| 第3周 Construction | `03-Plans/Sprint3/` | Iteration-Plan, Milestones, Interface-Contract, Project-Structure |
| 第4周 Stabilization | `03-Plans/Sprint4/` + `04-Refactoring/` | 事件框架, 设计模式报告, 旧代码切除 |
| 第5周 Final Delivery | `00-Delivery/` + `05-Model-Refinement/` | 终版检查表, 模型精化日志 |
| 第6周 Defense | `06-Defense/` | 答辩PPT, Defense-Materials |

## 项目规模

- **代码**：约 100 个 Java 类，11 个功能包
- **测试**：123 个用例，0 失败
- **接口**：14 个（含 DI 注入体系）
- **设计模式**：6 种（策略、观察者、工厂、适配器、Builder、单例）

## 关键设计决策

1. **事件驱动架构**：`IEventBus` + `GameEventListener` 解耦 UI / AI / 蓝牙
2. **依赖注入**：构造器注入替代直接 `new`，14 个接口支撑完整 DI 体系
3. **异常精确化**：消除所有泛化 `catch(Exception)`，仅保留合理的回调隔离
4. **AI 三层架构**：Greedy → MonteCarlo → Adaptive，工厂模式统一创建

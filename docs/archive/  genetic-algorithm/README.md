# 离线遗传算法模块

> 用于优化锄大地AI策略参数的离线训练工具

---

## 一、概述

本模块实现了一个**离线遗传算法训练系统**，用于自动优化蒙特卡洛AI的决策参数。虽然最终未在主项目中集成使用，但作为探索性研究成果保留。

### 设计目标

- 通过遗传算法自动搜索最优AI参数组合
- 支持两种训练模式：强AI对战、种群自玩
- 输出可直接用于游戏的参数配置

---

## 二、核心组件

### 2.1 Chromosome（染色体）

染色体编码了蒙特卡洛AI的12个关键参数：

| 参数名 | 类型 | 范围 | 作用 |
|--------|------|------|------|
| `numSamples` | int | 5-10 | 蒙特卡洛模拟样本数 |
| `topKCandidates` | int | 3-5 | 候选动作截取数量 |
| `earlyBigCardBonus` | double | 0-2 | 开局大牌奖励系数 |
| `fiveCardPenalty` | double | -1~1 | 五张牌型惩罚系数 |
| `midSuppressBonus` | double | 0-1 | 中盘压制奖励系数 |
| `lateFastBonus` | double | 0-3 | 残局快出奖励系数 |
| `aggression` | double | 0-1 | 进攻性因子 |
| `defense` | double | 0-1 | 防守性因子 |
| `lateTwoBonus` | double | 0-2 | 残局单2奖励系数 |
| `straightKeepBonus` | double | 0-1 | 开局保留顺子倾向 |
| `midControlThreshold` | double | 0-1 | 中盘争牌权阈值 |
| `simulatedAggression` | double | 0-1 | 模拟对手激进程度 |

### 2.2 遗传操作

**选择**：精英保留 + 锦标赛选择

```java
// 精英保留
for (int i = 0; i < elitismCount; i++) {
    parents.add(population.get(topIndices.get(i)));
}

// 锦标赛选择（随机选择3个个体，返回适应度最高的）
```

**交叉**：单点交叉

```java
public static Chromosome crossover(Chromosome p1, Chromosome p2) {
    int point = random.nextInt(12);  // 在12个参数中随机选择交叉点
    // 前半部分来自p1，后半部分来自p2
}
```

**变异**：高斯变异

```java
public void mutate(double rate) {
    for (int i = 0; i < 12; i++) {
        if (random.nextDouble() < rate) {
            double delta = (random.nextDouble() - 0.5) * 0.3;
            setParam(i, clamp(getParam(i) + delta, i));
        }
    }
}
```

---

## 三、训练模式

### 3.1 强AI对战模式 (`GeneticAlgorithm`)

**原理**：每个个体与固定的强AI对战评估适应度

**流程**：
初始化种群 → 评估（与强AI对战）→ 选择 → 交叉变异 → 迭代

初始化种群 → 循环对战 → 计算排名和得分 → 选择 → 交叉变异 → 迭代

offline-ga/
├── src/main/java/com/example/cardgame/ga/
│   ├── Chromosome.java           # 染色体定义
│   ├── GeneticAlgorithm.java     # 基础遗传算法
│   ├── SelfPlayGeneticAlgorithm.java  # 自玩式遗传算法
│   ├── FitnessEvaluator.java     # 强AI对战评估器
│   ├── SelfPlayFitnessEvaluator.java  # 自玩评估器
│   ├── Tournament.java           # 游戏锦标赛
│   ├── GameMetrics.java          # 游戏指标收集
│   ├── OfflineMonteCarloStrategy.java  # 离线蒙特卡洛策略
│   └── Main.java                 # 入口类
├── src/test/java/                # 测试代码
└── pom.xml                       # Maven配置

```

---

## 七、实验结果示例

### 参数优化效果

| 指标 | 优化前 | 优化后 | 提升 |
|------|--------|--------|------|
| 胜率 | 45% | 58% | +13% |
| 平均排名 | 2.1 | 1.7 | -0.4 |
| 出牌效率 | 中等 | 较高 | 明显提升 |

### 最佳参数示例

```java
// 经过50代训练后的最优参数
Chromosome best = new Chromosome(
    8,      // numSamples
    4,      // topKCandidates
    0.8,    // earlyBigCardBonus
    -0.3,   // fiveCardPenalty
    0.4,    // midSuppressBonus
    1.2,    // lateFastBonus
    0.6,    // aggression
    0.5,    // defense
    1.5,    // lateTwoBonus
    0.3,    // straightKeepBonus
    0.6,    // midControlThreshold
    0.5     // simulatedAggression
);
```

---

## 八、技术价值

虽然本模块未投入实际使用，但具有以下价值：

1. **研究价值**：探索了使用遗传算法优化博弈AI的可行性
2. **教育价值**：展示了完整的遗传算法实现流程
3. **扩展潜力**：未来可作为在线学习模块的基础
4. **参数指导**：训练结果为手动调参提供了参考

---

## 九、未来扩展方向

- [ ] 集成到主项目，实现在线学习
- [ ] 引入强化学习算法
- [ ] 实现参数自动导入机制
- [ ] 添加可视化训练界面

```

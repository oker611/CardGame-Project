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

```
初始化种群 → 评估（与强AI对战）→ 选择 → 交叉变异 → 迭代
```

**适用场景**：快速评估基础性能

### 3.2 种群自玩模式 (`SelfPlayGeneticAlgorithm`)

**原理**：种群内所有个体两两对战，形成锦标赛

**流程**：

```
初始化种群 → 循环对战 → 计算排名和得分 → 选择 → 交叉变异 → 迭代
```

**适用场景**：发现更复杂的策略，避免过拟合到固定对手

**适应度计算**：

```java
public double getFitness() {
    double rankScore = (totalPlayers - rank + 1) / (double) totalPlayers;
    double normalizedScore = avgScore / 3.0;
    double aggressionBonus = avgAggression * 0.3;
    double bombBonus = avgBomb * 0.2;
    double twoBonus = avgTwo * 0.1;
    return 0.4 * rankScore + 0.3 * normalizedScore + 
           aggressionBonus + bombBonus + twoBonus;
}
```

---

## 四、未投入使用的原因

### 技术原因

1. **计算开销过大**：
   - 单次评估需要进行多场完整游戏模拟
   - 完整训练需要数小时甚至数天
   - 不适合移动端实时决策场景

2. **参数空间复杂**：
   - 12个参数的组合空间巨大
   - 容易陷入局部最优

3. **蒙特卡洛已足够**：
   - 手动调参的蒙特卡洛AI在实际对战中表现良好
   - 进一步优化收益递减

### 工程原因

1. **复杂度增加**：
   - 引入遗传算法增加了系统复杂度
   - 维护成本上升

2. **部署困难**：
   - 训练好的参数需要手动导入
   - 无法实现真正的自适应学习

---

## 五、使用方法

### 5.1 编译运行

```bash
cd offline-ga
mvn clean compile
mvn exec:java -Dexec.mainClass="com.example.cardgame.ga.Main"
```

### 5.2 示例代码

```java
// 创建遗传算法实例
GeneticAlgorithm ga = new GeneticAlgorithm(
    50,    // 种群大小
    0.1,   // 变异率
    0.7,   // 交叉率
    5      // 精英数量
);

// 运行100代训练
Chromosome best = ga.run(100);

// 输出最优参数
System.out.println("最佳参数: " + Arrays.toString(best.toArray()));
```

### 5.3 自玩模式示例

```java
SelfPlayGeneticAlgorithm ga = new SelfPlayGeneticAlgorithm(
    30,    // 种群大小
    0.15,  // 变异率
    0.6,   // 交叉率
    3      // 精英数量
);

Chromosome best = ga.run(50);
```

---

## 六、目录结构

```
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

## 七、实验结果示例

### 参数优化效果

| 指标 | 优化前 | 优化后 | 提升 |
|------|--------|--------|------|
| 胜率 | 45% | 58% | +13% |
| 平均排名 | 2.1 | 1.7 | -0.4 |
| 出牌效率 | 中等 | 较高 | 明显提升 |

### 最佳参数示例

```java
// 经过50代训练后的最优参数
Chromosome best = new Chromosome(
    8,      // numSamples
    4,      // topKCandidates
    0.8,    // earlyBigCardBonus
    -0.3,   // fiveCardPenalty
    0.4,    // midSuppressBonus
    1.2,    // lateFastBonus
    0.6,    // aggression
    0.5,    // defense
    1.5,    // lateTwoBonus
    0.3,    // straightKeepBonus
    0.6,    // midControlThreshold
    0.5     // simulatedAggression
);
```

---

## 八、技术价值

虽然本模块未投入实际使用，但具有以下价值：

1. **研究价值**：探索了使用遗传算法优化博弈AI的可行性
2. **教育价值**：展示了完整的遗传算法实现流程
3. **扩展潜力**：未来可作为在线学习模块的基础
4. **参数指导**：训练结果为手动调参提供了参考

---

## 九、未来扩展方向

- [ ] 集成到主项目，实现在线学习
- [ ] 引入强化学习算法
- [ ] 实现参数自动导入机制
- [ ] 添加可视化训练界面

---

## License

MIT License

*Last updated: June 2026*

# Domain Model Diagram

## 生成说明

领域模型图使用 ProcessOn 或 draw.io 绘制，导出为 PNG。

### 核心实体关系

```
GameState (1) ──────────── (*) Player
                           │
                           ├── handCards: List<Card>
                           ├── type: PlayerType (HUMAN / AI / REMOTE)
                           └── playerProfile: HumanStyleProfile / AIPlayerProfile

Card (值对象)
  ├── rank: Rank (THREE..TWO, ACE)
  └── suit: Suit (DIAMOND, CLUB, HEART, SPADE)

Play
  ├── playerId: String
  ├── cards: List<Card>
  └── pattern: CardPattern (SINGLE, PAIR, ... STRAIGHT_FLUSH)
```

### 预期输出
- `domain-model.png` — 包含上述实体及关联的完整领域模型图

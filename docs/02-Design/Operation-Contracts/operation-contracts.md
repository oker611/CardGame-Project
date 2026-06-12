# Operation Contracts — 锄大地卡牌游戏

> 基于 Larman 操作契约格式

---

## OC-01: playCards (出牌)

**Operation:** `playCards(playerId: String, selectedCardIds: List<String>)`

**Cross References:** Use Case UC-02 出牌

**Preconditions:**
- 游戏未结束 (gameState.isGameOver() == false)
- 当前回合属于 playerId
- selectedCardIds 非空，且是 playerId 手牌的子集

**Postconditions:**
1. 若首轮首出且含必出牌(♦3): 校验通过
2. 若上家有出牌: 本次出牌牌型 >= 上家牌型比较值
3. playerId 的手牌移除 selectedCardIds
4. lastPlay 更新为本次出牌
5. consecutivePassCount 重置为 0
6. openingTurn 设为 false
7. SettlementManager 检查是否有玩家手牌清空
8. 若未结束: TurnManager 切换到下一玩家
9. 发布 CardPlayedEvent + TurnChangedEvent

---

## OC-02: passTurn (过牌)

**Operation:** `passTurn(playerId: String)`

**Cross References:** Use Case UC-03 过牌

**Preconditions:**
- 游戏未结束
- 当前回合属于 playerId
- playerId 非首轮起始玩家 (首轮起始不可 pass)

**Postconditions:**
1. player.passed 设为 true
2. consecutivePassCount 递增
3. 若 consecutivePassCount >= 3:
   - lastPlay 清空
   - 所有玩家 passed 状态清除
   - 出牌权转给 lastWinnerId
4. 否则: TurnManager 切换到下一玩家
5. 发布 PlayerPassedEvent

---

## OC-03: dealCards (发牌)

**Operation:** `dealCards(gameState: GameState)`

**Cross References:** Use Case UC-09 发牌

**Preconditions:**
- gameState 已创建，玩家列表已配置

**Postconditions:**
1. 生成 52 张标准牌 (13 × 4)
2. Collections.shuffle 随机洗牌
3. 4 名玩家各分配 13 张
4. 识别持有 ♦3 的玩家为首发 (openingPlayer)
5. 设置 isOpeningTurn = true, isFirstRound = true

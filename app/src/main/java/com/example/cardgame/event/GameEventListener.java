package com.example.cardgame.event;

/**
 * 游戏事件监听器。
 *
 * 实现者可覆写 onEvent（通用）或具体事件方法（类型安全）。
 * 默认实现将具体事件方法委托给 onEvent，保证向后兼容。
 */
public interface GameEventListener {

    /** 通用事件入口（必须实现） */
    void onEvent(GameEvent event);

    /** 类型安全的出牌事件回调 */
    default void onCardPlayed(CardPlayedEvent event) { onEvent(event); }

    /** 类型安全的过牌事件回调 */
    default void onPlayerPassed(PlayerPassedEvent event) { onEvent(event); }

    /** 类型安全的回合切换回调 */
    default void onTurnChanged(TurnChangedEvent event) { onEvent(event); }

    /** 类型安全的游戏结束回调 */
    default void onGameOver(GameOverEvent event) { onEvent(event); }
}

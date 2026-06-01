package com.example.cardgame.controller;

import android.util.Log;

import com.example.cardgame.engine.GameEngine;
import com.example.cardgame.event.CardPlayedEvent;
import com.example.cardgame.event.EventBus;
import com.example.cardgame.event.GameEvent;
import com.example.cardgame.event.GameEventListener;
import com.example.cardgame.event.GameOverEvent;
import com.example.cardgame.event.PlayerPassedEvent;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Play;
import com.example.cardgame.model.Player;
import com.example.cardgame.model.PlayerType;
import com.example.cardgame.network.BluetoothGateway;

/**
 * 阶段一：蓝牙模块事件中继。
 * 监听 GameEngine 发布的游戏事件，将需要跨设备同步的事件
 * 转发到 BluetoothGateway 发送给远端玩家。
 *
 * 设计原则：只增不删，不修改任何已有文件的核心逻辑。
 */
public class BluetoothEventRelay implements GameEventListener {

    private static final String TAG = "CardGame";

    private final BluetoothGateway gateway;
    private final GameEngine gameEngine;
    private volatile boolean registered;

    public BluetoothEventRelay(BluetoothGateway gateway, GameEngine gameEngine) {
        this.gateway = gateway;
        this.gameEngine = gameEngine;
    }

    /**
     * 注册到全局事件总线，开始接收游戏事件。
     * 可重入安全：重复调用不会重复注册。
     */
    public void register() {
        if (registered) {
            Log.w(TAG, "[EVENT] BluetoothEventRelay already registered, skipping duplicate");
            return;
        }
        EventBus.getInstance().register(this);
        registered = true;
        Log.i(TAG, "[EVENT] BluetoothEventRelay registered to EventBus");
    }

    /**
     * 从事件总线取消注册。应在不再需要蓝牙同步时调用。
     */
    public void unregister() {
        if (!registered) {
            return;
        }
        EventBus.getInstance().unregister(this);
        registered = false;
        Log.i(TAG, "[EVENT] BluetoothEventRelay unregistered from EventBus");
    }

    @Override
    public void onEvent(GameEvent event) {
        if (event instanceof CardPlayedEvent) {
            handleCardPlayed((CardPlayedEvent) event);
        } else if (event instanceof PlayerPassedEvent) {
            handlePlayerPassed((PlayerPassedEvent) event);
        } else if (event instanceof GameOverEvent) {
            handleGameOver((GameOverEvent) event);
        }
        // TurnChangedEvent 不需要蓝牙同步，忽略
    }

    private void handleCardPlayed(CardPlayedEvent event) {
        GameState state = gameEngine.getGameState();
        if (state == null) {
            Log.w(TAG, "[EVENT] CardPlayedEvent ignored: GameState is null");
            return;
        }

        Player player = state.getPlayerById(event.getPlayerId());
        if (player == null) {
            Log.w(TAG, "[EVENT] CardPlayedEvent ignored: player not found for playerId="
                    + event.getPlayerId());
            return;
        }

        // 守卫：跳过 REMOTE 玩家的出牌，防止 executeRemotePlay 触发的回声
        if (player.getType() == PlayerType.REMOTE) {
            Log.d(TAG, "[EVENT] CardPlayedEvent skipped: remote player ("
                    + event.getPlayerId() + "), play came from network");
            if (gateway.isHost()) {
                gateway.syncGameState(state);
            }
            return;
        }

        Play lastPlay = state.getLastPlay();
        if (lastPlay == null) {
            Log.w(TAG, "[EVENT] CardPlayedEvent ignored: lastPlay is null for playerId="
                    + event.getPlayerId());
            return;
        }

        Log.i(TAG, "[EVENT] BluetoothEventRelay: CardPlayedEvent → sendPlayAction"
                + " playerId=" + event.getPlayerId()
                + " cardCount=" + event.getPlayedCardIds().size());
        gateway.sendPlayAction(lastPlay);
        if (gateway.isHost()) {
            gateway.syncGameState(state);
        }
    }

    private void handlePlayerPassed(PlayerPassedEvent event) {
        GameState state = gameEngine.getGameState();
        if (state == null) {
            Log.w(TAG, "[EVENT] PlayerPassedEvent ignored: GameState is null");
            return;
        }

        Player player = state.getPlayerById(event.getPlayerId());
        if (player == null) {
            Log.w(TAG, "[EVENT] PlayerPassedEvent ignored: player not found for playerId="
                    + event.getPlayerId());
            return;
        }

        // 守卫：跳过 REMOTE 玩家的过牌，防止 executeRemotePass 触发的回声
        if (player.getType() == PlayerType.REMOTE) {
            Log.d(TAG, "[EVENT] PlayerPassedEvent skipped: remote player ("
                    + event.getPlayerId() + "), pass came from network");
            if (gateway.isHost()) {
                gateway.syncGameState(state);
            }
            return;
        }

        Log.i(TAG, "[EVENT] BluetoothEventRelay: PlayerPassedEvent → sendPassAction"
                + " playerId=" + event.getPlayerId());
        gateway.sendPassAction(event.getPlayerId());
        if (gateway.isHost()) {
            gateway.syncGameState(state);
        }
    }

    private void handleGameOver(GameOverEvent event) {
        // 守卫：只有 HOST 负责广播游戏结束，CLIENT 不中继
        if (!gateway.isHost()) {
            Log.d(TAG, "[EVENT] GameOverEvent skipped: not host, game over relayed by HOST only");
            return;
        }

        String winnerId = event.getWinnerId();
        String winnerName = winnerId;

        GameState state = gameEngine.getGameState();
        if (state != null) {
            Player winner = state.getPlayerById(winnerId);
            if (winner != null && winner.getPlayerName() != null) {
                winnerName = winner.getPlayerName();
            }
        }

        Log.i(TAG, "[EVENT] BluetoothEventRelay: GameOverEvent → sendGameOver"
                + " winnerId=" + winnerId + " winnerName=" + winnerName);
        gateway.sendGameOver(winnerId, winnerName);
    }
}

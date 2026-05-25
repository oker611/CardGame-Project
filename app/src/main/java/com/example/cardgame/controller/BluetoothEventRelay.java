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

    public BluetoothEventRelay(BluetoothGateway gateway, GameEngine gameEngine) {
        this.gateway = gateway;
        this.gameEngine = gameEngine;
    }

    /**
     * 注册到全局事件总线，开始接收游戏事件。
     */
    public void register() {
        EventBus.getInstance().register(this);
        Log.i(TAG, "[EVENT] BluetoothEventRelay registered to EventBus");
    }

    /**
     * 从事件总线取消注册。应在不再需要蓝牙同步时调用。
     */
    public void unregister() {
        EventBus.getInstance().unregister(this);
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
        // 守卫：只中继本机玩家的出牌，防止 executeRemotePlay 触发的回声
        if (!event.getPlayerId().equals(gateway.getLocalPlayerId())) {
            Log.d(TAG, "[EVENT] CardPlayedEvent skipped: not local player ("
                    + event.getPlayerId() + " != " + gateway.getLocalPlayerId() + ")");
            return;
        }

        GameState state = gameEngine.getGameState();
        if (state == null) {
            Log.w(TAG, "[EVENT] CardPlayedEvent ignored: GameState is null");
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
    }

    private void handlePlayerPassed(PlayerPassedEvent event) {
        // 守卫：只中继本机玩家的过牌，防止 executeRemotePass 触发的回声
        if (!event.getPlayerId().equals(gateway.getLocalPlayerId())) {
            Log.d(TAG, "[EVENT] PlayerPassedEvent skipped: not local player ("
                    + event.getPlayerId() + " != " + gateway.getLocalPlayerId() + ")");
            return;
        }

        Log.i(TAG, "[EVENT] BluetoothEventRelay: PlayerPassedEvent → sendPassAction"
                + " playerId=" + event.getPlayerId());
        gateway.sendPassAction(event.getPlayerId());
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

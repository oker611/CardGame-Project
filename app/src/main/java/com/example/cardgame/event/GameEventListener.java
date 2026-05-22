// 【已修改，引入观察者模式基础框架】
package com.example.cardgame.event;

public interface GameEventListener {
    void onEvent(GameEvent event);
}

package com.example.cardgame.llm;

import com.example.cardgame.llm.model.ChatMessage;
import java.io.IOException;
import java.util.List;

public interface IVivoLLMClient {
    String chat(List<ChatMessage> messages) throws IOException;
}

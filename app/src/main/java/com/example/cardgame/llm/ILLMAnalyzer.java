package com.example.cardgame.llm;

import java.io.IOException;

public interface ILLMAnalyzer {
    String summarizeOpponentStyle(String playHistory) throws IOException;
}

package com.example.cardgame.llm;

import android.util.Log;

import com.example.cardgame.BuildConfig;
import com.example.cardgame.llm.model.ChatMessage;
import com.example.cardgame.llm.model.DeepSeekResponse;
import com.example.cardgame.llm.model.VivoLLMRequest;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@SuppressWarnings("SpellCheckingInspection")
public class VivoLLMClient {
    private static final String API_URL = "https://api-ai.vivo.com.cn/v1/chat/completions";
    private static final String APP_KEY = BuildConfig.VIVO_APP_KEY;

    private final OkHttpClient client;
    private final Gson gson;

    public VivoLLMClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        Log.d("VivoLLMClient", "APP_KEY length: " + (APP_KEY == null ? 0 : APP_KEY.length()));
    }

    @SuppressWarnings("SpellCheckingInspection")
    public String chat(List<ChatMessage> messages) throws IOException {
        VivoLLMRequest requestBody = new VivoLLMRequest();
        requestBody.setModel("Volc-DeepSeek-V3.2");
        requestBody.setMessages(messages);
        requestBody.setReasoningEffort("minimal");
        requestBody.setStream(false);

        String json = gson.toJson(requestBody);
        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + APP_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "null";
                throw new IOException("Unexpected code " + response + ", body: " + errorBody);
            }
            String responseBody = response.body() != null ? response.body().string() : null;
            if (responseBody == null) {
                throw new IOException("Empty response body from vivo API");
            }
            DeepSeekResponse deepSeekResp = gson.fromJson(responseBody, DeepSeekResponse.class);
            if (deepSeekResp == null || deepSeekResp.getContent() == null) {
                throw new IOException("Empty response from vivo API");
            }
            return deepSeekResp.getContent();
        }
    }
}
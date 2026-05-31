package com.example.cardgame.ai;

import android.util.Log;

import com.example.cardgame.llm.LLMAnalyzer;
import com.example.cardgame.model.HumanStyleProfile;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class HumanStyleAnalyzer {
    private static final String TAG = "HumanStyleAnalyzer";
    
    private static volatile ExecutorService executor;
    private static final Object LOCK = new Object();
    
    private final LLMAnalyzer llmAnalyzer;
    private WeakReference<StyleAnalysisCallback> callbackRef;
    private Future<?> currentTask;
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

    public interface StyleAnalysisCallback {
        void onAnalysisComplete(HumanStyleProfile profile);
        void onAnalysisFailed(String error);
    }

    private static ExecutorService getExecutor() {
        if (executor == null || executor.isShutdown()) {
            synchronized (LOCK) {
                if (executor == null || executor.isShutdown()) {
                    Log.d(TAG, "Creating new executor thread pool");
                    executor = Executors.newSingleThreadExecutor(r -> {
                        Thread t = new Thread(r, "HumanStyleAnalyzer-Thread");
                        t.setDaemon(true);
                        return t;
                    });
                }
            }
        }
        return executor;
    }

    public HumanStyleAnalyzer() {
        this.llmAnalyzer = new LLMAnalyzer();
        Log.d(TAG, "HumanStyleAnalyzer instance created");
    }

    public void setCallback(StyleAnalysisCallback callback) {
        this.callbackRef = new WeakReference<>(callback);
        Log.d(TAG, "Callback set: " + (callback != null ? callback.getClass().getSimpleName() : "null"));
    }

    public void analyzeStyleAsync(String playerId, List<String> playHistory, HumanStyleProfile existingProfile) {
        Log.d(TAG, "=== analyzeStyleAsync START ===");
        Log.d(TAG, "playerId: " + playerId);
        Log.d(TAG, "playHistory size: " + (playHistory != null ? playHistory.size() : "null"));
        Log.d(TAG, "existingProfile: " + (existingProfile != null ? existingProfile.getStyleLabel() : "null"));
        Log.d(TAG, "isShutdown: " + isShutdown.get());
        
        if (isShutdown.get()) {
            Log.w(TAG, "Analyzer has been shut down, cannot analyze");
            notifyFailed("Analyzer has been shut down");
            return;
        }

        if (currentTask != null && !currentTask.isDone()) {
            Log.d(TAG, "Cancelling previous running task");
            currentTask.cancel(true);
        }

        Log.d(TAG, "Submitting analysis task to executor...");
        currentTask = getExecutor().submit(() -> {
            Log.d(TAG, "[Thread] Analysis task started on thread: " + Thread.currentThread().getName());
            
            if (Thread.currentThread().isInterrupted()) {
                Log.w(TAG, "[Thread] Task interrupted before starting");
                return;
            }
            
            try {
                // Step 1: Format history
                Log.d(TAG, "[Step 1] Formatting play history...");
                String historySummary = formatHistory(playHistory);
                Log.d(TAG, "[Step 1] History summary length: " + historySummary.length());
                Log.d(TAG, "[Step 1] History summary: " + (historySummary.length() > 200 ? historySummary.substring(0, 200) + "..." : historySummary));
                
                if (historySummary.isEmpty()) {
                    Log.w(TAG, "[Step 1] History summary is empty, aborting");
                    notifyFailed("No play history available");
                    return;
                }

                if (Thread.currentThread().isInterrupted()) {
                    Log.w(TAG, "[Thread] Task interrupted after formatting history");
                    return;
                }

                // Step 2: Call LLM API
                Log.d(TAG, "[Step 2] Calling LLM API...");
                long startTime = System.currentTimeMillis();
                String llmResponse = null;
                try {
                    llmResponse = llmAnalyzer.summarizeOpponentStyle(historySummary);
                    long elapsed = System.currentTimeMillis() - startTime;
                    Log.d(TAG, "[Step 2] LLM API call completed in " + elapsed + "ms");
                    Log.d(TAG, "[Step 2] LLM response length: " + (llmResponse != null ? llmResponse.length() : "null"));
                    Log.d(TAG, "[Step 2] LLM response: " + (llmResponse != null && llmResponse.length() > 500 ? llmResponse.substring(0, 500) + "..." : llmResponse));
                } catch (Exception e) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    Log.e(TAG, "[Step 2] LLM API call FAILED after " + elapsed + "ms: " + e.getMessage(), e);
                    throw e;
                }
                
                if (Thread.currentThread().isInterrupted()) {
                    Log.w(TAG, "[Thread] Task interrupted after LLM call");
                    return;
                }

                // Step 3: Parse response
                Log.d(TAG, "[Step 3] Parsing LLM response...");
                HumanStyleProfile profile = parseLLMResponse(playerId, llmResponse, existingProfile);
                Log.d(TAG, "[Step 3] Parsed profile - style: " + profile.getStyleLabel() + 
                      ", aggressiveness: " + profile.getAggressivenessScore() +
                      ", conservativeness: " + profile.getConservativenessScore() +
                      ", gamesAnalyzed: " + profile.getGamesAnalyzed());
                
                // Step 4: Notify callback
                Log.d(TAG, "[Step 4] Notifying callback with success result");
                notifyComplete(profile);
                Log.d(TAG, "=== analyzeStyleAsync COMPLETED SUCCESSFULLY ===");
                
            } catch (Exception e) {
                Log.e(TAG, "[ERROR] Exception during analysis: " + e.getClass().getSimpleName() + " - " + e.getMessage(), e);
                if (!Thread.currentThread().isInterrupted()) {
                    notifyFailed(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                Log.d(TAG, "=== analyzeStyleAsync FAILED ===");
            }
        });
        
        Log.d(TAG, "Analysis task submitted to executor");
    }

    private void notifyComplete(HumanStyleProfile profile) {
        Log.d(TAG, "notifyComplete() called");
        StyleAnalysisCallback callback = callbackRef != null ? callbackRef.get() : null;
        if (callback != null) {
            Log.d(TAG, "Callback available, calling onAnalysisComplete()");
            try {
                callback.onAnalysisComplete(profile);
                Log.d(TAG, "onAnalysisComplete() callback executed successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error in onAnalysisComplete callback: " + e.getMessage(), e);
            }
        } else {
            Log.w(TAG, "Callback is null or has been garbage collected");
        }
    }

    private void notifyFailed(String error) {
        Log.d(TAG, "notifyFailed() called with error: " + error);
        StyleAnalysisCallback callback = callbackRef != null ? callbackRef.get() : null;
        if (callback != null) {
            Log.d(TAG, "Callback available, calling onAnalysisFailed()");
            try {
                callback.onAnalysisFailed(error);
                Log.d(TAG, "onAnalysisFailed() callback executed successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error in onAnalysisFailed callback: " + e.getMessage(), e);
            }
        } else {
            Log.w(TAG, "Callback is null or has been garbage collected, cannot notify failure");
        }
    }

    private String formatHistory(List<String> playHistory) {
        Log.d(TAG, "formatHistory() - input size: " + (playHistory != null ? playHistory.size() : "null"));
        
        if (playHistory == null || playHistory.isEmpty()) {
            Log.d(TAG, "formatHistory() - returning empty string (null or empty input)");
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        int count = Math.min(playHistory.size(), 30);
        Log.d(TAG, "formatHistory() - processing last " + count + " plays");
        
        for (int i = playHistory.size() - count; i < playHistory.size(); i++) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(playHistory.get(i));
        }
        
        String result = sb.toString();
        Log.d(TAG, "formatHistory() - output length: " + result.length());
        return result;
    }

    private HumanStyleProfile parseLLMResponse(String playerId, String response, HumanStyleProfile existing) {
        Log.d(TAG, "parseLLMResponse() - playerId: " + playerId + ", response: " + 
              (response != null ? (response.length() > 100 ? response.substring(0, 100) + "..." : response) : "null"));
        
        HumanStyleProfile profile = existing != null ? existing : new HumanStyleProfile(playerId);
        Log.d(TAG, "parseLLMResponse() - using " + (existing != null ? "existing" : "new") + " profile");
        
        if (response == null || response.isEmpty()) {
            Log.w(TAG, "parseLLMResponse() - response is null or empty, returning default profile");
            return profile;
        }

        String trimmed = response.trim();
        Log.d(TAG, "parseLLMResponse() - trimmed response: " + (trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed));
        
        // Check for style keywords
        boolean hasAggressive = trimmed.contains("激进") || trimmed.toLowerCase().contains("aggressive");
        boolean hasConservative = trimmed.contains("保守") || trimmed.toLowerCase().contains("defensive") || trimmed.toLowerCase().contains("conservative");
        
        Log.d(TAG, "parseLLMResponse() - keyword check: 激进=" + trimmed.contains("激进") + 
              ", aggressive=" + trimmed.toLowerCase().contains("aggressive") +
              ", 保守=" + trimmed.contains("保守") +
              ", defensive=" + trimmed.toLowerCase().contains("defensive") +
              ", conservative=" + trimmed.toLowerCase().contains("conservative"));
        
        if (hasAggressive) {
            Log.d(TAG, "parseLLMResponse() - Detected AGGRESSIVE style");
            profile.setStyleLabel(HumanStyleProfile.STYLE_AGGRESSIVE);
            profile.setAggressivenessScore(0.8);
            profile.setConservativenessScore(0.2);
        } else if (hasConservative) {
            Log.d(TAG, "parseLLMResponse() - Detected CONSERVATIVE style");
            profile.setStyleLabel(HumanStyleProfile.STYLE_CONSERVATIVE);
            profile.setAggressivenessScore(0.2);
            profile.setConservativenessScore(0.8);
        } else {
            Log.d(TAG, "parseLLMResponse() - Detected BALANCED style (no keywords found)");
            profile.setStyleLabel(HumanStyleProfile.STYLE_BALANCED);
            profile.setAggressivenessScore(0.5);
            profile.setConservativenessScore(0.5);
        }

        profile.incrementGamesAnalyzed();
        Log.d(TAG, "parseLLMResponse() - Final profile: style=" + profile.getStyleLabel() + 
              ", aggressiveness=" + profile.getAggressivenessScore() +
              ", conservativeness=" + profile.getConservativenessScore() +
              ", gamesAnalyzed=" + profile.getGamesAnalyzed());
        
        return profile;
    }

    /**
     * 取消当前正在执行的分析任务
     */
    public void cancel() {
        if (currentTask != null && !currentTask.isDone()) {
            Log.d(TAG, "cancel() - Cancelling running task");
            System.out.println("[HumanStyleAnalyzer] cancel() - Cancelling running task");
            currentTask.cancel(true);
        }
    }

    /**
     * 关闭当前实例，取消任务并清理引用
     * 注意：这不会关闭静态线程池，线程池会继续为其他实例服务
     */
    public void shutdown() {
        Log.d(TAG, "shutdown() called");
        System.out.println("[HumanStyleAnalyzer] shutdown() called");
        isShutdown.set(true);
        cancel();
        callbackRef = null;
        Log.d(TAG, "shutdown() complete, isShutdown=" + isShutdown.get());
        System.out.println("[HumanStyleAnalyzer] shutdown() complete, isShutdown=" + isShutdown.get());
    }

    /**
     * 完全关闭静态线程池
     * 应在应用退出时调用（如 Application.onTerminate()）
     * 调用后所有 HumanStyleAnalyzer 实例将无法再执行分析任务
     */
    public static void shutdownExecutor() {
        synchronized (LOCK) {
            Log.d(TAG, "shutdownExecutor() called");
            System.out.println("[HumanStyleAnalyzer] shutdownExecutor() called");
            
            if (executor == null) {
                Log.d(TAG, "Executor is already null");
                System.out.println("[HumanStyleAnalyzer] Executor is already null");
                return;
            }
            
            if (executor.isShutdown()) {
                Log.d(TAG, "Executor is already shutdown");
                System.out.println("[HumanStyleAnalyzer] Executor is already shutdown");
                return;
            }
            
            Log.d(TAG, "Calling shutdownNow() on executor...");
            System.out.println("[HumanStyleAnalyzer] Calling shutdownNow() on executor...");
            executor.shutdownNow();
            
            try {
                Log.d(TAG, "Waiting for termination (max 5 seconds)...");
                System.out.println("[HumanStyleAnalyzer] Waiting for termination (max 5 seconds)...");
                boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);
                if (!terminated) {
                    Log.w(TAG, "Thread pool did not terminate in time");
                    System.err.println("[HumanStyleAnalyzer] Thread pool did not terminate in time");
                } else {
                    Log.d(TAG, "Thread pool terminated successfully");
                    System.out.println("[HumanStyleAnalyzer] Thread pool terminated successfully");
                }
            } catch (InterruptedException e) {
                Log.w(TAG, "awaitTermination interrupted");
                System.out.println("[HumanStyleAnalyzer] awaitTermination interrupted");
                Thread.currentThread().interrupt();
            }
            
            executor = null;
            Log.d(TAG, "shutdownExecutor() complete, executor set to null");
            System.out.println("[HumanStyleAnalyzer] shutdownExecutor() complete, executor set to null");
        }
    }

    /**
     * 检查线程池是否已关闭
     */
    public static boolean isExecutorShutdown() {
        synchronized (LOCK) {
            boolean result = executor == null || executor.isShutdown();
            Log.d(TAG, "isExecutorShutdown() = " + result);
            System.out.println("[HumanStyleAnalyzer] isExecutorShutdown() = " + result);
            return result;
        }
    }
}

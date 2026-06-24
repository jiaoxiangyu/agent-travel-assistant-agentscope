package com.example.travelassistant.persistence.service;

/** 记录每次 Agent 运行日志，便于本地排查请求开始、成功或失败状态。 */
public interface AgentRunLogService {

    /** 记录一次 Agent 调用开始。 */
    void markRunning(String runId, String conversationId, String userId, String message);

    /** 记录 Agent 调用执行完成。 */
    void markCompleted(String runId, String conversationId, String userId, long durationMs);

    /** 记录执行失败和异常消息。 */
    void markFailed(
            String runId, String conversationId, String userId, long durationMs, Exception exception);

    /** 记录一轮 Agent 推理开始。 */
    void markReasoningStarted(
            String runId,
            String conversationId,
            String userId,
            int reasoningStep,
            int messageCount,
            int toolCount,
            String latestMessage,
            String toolNames);

    /** 记录一轮 Agent 推理完成。 */
    void markReasoningCompleted(
            String runId, String conversationId, String userId, int reasoningStep, long durationMs);

    /** 记录一轮 Agent 推理失败。 */
    void markReasoningFailed(
            String runId,
            String conversationId,
            String userId,
            int reasoningStep,
            long durationMs,
            Exception exception);

    /** 记录一轮推理使用的 system prompt 摘要。 */
    void markSystemPromptPrepared(
            String runId,
            String conversationId,
            String userId,
            int reasoningStep,
            int promptLength,
            long durationMs,
            String promptSummary);

    /** 记录底层模型调用开始。 */
    void markModelCallStarted(
            String runId,
            String conversationId,
            String userId,
            int reasoningStep,
            String modelName,
            int messageCount,
            int toolCount,
            String requestSummary);

    /** 记录底层模型调用完成。 */
    void markModelCallCompleted(
            String runId,
            String conversationId,
            String userId,
            int reasoningStep,
            String modelName,
            long durationMs,
            String usageSummary,
            String outputSummary,
            String visibleThinkingSummary,
            String toolCallSummary);

    /** 记录底层模型调用失败。 */
    void markModelCallFailed(
            String runId,
            String conversationId,
            String userId,
            int reasoningStep,
            String modelName,
            long durationMs,
            Exception exception);

    /** 记录工具调用开始。 */
    void markToolCalling(
            String runId,
            String conversationId,
            String userId,
            String toolCallId,
            String toolName,
            String input);

    /** 记录工具调用完成。 */
    void markToolCompleted(
            String runId,
            String conversationId,
            String userId,
            String toolCallId,
            String toolName,
            long durationMs,
            String outputSummary);

    /** 记录工具调用失败。 */
    void markToolFailed(
            String runId,
            String conversationId,
            String userId,
            String toolCallId,
            String toolName,
            long durationMs,
            Exception exception);

    /** 记录 Agent 最终对用户可见的回答。 */
    void markFinalResponse(
            String runId, String conversationId, String userId, long durationMs, String responseSummary);
}

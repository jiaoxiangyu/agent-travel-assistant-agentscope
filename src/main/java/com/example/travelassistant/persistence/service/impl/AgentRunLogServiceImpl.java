package com.example.travelassistant.persistence.service.impl;

import com.example.travelassistant.config.TravelAgentProperties;
import com.example.travelassistant.persistence.service.AgentRunLogService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 将每次 Agent 运行状态写入应用日志和本地文件。 */
@Service
public class AgentRunLogServiceImpl implements AgentRunLogService {

    private static final Logger log = LoggerFactory.getLogger(AgentRunLogServiceImpl.class);

    private final TravelAgentProperties properties;

    public AgentRunLogServiceImpl(TravelAgentProperties properties) {
        this.properties = properties;
    }

    /** 记录一次 Agent 调用开始。 */
    @Override
    public void markRunning(String runId, String conversationId, String userId, String message) {
        Instant now = Instant.now();
        log.info(
                "Agent run started: runId={}, conversationId={}, userId={}, message={}",
                runId,
                conversationId,
                userId,
                message);
        appendRunLog(
                userId,
                conversationId,
                """
                [%s] RUNNING
                runId=%s
                conversationId=%s
                userId=%s
                message=%s

                """
                        .formatted(now, runId, conversationId, userId, message));
    }

    /** 记录 Agent 调用执行完成。 */
    @Override
    public void markCompleted(String runId, String conversationId, String userId, long durationMs) {
        Instant now = Instant.now();
        log.info(
                "Agent run completed: runId={}, conversationId={}, userId={}, durationMs={}",
                runId,
                conversationId,
                userId,
                durationMs);
        appendRunLog(
                userId,
                conversationId,
                """
                [%s] COMPLETED
                runId=%s
                conversationId=%s
                userId=%s
                durationMs=%d

                """
                        .formatted(now, runId, conversationId, userId, durationMs));
    }

    /** 记录执行失败和异常消息。 */
    @Override
    public void markFailed(
            String runId,
            String conversationId,
            String userId,
            long durationMs,
            Exception exception) {
        Instant now = Instant.now();
        String error = exception == null ? "unknown" : exception.getMessage();
        log.info(
                "Agent run failed: runId={}, conversationId={}, userId={}, durationMs={}, error={}",
                runId,
                conversationId,
                userId,
                durationMs,
                error,
                exception);
        appendRunLog(
                userId,
                conversationId,
                """
                [%s] FAILED
                runId=%s
                conversationId=%s
                userId=%s
                durationMs=%d
                error=%s

                """
                        .formatted(now, runId, conversationId, userId, durationMs, error));
    }

    /** 记录一轮 Agent 推理开始。 */
    @Override
    public void markReasoningStarted(
            String runId,
            String conversationId,
            String userId,
            int reasoningStep,
            int messageCount,
            int toolCount,
            String latestMessage,
            String toolNames) {
        Instant now = Instant.now();
        log.info(
                "Agent reasoning started: runId={}, conversationId={}, userId={}, step={}, messageCount={}, toolCount={}, tools={}",
                runId,
                conversationId,
                userId,
                reasoningStep,
                messageCount,
                toolCount,
                toolNames);
        appendRunLog(
                userId,
                conversationId,
                """
                [%s] REASONING_STARTED
                runId=%s
                conversationId=%s
                userId=%s
                reasoningStep=%d
                messageCount=%d
                toolCount=%d
                latestMessage=%s
                tools=%s

                """
                        .formatted(
                                now,
                                runId,
                                conversationId,
                                userId,
                                reasoningStep,
                                messageCount,
                                toolCount,
                                latestMessage,
                                toolNames));
    }

    /** 记录一轮 Agent 推理完成。 */
    @Override
    public void markReasoningCompleted(
            String runId,
            String conversationId,
            String userId,
            int reasoningStep,
            long durationMs) {
        Instant now = Instant.now();
        log.info(
                "Agent reasoning completed: runId={}, conversationId={}, userId={}, step={}, durationMs={}",
                runId,
                conversationId,
                userId,
                reasoningStep,
                durationMs);
        appendRunLog(
                userId,
                conversationId,
                """
                [%s] REASONING_COMPLETED
                runId=%s
                conversationId=%s
                userId=%s
                reasoningStep=%d
                durationMs=%d

                """
                        .formatted(now, runId, conversationId, userId, reasoningStep, durationMs));
    }

    /** 记录一轮 Agent 推理失败。 */
    @Override
    public void markReasoningFailed(
            String runId,
            String conversationId,
            String userId,
            int reasoningStep,
            long durationMs,
            Exception exception) {
        Instant now = Instant.now();
        String error = exception == null ? "unknown" : exception.getMessage();
        log.info(
                "Agent reasoning failed: runId={}, conversationId={}, userId={}, step={}, durationMs={}, error={}",
                runId,
                conversationId,
                userId,
                reasoningStep,
                durationMs,
                error,
                exception);
        appendRunLog(
                userId,
                conversationId,
                """
                [%s] REASONING_FAILED
                runId=%s
                conversationId=%s
                userId=%s
                reasoningStep=%d
                durationMs=%d
                error=%s

                """
                        .formatted(
                                now, runId, conversationId, userId, reasoningStep, durationMs, error));
    }

    /** 记录一轮推理使用的 system prompt 摘要。 */
    @Override
    public void markSystemPromptPrepared(
            String runId,
            String conversationId,
            String userId,
            int reasoningStep,
            int promptLength,
            long durationMs,
            String promptSummary) {
        Instant now = Instant.now();
        log.info(
                "Agent system prompt prepared: runId={}, conversationId={}, userId={}, step={}, promptLength={}, durationMs={}",
                runId,
                conversationId,
                userId,
                reasoningStep,
                promptLength,
                durationMs);
        appendRunLog(
                userId,
                conversationId,
                """
                [%s] SYSTEM_PROMPT_PREPARED
                runId=%s
                conversationId=%s
                userId=%s
                reasoningStep=%d
                promptLength=%d
                durationMs=%d
                promptSummary=%s

                """
                        .formatted(
                                now,
                                runId,
                                conversationId,
                                userId,
                                reasoningStep,
                                promptLength,
                                durationMs,
                                promptSummary));
    }

    /** 记录底层模型调用开始。 */
    @Override
    public void markModelCallStarted(
            String runId,
            String conversationId,
            String userId,
            int reasoningStep,
            String modelName,
            int messageCount,
            int toolCount,
            String requestSummary) {
        Instant now = Instant.now();
        log.info(
                "Agent model call started: runId={}, conversationId={}, userId={}, step={}, model={}, messageCount={}, toolCount={}",
                runId,
                conversationId,
                userId,
                reasoningStep,
                modelName,
                messageCount,
                toolCount);
        appendRunLog(
                userId,
                conversationId,
                """
                [%s] MODEL_CALL_STARTED
                runId=%s
                conversationId=%s
                userId=%s
                reasoningStep=%d
                model=%s
                messageCount=%d
                toolCount=%d
                requestSummary=%s

                """
                        .formatted(
                                now,
                                runId,
                                conversationId,
                                userId,
                                reasoningStep,
                                modelName,
                                messageCount,
                                toolCount,
                                requestSummary));
    }

    /** 记录底层模型调用完成。 */
    @Override
    public void markModelCallCompleted(
            String runId,
            String conversationId,
            String userId,
            int reasoningStep,
            String modelName,
            long durationMs,
            String usageSummary,
            String outputSummary,
            String visibleThinkingSummary,
            String toolCallSummary) {
        Instant now = Instant.now();
        log.info(
                "Agent model call completed: runId={}, conversationId={}, userId={}, step={}, model={}, durationMs={}",
                runId,
                conversationId,
                userId,
                reasoningStep,
                modelName,
                durationMs);
        appendRunLog(
                userId,
                conversationId,
                """
                [%s] MODEL_CALL_COMPLETED
                runId=%s
                conversationId=%s
                userId=%s
                reasoningStep=%d
                model=%s
                durationMs=%d
                usage=%s
                outputSummary=%s
                visibleThinkingSummary=%s
                toolCallSummary=%s

                """
                        .formatted(
                                now,
                                runId,
                                conversationId,
                                userId,
                                reasoningStep,
                                modelName,
                                durationMs,
                                usageSummary,
                                outputSummary,
                                visibleThinkingSummary,
                                toolCallSummary));
    }

    /** 记录底层模型调用失败。 */
    @Override
    public void markModelCallFailed(
            String runId,
            String conversationId,
            String userId,
            int reasoningStep,
            String modelName,
            long durationMs,
            Exception exception) {
        Instant now = Instant.now();
        String error = exception == null ? "unknown" : exception.getMessage();
        log.info(
                "Agent model call failed: runId={}, conversationId={}, userId={}, step={}, model={}, durationMs={}, error={}",
                runId,
                conversationId,
                userId,
                reasoningStep,
                modelName,
                durationMs,
                error,
                exception);
        appendRunLog(
                userId,
                conversationId,
                """
                [%s] MODEL_CALL_FAILED
                runId=%s
                conversationId=%s
                userId=%s
                reasoningStep=%d
                model=%s
                durationMs=%d
                error=%s

                """
                        .formatted(
                                now,
                                runId,
                                conversationId,
                                userId,
                                reasoningStep,
                                modelName,
                                durationMs,
                                error));
    }

    /** 记录工具调用开始。 */
    @Override
    public void markToolCalling(
            String runId,
            String conversationId,
            String userId,
            String toolCallId,
            String toolName,
            String input) {
        Instant now = Instant.now();
        log.info(
                "Agent tool started: runId={}, conversationId={}, userId={}, toolCallId={}, toolName={}, input={}",
                runId,
                conversationId,
                userId,
                toolCallId,
                toolName,
                input);
        appendRunLog(
                userId,
                conversationId,
                """
                [%s] TOOL_CALLING
                runId=%s
                conversationId=%s
                userId=%s
                toolCallId=%s
                toolName=%s
                input=%s

                """
                        .formatted(now, runId, conversationId, userId, toolCallId, toolName, input));
    }

    /** 记录工具调用完成。 */
    @Override
    public void markToolCompleted(
            String runId,
            String conversationId,
            String userId,
            String toolCallId,
            String toolName,
            long durationMs,
            String outputSummary) {
        Instant now = Instant.now();
        log.info(
                "Agent tool completed: runId={}, conversationId={}, userId={}, toolCallId={}, toolName={}, durationMs={}",
                runId,
                conversationId,
                userId,
                toolCallId,
                toolName,
                durationMs);
        appendRunLog(
                userId,
                conversationId,
                """
                [%s] TOOL_COMPLETED
                runId=%s
                conversationId=%s
                userId=%s
                toolCallId=%s
                toolName=%s
                durationMs=%d
                outputSummary=%s

                """
                        .formatted(
                                now,
                                runId,
                                conversationId,
                                userId,
                                toolCallId,
                                toolName,
                                durationMs,
                                outputSummary));
    }

    /** 记录工具调用失败。 */
    @Override
    public void markToolFailed(
            String runId,
            String conversationId,
            String userId,
            String toolCallId,
            String toolName,
            long durationMs,
            Exception exception) {
        Instant now = Instant.now();
        String error = exception == null ? "unknown" : exception.getMessage();
        log.info(
                "Agent tool failed: runId={}, conversationId={}, userId={}, toolCallId={}, toolName={}, durationMs={}, error={}",
                runId,
                conversationId,
                userId,
                toolCallId,
                toolName,
                durationMs,
                error,
                exception);
        appendRunLog(
                userId,
                conversationId,
                """
                [%s] TOOL_FAILED
                runId=%s
                conversationId=%s
                userId=%s
                toolCallId=%s
                toolName=%s
                durationMs=%d
                error=%s

                """
                        .formatted(
                                now,
                                runId,
                                conversationId,
                                userId,
                                toolCallId,
                                toolName,
                                durationMs,
                                error));
    }

    /** 记录 Agent 最终对用户可见的回答。 */
    @Override
    public void markFinalResponse(
            String runId,
            String conversationId,
            String userId,
            long durationMs,
            String responseSummary) {
        Instant now = Instant.now();
        log.info(
                "Agent final response observed: runId={}, conversationId={}, userId={}, durationMs={}",
                runId,
                conversationId,
                userId,
                durationMs);
        appendRunLog(
                userId,
                conversationId,
                """
                [%s] FINAL_RESPONSE
                runId=%s
                conversationId=%s
                userId=%s
                durationMs=%d
                responseSummary=%s

                """
                        .formatted(now, runId, conversationId, userId, durationMs, responseSummary));
    }

    private void appendRunLog(String userId, String conversationId, String content) {
        Path file =
                properties
                        .getRunLogDir()
                        .resolve(sanitizePathSegment(userId))
                        .resolve(sanitizePathSegment(conversationId) + ".log");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(
                    file,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn(
                    "Failed to write local agent run log: conversationId={}, userId={}, file={}",
                    conversationId,
                    userId,
                    file,
                    e);
        }
    }

    private String sanitizePathSegment(String value) {
        return value == null || value.isBlank()
                ? "unknown"
                : value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}

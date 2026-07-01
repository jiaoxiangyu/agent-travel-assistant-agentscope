package com.example.travelassistant.persistence.service.impl;

import com.example.travelassistant.config.TravelAgentProperties;
import com.example.travelassistant.persistence.service.AgentRunLogService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 将每次 Agent 运行状态写入应用日志和本地文件。 */
@Service
public class AgentRunLogServiceImpl implements AgentRunLogService {

    private static final Logger log = LoggerFactory.getLogger(AgentRunLogServiceImpl.class);

    /** 本地运行日志使用 24 小时制，避免默认 Instant 的 UTC `T/Z` 格式不便阅读。 */
    private static final DateTimeFormatter RUN_LOG_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final TravelAgentProperties properties;

    public AgentRunLogServiceImpl(TravelAgentProperties properties) {
        this.properties = properties;
    }

    /** 记录一次 Agent 调用开始。 */
    @Override
    public void markRunning(
            String runId,
            String conversationId,
            String userId,
            String agentName,
            String agentId,
            String message) {
        Instant now = Instant.now();
        log.info(
                "Agent run started: runId={}, conversationId={}, userId={}, agentName={}, agentId={}, message={}",
                runId,
                conversationId,
                userId,
                agentName,
                agentId,
                message);
        appendRunLog(
                userId,
                conversationId,
                """
                [%s] RUNNING
                runId=%s
                conversationId=%s
                userId=%s
                agentName=%s
                agentId=%s
                message=%s

                """
                        .formatted(
                                formatTimestamp(now),
                                runId,
                                conversationId,
                                userId,
                                agentName,
                                agentId,
                                message));
    }

    /** 记录 Agent 调用执行完成。 */
    @Override
    public void markCompleted(
            String runId,
            String conversationId,
            String userId,
            String agentName,
            String agentId,
            long durationMs) {
        Instant now = Instant.now();
        log.info(
                "Agent run completed: runId={}, conversationId={}, userId={}, agentName={}, agentId={}, durationMs={}",
                runId,
                conversationId,
                userId,
                agentName,
                agentId,
                durationMs);
        appendRunLog(
                userId,
                conversationId,
                """
                [%s] COMPLETED
                runId=%s
                conversationId=%s
                userId=%s
                agentName=%s
                agentId=%s
                durationMs=%d

                """
                        .formatted(
                                formatTimestamp(now),
                                runId,
                                conversationId,
                                userId,
                                agentName,
                                agentId,
                                durationMs));
    }

    /** 记录执行失败和异常消息。 */
    @Override
    public void markFailed(
            String runId,
            String conversationId,
            String userId,
            String agentName,
            String agentId,
            long durationMs,
            Exception exception) {
        Instant now = Instant.now();
        String error = exception == null ? "unknown" : exception.getMessage();
        log.info(
                "Agent run failed: runId={}, conversationId={}, userId={}, agentName={}, agentId={}, durationMs={}, error={}",
                runId,
                conversationId,
                userId,
                agentName,
                agentId,
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
                agentName=%s
                agentId=%s
                durationMs=%d
                error=%s

                """
                        .formatted(
                                formatTimestamp(now),
                                runId,
                                conversationId,
                                userId,
                                agentName,
                                agentId,
                                durationMs,
                                error));
    }

    /** 记录一轮 Agent 推理开始。 */
    @Override
    public void markReasoningStarted(
            String runId,
            String conversationId,
            String userId,
            String agentName,
            String agentId,
            int reasoningStep,
            int messageCount,
            int toolCount,
            String latestMessage,
            String toolNames) {
        Instant now = Instant.now();
        log.info(
                "Agent reasoning started: runId={}, conversationId={}, userId={}, agentName={}, agentId={}, step={}, messageCount={}, toolCount={}, tools={}",
                runId,
                conversationId,
                userId,
                agentName,
                agentId,
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
                agentName=%s
                agentId=%s
                reasoningStep=%d
                messageCount=%d
                toolCount=%d
                latestMessage=%s
                tools=%s

                """
                        .formatted(
                                formatTimestamp(now),
                                runId,
                                conversationId,
                                userId,
                                agentName,
                                agentId,
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
            String agentName,
            String agentId,
            int reasoningStep,
            long durationMs) {
        Instant now = Instant.now();
        log.info(
                "Agent reasoning completed: runId={}, conversationId={}, userId={}, agentName={}, agentId={}, step={}, durationMs={}",
                runId,
                conversationId,
                userId,
                agentName,
                agentId,
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
                agentName=%s
                agentId=%s
                reasoningStep=%d
                durationMs=%d

                """
                        .formatted(
                                formatTimestamp(now),
                                runId,
                                conversationId,
                                userId,
                                agentName,
                                agentId,
                                reasoningStep,
                                durationMs));
    }

    /** 记录一轮 Agent 推理失败。 */
    @Override
    public void markReasoningFailed(
            String runId,
            String conversationId,
            String userId,
            String agentName,
            String agentId,
            int reasoningStep,
            long durationMs,
            Exception exception) {
        Instant now = Instant.now();
        String error = exception == null ? "unknown" : exception.getMessage();
        log.info(
                "Agent reasoning failed: runId={}, conversationId={}, userId={}, agentName={}, agentId={}, step={}, durationMs={}, error={}",
                runId,
                conversationId,
                userId,
                agentName,
                agentId,
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
                agentName=%s
                agentId=%s
                reasoningStep=%d
                durationMs=%d
                error=%s

                """
                        .formatted(
                                formatTimestamp(now),
                                runId,
                                conversationId,
                                userId,
                                agentName,
                                agentId,
                                reasoningStep,
                                durationMs,
                                error));
    }

    /** 记录一轮推理使用的 system prompt 摘要。 */
    @Override
    public void markSystemPromptPrepared(
            String runId,
            String conversationId,
            String userId,
            String agentName,
            String agentId,
            int reasoningStep,
            int promptLength,
            long durationMs,
            String promptSummary) {
        Instant now = Instant.now();
        log.info(
                "Agent system prompt prepared: runId={}, conversationId={}, userId={}, agentName={}, agentId={}, step={}, promptLength={}, durationMs={}",
                runId,
                conversationId,
                userId,
                agentName,
                agentId,
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
                agentName=%s
                agentId=%s
                reasoningStep=%d
                promptLength=%d
                durationMs=%d
                promptSummary=%s

                """
                        .formatted(
                                formatTimestamp(now),
                                runId,
                                conversationId,
                                userId,
                                agentName,
                                agentId,
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
            String agentName,
            String agentId,
            int reasoningStep,
            String modelName,
            int messageCount,
            int toolCount,
            String requestSummary) {
        Instant now = Instant.now();
        log.info(
                "Agent model call started: runId={}, conversationId={}, userId={}, agentName={}, agentId={}, step={}, model={}, messageCount={}, toolCount={}",
                runId,
                conversationId,
                userId,
                agentName,
                agentId,
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
                agentName=%s
                agentId=%s
                reasoningStep=%d
                model=%s
                messageCount=%d
                toolCount=%d
                requestSummary=%s

                """
                        .formatted(
                                formatTimestamp(now),
                                runId,
                                conversationId,
                                userId,
                                agentName,
                                agentId,
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
            String agentName,
            String agentId,
            int reasoningStep,
            String modelName,
            long durationMs,
            String usageSummary,
            String outputSummary,
            String visibleThinkingSummary,
            String toolCallSummary) {
        Instant now = Instant.now();
        log.info(
                "Agent model call completed: runId={}, conversationId={}, userId={}, agentName={}, agentId={}, step={}, model={}, durationMs={}",
                runId,
                conversationId,
                userId,
                agentName,
                agentId,
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
                agentName=%s
                agentId=%s
                reasoningStep=%d
                model=%s
                durationMs=%d
                usage=%s
                outputSummary=%s
                visibleThinkingSummary=%s
                toolCallSummary=%s

                """
                        .formatted(
                                formatTimestamp(now),
                                runId,
                                conversationId,
                                userId,
                                agentName,
                                agentId,
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
            String agentName,
            String agentId,
            int reasoningStep,
            String modelName,
            long durationMs,
            Exception exception) {
        Instant now = Instant.now();
        String error = exception == null ? "unknown" : exception.getMessage();
        log.info(
                "Agent model call failed: runId={}, conversationId={}, userId={}, agentName={}, agentId={}, step={}, model={}, durationMs={}, error={}",
                runId,
                conversationId,
                userId,
                agentName,
                agentId,
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
                agentName=%s
                agentId=%s
                reasoningStep=%d
                model=%s
                durationMs=%d
                error=%s

                """
                        .formatted(
                                formatTimestamp(now),
                                runId,
                                conversationId,
                                userId,
                                agentName,
                                agentId,
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
            String agentName,
            String agentId,
            String toolCallId,
            String toolName,
            String input) {
        Instant now = Instant.now();
        log.info(
                "Agent tool started: runId={}, conversationId={}, userId={}, agentName={}, agentId={}, toolCallId={}, toolName={}, input={}",
                runId,
                conversationId,
                userId,
                agentName,
                agentId,
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
                agentName=%s
                agentId=%s
                toolCallId=%s
                toolName=%s
                input=%s

                """
                        .formatted(
                                formatTimestamp(now),
                                runId,
                                conversationId,
                                userId,
                                agentName,
                                agentId,
                                toolCallId,
                                toolName,
                                input));
    }

    /** 记录工具调用完成。 */
    @Override
    public void markToolCompleted(
            String runId,
            String conversationId,
            String userId,
            String agentName,
            String agentId,
            String toolCallId,
            String toolName,
            long durationMs,
            String outputSummary) {
        Instant now = Instant.now();
        log.info(
                "Agent tool completed: runId={}, conversationId={}, userId={}, agentName={}, agentId={}, toolCallId={}, toolName={}, durationMs={}",
                runId,
                conversationId,
                userId,
                agentName,
                agentId,
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
                agentName=%s
                agentId=%s
                toolCallId=%s
                toolName=%s
                durationMs=%d
                outputSummary=%s

                """
                        .formatted(
                                formatTimestamp(now),
                                runId,
                                conversationId,
                                userId,
                                agentName,
                                agentId,
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
            String agentName,
            String agentId,
            String toolCallId,
            String toolName,
            long durationMs,
            Exception exception) {
        Instant now = Instant.now();
        String error = exception == null ? "unknown" : exception.getMessage();
        log.info(
                "Agent tool failed: runId={}, conversationId={}, userId={}, agentName={}, agentId={}, toolCallId={}, toolName={}, durationMs={}, error={}",
                runId,
                conversationId,
                userId,
                agentName,
                agentId,
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
                agentName=%s
                agentId=%s
                toolCallId=%s
                toolName=%s
                durationMs=%d
                error=%s

                """
                        .formatted(
                                formatTimestamp(now),
                                runId,
                                conversationId,
                                userId,
                                agentName,
                                agentId,
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
            String agentName,
            String agentId,
            long durationMs,
            String responseSummary) {
        Instant now = Instant.now();
        log.info(
                "Agent final response observed: runId={}, conversationId={}, userId={}, agentName={}, agentId={}, durationMs={}",
                runId,
                conversationId,
                userId,
                agentName,
                agentId,
                durationMs);
        appendRunLog(
                userId,
                conversationId,
                """
                [%s] FINAL_RESPONSE
                runId=%s
                conversationId=%s
                userId=%s
                agentName=%s
                agentId=%s
                durationMs=%d
                responseSummary=%s

                """
                        .formatted(
                                formatTimestamp(now),
                                runId,
                                conversationId,
                                userId,
                                agentName,
                                agentId,
                                durationMs,
                                responseSummary));
    }

    /** 记录父 Agent streamEvents 中转发出来的子 Agent 事件。 */
    @Override
    public void markSubagentEvent(
            String runId,
            String conversationId,
            String userId,
            String agentName,
            String agentId,
            String source,
            String eventType,
            String eventId,
            String eventSummary) {
        Instant now = Instant.now();
        log.info(
                "Subagent event observed: runId={}, conversationId={}, userId={}, agentName={}, agentId={}, source={}, eventType={}, eventId={}",
                runId,
                conversationId,
                userId,
                agentName,
                agentId,
                source,
                eventType,
                eventId);
        appendRunLog(
                userId,
                conversationId,
                """
                [%s] SUBAGENT_EVENT
                runId=%s
                conversationId=%s
                userId=%s
                agentName=%s
                agentId=%s
                source=%s
                eventType=%s
                eventId=%s
                eventSummary=%s

                """
                        .formatted(
                                formatTimestamp(now),
                                runId,
                                conversationId,
                                userId,
                                agentName,
                                agentId,
                                source,
                                eventType,
                                eventId,
                                eventSummary));
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

    private String formatTimestamp(Instant instant) {
        return RUN_LOG_TIME_FORMATTER.format(instant);
    }

    private String sanitizePathSegment(String value) {
        return value == null || value.isBlank()
                ? "unknown"
                : value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}

package com.example.travelassistant.middleware;

import com.example.travelassistant.persistence.service.AgentRunLogService;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultDataDeltaEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.middleware.ReasoningInput;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** 使用 AgentScope middleware 记录每次 Agent 调用的运行日志。 */
@Component
public class AgentRunLogMiddleware implements MiddlewareBase {

    private final AgentRunLogService agentRunLogService;
    private final Map<String, ActiveRun> activeRuns = new ConcurrentHashMap<>();

    public AgentRunLogMiddleware(AgentRunLogService agentRunLogService) {
        this.agentRunLogService = agentRunLogService;
    }

    /** 包裹一次完整 Agent 调用，记录开始、完成、失败或取消状态。 */
    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext context,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        String runId = UUID.randomUUID().toString();
        String conversationId = context.getSessionId();
        String userId = context.getUserId();
        String runKey = runKey(conversationId, userId);
        ActiveRun activeRun = new ActiveRun(runId, System.nanoTime());
        activeRuns.put(runKey, activeRun);
        context.put("agentRunId", runId);

        agentRunLogService.markRunning(runId, conversationId, userId, latestMessage(input.msgs()));

        return next.apply(input)
                .doOnNext(event -> observeAgentEvent(activeRun, conversationId, userId, event))
                .doOnComplete(
                        () -> {
                            agentRunLogService.markCompleted(
                                    runId, conversationId, userId, durationMs(activeRun.startNanos()));
                        })
                .doOnError(
                        error -> {
                            agentRunLogService.markFailed(
                                    runId,
                                    conversationId,
                                    userId,
                                    durationMs(activeRun.startNanos()),
                                    asException(error));
                        })
                .doFinally(signalType -> activeRuns.remove(runKey, activeRun));
    }

    /** 包裹一轮 ReAct 推理，记录模型推理前后的上下文摘要。 */
    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent,
            RuntimeContext context,
            ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next) {
        String conversationId = context.getSessionId();
        String userId = context.getUserId();
        ActiveRun activeRun = activeRun(context);
        String runId = runId(context, activeRun);
        int reasoningStep = nextReasoningStep(context, activeRun);
        List<Msg> messages = input.messages();
        List<ToolSchema> tools = input.tools();
        long startNanos = System.nanoTime();

        agentRunLogService.markReasoningStarted(
                runId,
                conversationId,
                userId,
                reasoningStep,
                size(messages),
                size(tools),
                truncate(latestMessage(messages)),
                truncate(toolNames(tools)));

        return next.apply(input)
                .doOnComplete(
                        () -> {
                            agentRunLogService.markReasoningCompleted(
                                    runId,
                                    conversationId,
                                    userId,
                                    reasoningStep,
                                    durationMs(startNanos));
                        })
                .doOnError(
                        error -> {
                            agentRunLogService.markReasoningFailed(
                                    runId,
                                    conversationId,
                                    userId,
                                    reasoningStep,
                                    durationMs(startNanos),
                                    asException(error));
                        });
    }

    /** 包裹底层模型 API 调用，记录模型请求开始、完成、失败或取消状态。 */
    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent,
            RuntimeContext context,
            ModelCallInput input,
            Function<ModelCallInput, Flux<AgentEvent>> next) {
        String conversationId = context.getSessionId();
        String userId = context.getUserId();
        ActiveRun activeRun = activeRun(context);
        String runId = runId(context, activeRun);
        int reasoningStep = currentReasoningStep(context, activeRun);
        String modelName = modelName(input);
        long startNanos = System.nanoTime();
        ModelCallTrace trace = new ModelCallTrace();

        agentRunLogService.markModelCallStarted(
                runId,
                conversationId,
                userId,
                reasoningStep,
                modelName,
                size(input.messages()),
                size(input.tools()),
                truncate(summarizeMessages(input.messages())));

        return next.apply(input)
                .doOnNext(trace::observe)
                .doOnComplete(
                        () -> {
                            agentRunLogService.markModelCallCompleted(
                                    runId,
                                    conversationId,
                                    userId,
                                    reasoningStep,
                                    modelName,
                                    durationMs(startNanos),
                                    trace.usageSummary(),
                                    trace.outputSummary(),
                                    trace.visibleThinkingSummary(),
                                    trace.toolCallSummary());
                        })
                .doOnError(
                        error -> {
                            agentRunLogService.markModelCallFailed(
                                    runId,
                                    conversationId,
                                    userId,
                                    reasoningStep,
                                    modelName,
                                    durationMs(startNanos),
                                    asException(error));
                        });
    }

    /** 记录每轮推理最终使用的 system prompt 摘要，不修改原 prompt。 */
    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext context, String currentPrompt) {
        String conversationId = context.getSessionId();
        String userId = context.getUserId();
        ActiveRun activeRun = activeRun(context);
        String runId = runId(context, activeRun);
        long startNanos = System.nanoTime();
        String prompt = currentPrompt == null ? "" : currentPrompt;

        agentRunLogService.markSystemPromptPrepared(
                runId,
                conversationId,
                userId,
                currentReasoningStep(context, activeRun),
                prompt.length(),
                durationMs(startNanos),
                truncate(prompt));

        return Mono.just(prompt);
    }

    /** 包裹工具执行，记录每次工具调用的开始、完成、失败或取消状态。 */
    @Override
    public Flux<AgentEvent> onActing(
            Agent agent,
            RuntimeContext context,
            ActingInput input,
            Function<ActingInput, Flux<AgentEvent>> next) {
        String conversationId = context.getSessionId();
        String userId = context.getUserId();
        ActiveRun activeRun = activeRun(context);
        String runId = runId(context, activeRun);
        List<ToolUseBlock> toolCalls = input.toolCalls();
        long startNanos = System.nanoTime();
        ToolResultTrace trace = new ToolResultTrace();

        for (ToolUseBlock toolCall : toolCalls) {
            agentRunLogService.markToolCalling(
                    runId,
                    conversationId,
                    userId,
                    toolCall.getId(),
                    toolCall.getName(),
                    truncate(String.valueOf(toolCall.getInput())));
        }

        return next.apply(input)
                .doOnNext(trace::observe)
                .doOnComplete(
                        () -> {
                            for (ToolUseBlock toolCall : toolCalls) {
                                agentRunLogService.markToolCompleted(
                                        runId,
                                        conversationId,
                                        userId,
                                        toolCall.getId(),
                                        toolCall.getName(),
                                        durationMs(startNanos),
                                        trace.outputSummary(toolCall.getId()));
                            }
                        })
                .doOnError(
                        error -> {
                            for (ToolUseBlock toolCall : toolCalls) {
                                agentRunLogService.markToolFailed(
                                        runId,
                                        conversationId,
                                        userId,
                                        toolCall.getId(),
                                        toolCall.getName(),
                                        durationMs(startNanos),
                                        asException(error));
                            }
                        });
    }

    private void observeAgentEvent(
            ActiveRun activeRun, String conversationId, String userId, AgentEvent event) {
        if (event instanceof AgentResultEvent resultEvent
                && activeRun.finalResponseLogged().compareAndSet(false, true)) {
            Msg result = resultEvent.getResult();
            agentRunLogService.markFinalResponse(
                    activeRun.runId(),
                    conversationId,
                    userId,
                    durationMs(activeRun.startNanos()),
                    truncate(result == null ? "" : result.getTextContent()));
        }
    }

    private ActiveRun activeRun(RuntimeContext context) {
        return activeRuns.get(runKey(context.getSessionId(), context.getUserId()));
    }

    private String runId(RuntimeContext context, ActiveRun activeRun) {
        if (activeRun != null) {
            return activeRun.runId();
        }
        return context.get("agentRunId", String.class);
    }

    private int nextReasoningStep(RuntimeContext context, ActiveRun activeRun) {
        if (activeRun != null) {
            int nextStep = activeRun.reasoningStep().incrementAndGet();
            context.put("agentReasoningStep", nextStep);
            return nextStep;
        }
        Integer currentStep = context.get("agentReasoningStep", Integer.class);
        int nextStep = currentStep == null ? 1 : currentStep + 1;
        context.put("agentReasoningStep", nextStep);
        return nextStep;
    }

    private int currentReasoningStep(RuntimeContext context, ActiveRun activeRun) {
        if (activeRun != null) {
            return activeRun.reasoningStep().get();
        }
        Integer currentStep = context.get("agentReasoningStep", Integer.class);
        return currentStep == null ? 0 : currentStep;
    }

    private String runKey(String conversationId, String userId) {
        return (userId == null ? "" : userId) + "::" + (conversationId == null ? "" : conversationId);
    }

    private String latestMessage(List<Msg> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        return messages.get(messages.size() - 1).getTextContent();
    }

    private String toolNames(List<ToolSchema> tools) {
        if (tools == null || tools.isEmpty()) {
            return "";
        }
        return String.join(", ", tools.stream().map(ToolSchema::getName).toList());
    }

    private String summarizeMessages(List<Msg> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        return String.join(
                "\n",
                messages.stream()
                        .map(
                                message ->
                                        "%s:%s"
                                                .formatted(
                                                        message.getRole(),
                                                        truncate(message.getTextContent(), 300)))
                        .toList());
    }

    private int size(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private long durationMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private String modelName(ModelCallInput input) {
        if (input == null || input.model() == null) {
            return "unknown";
        }
        return input.model().getClass().getSimpleName();
    }

    private String truncate(String value) {
        return truncate(value, 1000);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private String usageSummary(ChatUsage usage) {
        if (usage == null) {
            return "";
        }
        return "inputTokens=%d, outputTokens=%d, totalTokens=%d, time=%s"
                .formatted(
                        usage.getInputTokens(),
                        usage.getOutputTokens(),
                        usage.getTotalTokens(),
                        usage.getTime());
    }

    private Exception asException(Throwable throwable) {
        if (throwable instanceof Exception exception) {
            return exception;
        }
        return new IllegalStateException(throwable);
    }

    private record ActiveRun(
            String runId,
            long startNanos,
            AtomicInteger reasoningStep,
            AtomicBoolean finalResponseLogged) {

        private ActiveRun(String runId, long startNanos) {
            this(runId, startNanos, new AtomicInteger(0), new AtomicBoolean(false));
        }
    }

    private class ModelCallTrace {

        private final StringBuilder output = new StringBuilder();
        private final StringBuilder visibleThinking = new StringBuilder();
        private final Map<String, StringBuilder> toolCalls = new ConcurrentHashMap<>();
        private volatile String usage = "";

        private void observe(AgentEvent event) {
            if (event instanceof TextBlockDeltaEvent textDelta) {
                output.append(textDelta.getDelta());
            } else if (event instanceof ThinkingBlockDeltaEvent thinkingDelta) {
                visibleThinking.append(thinkingDelta.getDelta());
            } else if (event instanceof ToolCallStartEvent toolCallStart) {
                toolCalls.computeIfAbsent(toolCallStart.getToolCallId(), id -> new StringBuilder())
                        .append(toolCallStart.getToolCallName());
            } else if (event instanceof ToolCallDeltaEvent toolCallDelta) {
                toolCalls.computeIfAbsent(toolCallDelta.getToolCallId(), id -> new StringBuilder())
                        .append(toolCallDelta.getDelta());
            } else if (event instanceof ModelCallEndEvent modelCallEnd) {
                usage = AgentRunLogMiddleware.this.usageSummary(modelCallEnd.getUsage());
            }
        }

        private String usageSummary() {
            return usage;
        }

        private String outputSummary() {
            return truncate(output.toString());
        }

        private String visibleThinkingSummary() {
            return truncate(visibleThinking.toString());
        }

        private String toolCallSummary() {
            if (toolCalls.isEmpty()) {
                return "";
            }
            return truncate(
                    String.join(
                            "\n",
                            toolCalls.entrySet().stream()
                                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                                    .toList()));
        }
    }

    private class ToolResultTrace {

        private final Map<String, StringBuilder> outputs = new ConcurrentHashMap<>();

        private void observe(AgentEvent event) {
            if (event instanceof ToolResultTextDeltaEvent textDelta) {
                outputs.computeIfAbsent(textDelta.getToolCallId(), id -> new StringBuilder())
                        .append(textDelta.getDelta());
            } else if (event instanceof ToolResultDataDeltaEvent dataDelta) {
                outputs.computeIfAbsent(dataDelta.getToolCallId(), id -> new StringBuilder())
                        .append(dataDelta.getData());
            }
        }

        private String outputSummary(String toolCallId) {
            StringBuilder output = outputs.get(toolCallId);
            return output == null ? "" : truncate(output.toString());
        }
    }
}

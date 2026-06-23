package com.example.travelassistant.service.impl;

import com.example.travelassistant.config.TravelAgentProperties;
import com.example.travelassistant.service.TravelAgentService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.model.transport.OkHttpTransport;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TravelAgentServiceImpl implements TravelAgentService {

    private static final String DEFAULT_USER_ID = "default-user";

    private static final String SYSTEM_PROMPT =
            """
            你是一个中文旅行规划助手，目标是给用户产出可执行、预算友好、兼顾天气和体力的旅行方案。

            工作方式：
            1. 先判断用户是否至少提供了目的地和旅行天数；缺少这些关键信息时，先用一句话追问，不要编造。
            2. 信息足够时，优先调用工具查询城市画像、天气、景点候选，并生成结构化行程草稿。
            3. 最终回答请用中文 Markdown，包含天气提示、每日行程、交通建议、餐饮/预算建议和注意事项。
            4. 预算未说明住宿或大交通时，要明确说明估算边界；涉及门票、营业时间、预约要求时提醒以官方信息为准。
            5. 行程不要过满，每天保留机动时间，并根据天气给出室内/室外备选方案。
            """;

    @Resource
    private TravelAgentProperties properties;

    @Resource(name = "travelToolkit")
    private Toolkit toolkit;

    @Resource(name = "travelAgentStateStore")
    private AgentStateStore stateStore;

    @Override
    public String chat(String conversationId, String userId, String message) {
        RuntimeContext context =
                RuntimeContext.builder()
                        .sessionId(conversationId)
                        .userId(StringUtils.hasText(userId) ? userId : DEFAULT_USER_ID)
                        .build();

        try (ReActAgent agent = buildAgent(conversationId)) {
            Msg response =
                    agent.call(List.of(new UserMessage(message)), context)
                            .block(properties.getTimeout());
            if (response == null || !StringUtils.hasText(response.getTextContent())) {
                return "抱歉，我暂时没有生成有效的旅行方案，请补充目的地、天数、预算和同行人后再试。";
            }
            return response.getTextContent();
        }
    }

    private ReActAgent buildAgent(String conversationId) {
        return ReActAgent.builder()
                .name(properties.getName())
                .sysPrompt(SYSTEM_PROMPT)
                .model(buildModel())
                .toolkit(toolkit)
                .stateStore(stateStore)
                .defaultSessionId(conversationId)
                .maxIters(properties.getMaxIters())
                .build();
    }

    private Model buildModel() {
        String apiKey = System.getenv(properties.getApiKeyEnv());
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Missing environment variable: " + properties.getApiKeyEnv());
        }

        return OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(properties.getModel())
                .baseUrl(properties.getBaseUrl())
                .httpTransport(OkHttpTransport.builder().build())
                .build();
    }
}

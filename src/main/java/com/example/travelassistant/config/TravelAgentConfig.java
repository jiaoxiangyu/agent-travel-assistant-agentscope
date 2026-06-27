package com.example.travelassistant.config;

import com.example.travelassistant.persistence.RedisAgentStateStore;
import com.example.travelassistant.tools.AttractionTools;
import com.example.travelassistant.tools.CityProfileTools;
import com.example.travelassistant.tools.ItineraryTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 注册旅行 Agent 运行所需的 Spring Bean。 */
@Configuration
public class TravelAgentConfig {

    /** 提供工具结果、Redis 状态等 JSON 序列化能力。 */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /** 将多个旅行领域工具注册到 AgentScope Toolkit，供 HarnessAgent 调用。 */
    @Bean
    public Toolkit travelToolkit(
            CityProfileTools cityProfileTools,
            AttractionTools attractionTools,
            ItineraryTools itineraryTools) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(cityProfileTools);
        toolkit.registerTool(attractionTools);
        toolkit.registerTool(itineraryTools);
        return toolkit;
    }

    /** 使用 Redis 持久化 AgentScope 内部状态，支持多轮会话上下文恢复。 */
    @Bean
    public AgentStateStore travelAgentStateStore(
            StringRedisTemplate redisTemplate, TravelAgentProperties properties) {
        return new RedisAgentStateStore(redisTemplate, properties.getStateTtl());
    }
}

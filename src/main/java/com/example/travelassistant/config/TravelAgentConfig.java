package com.example.travelassistant.config;

import com.example.travelassistant.tools.AttractionTools;
import com.example.travelassistant.tools.CityProfileTools;
import com.example.travelassistant.tools.ItineraryTools;
import com.example.travelassistant.tools.WeatherTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TravelAgentConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public Toolkit travelToolkit(
            WeatherTools weatherTools,
            CityProfileTools cityProfileTools,
            AttractionTools attractionTools,
            ItineraryTools itineraryTools) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(weatherTools);
        toolkit.registerTool(cityProfileTools);
        toolkit.registerTool(attractionTools);
        toolkit.registerTool(itineraryTools);
        return toolkit;
    }

    @Bean
    public AgentStateStore travelAgentStateStore(TravelAgentProperties properties) {
        return new JsonFileAgentStateStore(properties.getStateDir());
    }
}

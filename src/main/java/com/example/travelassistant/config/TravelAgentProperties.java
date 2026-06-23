package com.example.travelassistant.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "travel.agent")
public class TravelAgentProperties {

    private String name = "TravelAssistant";

    private String model = "Qwen/Qwen3-235B-A22B-Instruct-2507";

    private String baseUrl = "https://api-inference.modelscope.cn/v1";

    private String apiKeyEnv = "MODELSCOPE_API_KEY";

    private int maxIters = 6;

    private Duration timeout = Duration.ofSeconds(90);

    private Path stateDir = Path.of(".agentscope/state/travel-assistant");

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKeyEnv() {
        return apiKeyEnv;
    }

    public void setApiKeyEnv(String apiKeyEnv) {
        this.apiKeyEnv = apiKeyEnv;
    }

    public int getMaxIters() {
        return maxIters;
    }

    public void setMaxIters(int maxIters) {
        this.maxIters = maxIters;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Path getStateDir() {
        return stateDir;
    }

    public void setStateDir(Path stateDir) {
        this.stateDir = stateDir;
    }
}

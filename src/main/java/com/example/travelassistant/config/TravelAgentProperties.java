package com.example.travelassistant.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 绑定 {@code travel.agent.*} 配置项，集中管理 Agent 模型、执行和产物参数。 */
@ConfigurationProperties(prefix = "travel.agent")
public class TravelAgentProperties {

    /** AgentScope Agent 名称，主要用于运行时标识和日志定位。 */
    private String name = "TravelAssistant";

    /** OpenAI 兼容接口中的模型名称。 */
    private String model = "Qwen/Qwen3-235B-A22B-Instruct-2507";

    /** OpenAI 兼容模型服务的基础地址。 */
    private String baseUrl = "https://api-inference.modelscope.cn/v1";

    /** 保存模型访问令牌的环境变量名，避免把密钥写入配置文件。 */
    private String apiKeyEnv = "MODELSCOPE_API_KEY";

    /** ReActAgent 单次回答最多推理/工具调用轮数。 */
    private int maxIters = 6;

    /** Redis 上下文缺失时，从 MySQL 恢复的最近消息数量。 */
    private int historyLimit = 20;

    /** 等待模型生成完整响应的最长时间。 */
    private Duration timeout = Duration.ofSeconds(90);

    /** AgentScope 状态在 Redis 中的保留时间。 */
    private Duration stateTtl = Duration.ofHours(24);

    /** 最终旅行策略 Markdown 文件的根目录。 */
    private Path artifactDir = Path.of(".agentscope/artifacts/travel-strategies");

    /** Agent 单次运行日志的本地保存目录。 */
    private Path runLogDir = Path.of(".agentscope/logs/agent-runs");

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

    public int getHistoryLimit() {
        return historyLimit;
    }

    public void setHistoryLimit(int historyLimit) {
        this.historyLimit = historyLimit;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Duration getStateTtl() {
        return stateTtl;
    }

    public void setStateTtl(Duration stateTtl) {
        this.stateTtl = stateTtl;
    }

    public Path getArtifactDir() {
        return artifactDir;
    }

    public void setArtifactDir(Path artifactDir) {
        this.artifactDir = artifactDir;
    }

    public Path getRunLogDir() {
        return runLogDir;
    }

    public void setRunLogDir(Path runLogDir) {
        this.runLogDir = runLogDir;
    }
}

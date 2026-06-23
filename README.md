# AgentScope 旅行助手 Agent

参考同级 `agent-travel-assistant-demo` 的旅行规划能力，本项目使用 AgentScope Java 搭建了一个 Spring Boot 旅行助手 Agent。

## 功能

- 基于 AgentScope `ReActAgent` 编排大模型推理和工具调用
- 使用 `@Tool` 注册旅行工具：天气查询、城市画像、景点推荐、行程草稿
- 提供 HTTP 聊天接口，支持 `conversationId` 多轮会话
- 使用 `JsonFileAgentStateStore` 做本地会话状态持久化

## 运行

要求 JDK 17+，并配置 ModelScope 访问令牌：

```bash
export MODELSCOPE_API_KEY=你的 ModelScope token
mvn spring-boot:run
```

默认模型配置在 `src/main/resources/application.properties`：

```properties
travel.agent.model=Qwen/Qwen3-235B-A22B-Instruct-2507
travel.agent.base-url=https://api-inference.modelscope.cn/v1
travel.agent.api-key-env=MODELSCOPE_API_KEY
travel.agent.max-iters=6
travel.agent.state-dir=.agentscope/state/travel-assistant
```

## 调用示例

```bash
curl -X POST http://localhost:8080/api/travel-agent/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId": "alice",
    "message": "帮我规划北京3天2晚情侣游，预算3000元，不含住宿，偏人文和美食"
  }'
```

继续多轮对话时，把响应里的 `conversationId` 带回：

```bash
curl -X POST http://localhost:8080/api/travel-agent/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "conversationId": "上一次返回的 conversationId",
    "userId": "alice",
    "message": "如果其中一天大雨，帮我换成室内方案"
  }'
```

响应格式：

```json
{
  "conversationId": "uuid",
  "answer": "中文旅行方案"
}
```

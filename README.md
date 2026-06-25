# AgentScope 旅行助手 Agent

参考同级 `agent-travel-assistant-demo` 的旅行规划能力，本项目使用 AgentScope Java 搭建了一个 Spring Boot 旅行助手 Agent。

## 功能

- 基于 AgentScope `HarnessAgent` 编排大模型推理、工具调用和本机 Workspace
- 使用 `@Tool` 注册旅行工具：天气查询、城市画像、景点推荐、行程草稿
- 提供 HTTP 聊天接口，支持 `conversationId` 多轮会话
- 使用 MySQL 保存业务会话和消息
- 使用 Redis 保存 Agent 单次执行状态和 AgentScope 内部上下文，支持 TTL 自动清理
- Redis 上下文缺失时，从 MySQL 最近消息重建多轮语义上下文
- 使用 `.agentscope/workspace/travel-assistant` 保存 `AGENTS.md`、知识库、记忆和会话运行文件
- 将最终旅行策略生成 Markdown 文档并保存到 Workspace 下

## 运行

要求 JDK 17+，并准备 MySQL、Redis，再配置 ModelScope 访问令牌：

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
travel.agent.history-limit=20
travel.agent.state-ttl=24h
travel.agent.workspace-dir=.agentscope/workspace/travel-assistant
travel.agent.artifact-dir=.agentscope/workspace/travel-assistant/artifacts/travel-strategies

spring.datasource.url=jdbc:mysql://localhost:3306/travel_assistant?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
spring.datasource.username=root
spring.datasource.password=
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

启动时应用会按需初始化本机 Workspace：

```text
.agentscope/workspace/travel-assistant/
├── AGENTS.md
├── knowledge/KNOWLEDGE.md
├── skills/
├── subagents/
└── plans/
```

`AGENTS.md` 和 `knowledge/KNOWLEDGE.md` 只在文件不存在时写入，后续可以直接修改这些文件来调整旅行助手的人格、规则和领域知识。

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
  "answer": "中文旅行方案",
  "artifactPath": ".agentscope/workspace/travel-assistant/artifacts/travel-strategies/alice/uuid/20260623-101500.md"
}
```

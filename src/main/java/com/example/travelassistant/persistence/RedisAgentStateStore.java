package com.example.travelassistant.persistence;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import io.agentscope.core.util.JsonUtils;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/** AgentScope 状态存储实现，将单个 State 和 State 列表保存到 Redis。 */
public class RedisAgentStateStore implements AgentStateStore {

    /** AgentScope 未传用户 ID 时使用的 Redis 命名空间。 */
    private static final String ANON_USER = "anonymous";

    /** 所有 AgentScope 状态 key 的统一前缀。 */
    private static final String KEY_PREFIX = "travel-agent:state:";

    /** 用户维度 session 集合的后缀。 */
    private static final String USER_SESSIONS = "sessions";

    /** session 维度实际状态 key 集合的后缀。 */
    private static final String SESSION_KEYS = "keys";

    private final StringRedisTemplate redisTemplate;

    private final Duration ttl;

    public RedisAgentStateStore(StringRedisTemplate redisTemplate, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    /** 保存单个 AgentScope 状态对象，并索引到用户和会话。 */
    @Override
    public void save(String userId, String sessionId, String key, State state) {
        String redisKey = stateKey(userId, sessionId, key);
        redisTemplate.opsForValue().set(redisKey, JsonUtils.getJsonCodec().toPrettyJson(state), ttl);
        indexSession(userId, sessionId, redisKey);
    }

    /** 保存 AgentScope 状态列表，通常用于消息历史或工具调用轨迹。 */
    @Override
    public void save(String userId, String sessionId, String key, List<? extends State> states) {
        String redisKey = listKey(userId, sessionId, key);
        redisTemplate.delete(redisKey);
        if (!states.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(redisKey, serializeStates(states));
        }
        redisTemplate.expire(redisKey, ttl);
        indexSession(userId, sessionId, redisKey);
    }

    /** 读取单个状态；Redis 无内容时返回空。 */
    @Override
    public <T extends State> Optional<T> get(
            String userId, String sessionId, String key, Class<T> stateType) {
        String json = redisTemplate.opsForValue().get(stateKey(userId, sessionId, key));
        if (!StringUtils.hasText(json)) {
            return Optional.empty();
        }
        return Optional.of(JsonUtils.getJsonCodec().fromJson(json, stateType));
    }

    /** 按写入顺序读取状态列表。 */
    @Override
    public <T extends State> List<T> getList(
            String userId, String sessionId, String key, Class<T> stateType) {
        List<String> values = redisTemplate.opsForList().range(listKey(userId, sessionId, key), 0, -1);
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        List<T> states = new ArrayList<>(values.size());
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                states.add(JsonUtils.getJsonCodec().fromJson(value, stateType));
            }
        }
        return states;
    }

    /** 判断某个用户会话是否还存在 Redis 状态。 */
    @Override
    public boolean exists(String userId, String sessionId) {
        Boolean hasSession =
                redisTemplate.opsForSet().isMember(userSessionsKey(userId), requireSessionId(sessionId));
        if (Boolean.TRUE.equals(hasSession)) {
            return true;
        }
        Long keyCount = redisTemplate.opsForSet().size(sessionKeysKey(userId, sessionId));
        return keyCount != null && keyCount > 0;
    }

    /** 删除某个会话下的全部 AgentScope 状态。 */
    @Override
    public void delete(String userId, String sessionId) {
        String sessionKeysKey = sessionKeysKey(userId, sessionId);
        Set<String> keys = redisTemplate.opsForSet().members(sessionKeysKey);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        redisTemplate.delete(sessionKeysKey);
        redisTemplate.opsForSet().remove(userSessionsKey(userId), requireSessionId(sessionId));
    }

    /** 删除某个会话下指定逻辑 key 对应的单值和列表状态。 */
    @Override
    public void delete(String userId, String sessionId, String key) {
        String stateKey = stateKey(userId, sessionId, key);
        String listKey = listKey(userId, sessionId, key);
        redisTemplate.delete(List.of(stateKey, listKey));
        redisTemplate.opsForSet().remove(sessionKeysKey(userId, sessionId), stateKey, listKey);
    }

    /** 列出某个用户仍在 Redis 中保留状态的 sessionId。 */
    @Override
    public Set<String> listSessionIds(String userId) {
        Set<String> sessionIds = redisTemplate.opsForSet().members(userSessionsKey(userId));
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(sessionIds);
    }

    /** 将 State 列表逐个序列化，便于使用 Redis List 保序存储。 */
    private List<String> serializeStates(List<? extends State> states) {
        List<String> values = new ArrayList<>(states.size());
        for (State state : states) {
            values.add(JsonUtils.getJsonCodec().toJson(state));
        }
        return values;
    }

    /** 维护用户到 session、session 到实际状态 key 的索引，并同步刷新 TTL。 */
    private void indexSession(String userId, String sessionId, String redisKey) {
        String userSessionsKey = userSessionsKey(userId);
        String sessionKeysKey = sessionKeysKey(userId, sessionId);
        redisTemplate.opsForSet().add(userSessionsKey, requireSessionId(sessionId));
        redisTemplate.opsForSet().add(sessionKeysKey, redisKey);
        redisTemplate.expire(userSessionsKey, ttl);
        redisTemplate.expire(sessionKeysKey, ttl);
    }

    private String stateKey(String userId, String sessionId, String key) {
        return scopedKey(userId, sessionId, "state", key);
    }

    private String listKey(String userId, String sessionId, String key) {
        return scopedKey(userId, sessionId, "list", key);
    }

    private String userSessionsKey(String userId) {
        return KEY_PREFIX + normalizeUser(userId) + ":" + USER_SESSIONS;
    }

    private String sessionKeysKey(String userId, String sessionId) {
        return KEY_PREFIX
                + normalizeUser(userId)
                + ":"
                + requireSessionId(sessionId)
                + ":"
                + SESSION_KEYS;
    }

    private String scopedKey(String userId, String sessionId, String kind, String key) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("state key must not be blank");
        }
        return KEY_PREFIX
                + normalizeUser(userId)
                + ":"
                + requireSessionId(sessionId)
                + ":"
                + kind
                + ":"
                + key;
    }

    private String normalizeUser(String userId) {
        return StringUtils.hasText(userId) ? userId : ANON_USER;
    }

    private String requireSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        return sessionId;
    }
}

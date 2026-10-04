package com.frietsync.backend.service.impl.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;

    private static final Duration EXPIRY = Duration.ofDays(7);

    public void save(String token, UUID userId) {
        redisTemplate.opsForValue().set("refresh:" + token, userId.toString(), EXPIRY);

        String userKey = "user-tokens:" + userId;
        redisTemplate.opsForSet().add(userKey, token);
        redisTemplate.expire(userKey, EXPIRY);
    }

    public UUID getUserId(String token) {
        String userId = redisTemplate.opsForValue().get("refresh:" + token);
        if (userId == null) {
            return null;
        }
        return UUID.fromString(userId);
    }

    public void delete(String token) {
        UUID userId = getUserId(token);

        redisTemplate.delete("refresh:" + token);

        if (userId != null) {
            redisTemplate.opsForSet().remove("user-tokens:" + userId, token);
        }
    }

    public void deleteAllForUser(UUID userId) {
        String userKey = "user-tokens:" + userId;
        Set<String> tokens = redisTemplate.opsForSet().members(userKey);

        if (tokens != null) {
            for (String token : tokens) {
                redisTemplate.delete("refresh:" + token);
            }
        }

        redisTemplate.delete(userKey);
    }
    public UUID useRefreshToken(String token) {
        String script =
                "local userId = redis.call('GET', KEYS[1]) " +
                        "if not userId then return nil end " +
                        "redis.call('DEL', KEYS[1]) " +
                        "redis.call('SREM', 'user-tokens:' .. userId, ARGV[1]) " +
                        "return userId";

        DefaultRedisScript<String> redisScript = new DefaultRedisScript<>(script, String.class);

        String userId = redisTemplate.execute(redisScript, List.of("refresh:" + token), token);

        if (userId == null) {
            return null;
        }
        return UUID.fromString(userId);
    }
}
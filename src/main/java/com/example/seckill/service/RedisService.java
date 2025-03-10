package com.example.seckill.service;

import com.example.seckill.redis.KeyPrefix;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    // Get key with prefix
    public String getRealKey(KeyPrefix prefix, String key) {
        return prefix.getPrefix() + ":" + key;
    }
    
    // Get value for key
    public <T> T get(KeyPrefix prefix, String key) {
        String realKey = getRealKey(prefix, key);
        return (T) redisTemplate.opsForValue().get(realKey);
    }
    
    // Set key-value with expiration
    public <T> boolean set(KeyPrefix prefix, String key, T value) {
        String realKey = getRealKey(prefix, key);
        int expireSeconds = prefix.expireSeconds();
        try {
            if (expireSeconds <= 0) {
                redisTemplate.opsForValue().set(realKey, value);
            } else {
                redisTemplate.opsForValue().set(realKey, value, expireSeconds, TimeUnit.SECONDS);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    // Check if key exists
    public boolean exists(KeyPrefix prefix, String key) {
        String realKey = getRealKey(prefix, key);
        return redisTemplate.hasKey(realKey);
    }
    
    // Delete key
    public boolean delete(KeyPrefix prefix, String key) {
        String realKey = getRealKey(prefix, key);
        return redisTemplate.delete(realKey);
    }
    
    // Increment value
    public Long incr(KeyPrefix prefix, String key) {
        String realKey = getRealKey(prefix, key);
        return redisTemplate.opsForValue().increment(realKey);
    }
    
    // Decrement value
    public Long decr(KeyPrefix prefix, String key) {
        String realKey = getRealKey(prefix, key);
        return redisTemplate.opsForValue().decrement(realKey);
    }

    // In RedisService implementation:
    public boolean setIfNotExists(String key, String value, int expireSeconds) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, expireSeconds, TimeUnit.SECONDS);
        return result != null && result;
    }

    public boolean delete(String key) {
        Boolean result = redisTemplate.delete(key);
        return result != null && result;
    }

    public <T> T executeScript(DefaultRedisScript<T> script, List<String> keys, Object... args) {
        return redisTemplate.execute(script, keys, args);
    }

    public List<Integer> mget(String... keys) {
        List<Object> results = redisTemplate.opsForValue().multiGet(Arrays.asList(keys));
        
        List<Integer> integers = new ArrayList<>();
        for (Object result : results) {
            integers.add(result != null ? Integer.parseInt(result.toString()) : null);
        }
        return integers;
    }
}
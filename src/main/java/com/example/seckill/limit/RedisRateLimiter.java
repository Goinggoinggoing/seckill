package com.example.seckill.limit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class RedisRateLimiter {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    private DefaultRedisScript<Long> tokenBucketScript;
    
    @PostConstruct
    public void init() {
        tokenBucketScript = new DefaultRedisScript<>();
        tokenBucketScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/token_bucket.lua")));
        tokenBucketScript.setResultType(Long.class);
        log.info("Token bucket script initialized");
    }
    
    /**
     * Try to acquire token(s)
     * 
     * @param key rate limiter key
     * @param rate token generation rate (per second)
     * @param capacity token bucket capacity
     * @param requested number of tokens requested
     * @return returns >0 indicates success, <=0 indicates failure, 
     *         and the absolute value represents the waiting time in milliseconds
     */
    public long tryAcquire(String key, double rate, int capacity, int requested) {
        try {
            // Call the Lua script to perform an atomic operation
            List<String> keys = Collections.singletonList(key);
            
            // Current timestamp (milliseconds)
            long now = System.currentTimeMillis();
            
            // Debug log
            log.debug("Executing token bucket script - key: {}, rate: {}, capacity: {}, now: {}, requested: {}", 
                key, rate, capacity, now, requested);
            
            // Execute the Lua script with explicit type conversion
            Long result = redisTemplate.execute(
                tokenBucketScript, 
                keys, 
                Double.toString(rate), 
                Integer.toString(capacity), 
                Long.toString(now),
                Integer.toString(requested)
            );
            
            if (result == null) {
                log.error("Redis token bucket script returned null for key: {}", key);
                return -1; // Default failure
            }
            
            return result;
        } catch (Exception e) {
            log.error("Error executing token bucket script for key: {}", key, e);
            return -1;
        }
    }
    
    /**
     * Try to acquire one token
     */
    public boolean tryAcquireOne(String key, double rate, int capacity) {
        return tryAcquire(key, rate, capacity, 1) > 0;
    }
    
    /**
     * Try to acquire tokens and return waiting time
     * 
     * @return 0 indicates success, >0 indicates the waiting time in milliseconds needed
     */
    public long tryAcquireWithWaitTime(String key, double rate, int capacity, int requested) {
        long result = tryAcquire(key, rate, capacity, requested);
        if (result > 0) {
            return 0; // Acquired successfully, no need to wait
        } else {
            return Math.abs(result); // Return the positive waiting time
        }
    }
}
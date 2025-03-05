package com.example.seckill.utils;

import com.example.seckill.service.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RedisDistributedLock {
    private final RedisService redisService;
    private final String lockKey;
    private final String lockValue; // Unique ID to ensure we only delete our own locks
    private final int expireSeconds;

    // Lua script for atomic lock release
    private static final String RELEASE_LOCK_SCRIPT = 
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";
    
    // Script compiled once for efficiency
    private static final DefaultRedisScript<Long> RELEASE_LOCK_REDIS_SCRIPT = new DefaultRedisScript<>(RELEASE_LOCK_SCRIPT, Long.class);

    /**
     * Create a new Redis distributed lock
     */
    public RedisDistributedLock(RedisService redisService, String lockKey, int expireSeconds) {
        this.redisService = redisService;
        this.lockKey = lockKey;
        // Generate a random UUID as lock value to prevent deleting others' locks
        this.lockValue = UUID.randomUUID().toString();
        this.expireSeconds = expireSeconds;
    }

    /**
     * Try to acquire the lock with a timeout
     * @param timeoutMs maximum time to wait for lock acquisition in milliseconds
     * @return true if lock was acquired, false otherwise
     */
    public boolean tryLock(long timeoutMs) {
        long startTime = System.currentTimeMillis();
        long waitTime = timeoutMs;
        
        do {
            boolean acquired = redisService.setIfNotExists(lockKey, lockValue, expireSeconds);
            if (acquired) {
                log.debug("Lock acquired: {}", lockKey);
                return true;
            }
            
            // Wait a bit before retrying
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            
            waitTime = timeoutMs - (System.currentTimeMillis() - startTime);
        } while (waitTime > 0);
        
        log.debug("Failed to acquire lock after {}ms: {}", timeoutMs, lockKey);
        return false;
    }

    /**
     * Release the lock if we own it, using Lua script for atomicity
     * @return true if lock was released, false if it wasn't our lock
     */
    public boolean unlock() {
        try {
            Long result = redisService.executeScript(
                RELEASE_LOCK_REDIS_SCRIPT,
                Collections.singletonList(lockKey),
                lockValue
            );
            boolean released = result != null && result == 1;
            if (released) {
                log.debug("Lock released: {}", lockKey);
            } else {
                log.warn("Failed to release lock (not owner or expired): {}", lockKey);
            }
            return released;
        } catch (Exception e) {
            log.error("Error releasing lock: {}", lockKey, e);
            return false;
        }
    }
}
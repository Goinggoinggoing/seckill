package com.example.seckill.aspect;

import com.example.seckill.annotation.RateLimit;
import com.example.seckill.exception.RateLimitException;
import com.example.seckill.limit.RedisRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

@Aspect
@Component
@Slf4j
public class RateLimitAspect {

    @Autowired
    private RedisRateLimiter redisRateLimiter;
    
    private static final String RATE_LIMIT_PREFIX = "rate_limit:";

    @Before("@annotation(com.example.seckill.annotation.RateLimit)")
    public void rateLimit(JoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        
        // Get annotation information
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return;
        }
        
        // Build rate limit key
        String key = buildRateLimitKey(rateLimit, point);
        
        // Retrieve rate limiting parameters
        double rate = rateLimit.rate();
        int capacity = rateLimit.capacity();
        int tokens = rateLimit.tokens();
        
        // Execute token bucket rate limiting logic
        long waitTime = redisRateLimiter.tryAcquireWithWaitTime(key, rate, capacity, tokens);
        
        if (waitTime > 0) {
            // Token acquisition failed, waiting is required
            String message = rateLimit.message();
            if (waitTime > 1000) {
                // If wait time exceeds 1 second, append the wait time to the message
                message += "，需等待" + (waitTime / 1000) + "秒";
            }
            log.warn("Rate limit exceeded for key: {}, wait time: {} ms", key, waitTime);
            throw new RateLimitException(message);
        }
    }
    
    /**
     * Build the rate limiting key
     */
    private String buildRateLimitKey(RateLimit rateLimit, JoinPoint point) {
        StringBuilder key = new StringBuilder(RATE_LIMIT_PREFIX);
        
        // Add custom key prefix
        if (!rateLimit.key().isEmpty()) {
            key.append(rateLimit.key()).append(":");
        } else {
            // If key is not specified, use the method's fully qualified name
            key.append(point.getSignature().getDeclaringTypeName())
               .append(".")
               .append(point.getSignature().getName())
               .append(":");
        }
        
        // Append rate limit object identifier based on the rate limit type
        switch (rateLimit.type()) {
            case USER:
                // Here, it is assumed that the userId in PathVariable is the first parameter
                key.append("user:").append(point.getArgs()[0]);
                break;
            case IP:
                HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
                String ip = getClientIp(request);
                key.append("ip:").append(ip);
                break;
            case GLOBAL:
                key.append("global");
                break;
            default:
                key.append("global");
        }
        
        return key.toString();
    }
    
    /**
     * Get client IP address
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
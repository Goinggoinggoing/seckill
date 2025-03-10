package com.example.seckill.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    /**
     * Unique identifier for rate limiting, used to distinguish different interfaces' limits
     */
    String key() default "";
    
    /**
     * Rate limiting mode, supports limiting by user ID, IP, or globally
     */
    RateLimitType type() default RateLimitType.USER;
    
    /**
     * Token generation rate, the number of tokens generated per second
     */
    double rate() default 1.0;
    
    /**
     * Token bucket capacity (maximum number of tokens), allowing burst requests
     */
    int capacity() default 5;
    
    /**
     * Number of tokens consumed per request
     */
    int tokens() default 1;
    
    /**
     * Rate limit prompt message
     */
    String message() default "请求过于频繁，请稍后再试";
    
    /**
     * Rate limit type enumeration
     */
    enum RateLimitType {
        /**
         * Rate limiting by user ID
         */
        USER,
        
        /**
         * Rate limiting by IP address
         */
        IP,
        
        /**
         * Global rate limiting
         */
        GLOBAL
    }
}
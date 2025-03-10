package com.example.seckill.exception;

public class RateLimitException extends RuntimeException {
    
    public RateLimitException(String message) {
        super(message);
    }
}
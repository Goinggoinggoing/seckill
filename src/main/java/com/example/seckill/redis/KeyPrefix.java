package com.example.seckill.redis;

public interface KeyPrefix {
    int expireSeconds();
    String getPrefix();
}
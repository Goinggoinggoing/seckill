package com.example.seckill.util;

import java.util.UUID;

public class UUIDUtil {
    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    public static String generateOrderNo() {
        return System.currentTimeMillis() + "" + (int)(Math.random() * 900000 + 100000);
    }
}
package com.example.seckill.redis;

public class SeckillKey extends BasePrefix {
    
    public SeckillKey(int expireSeconds, String prefix) {
        super(expireSeconds, prefix);
    }
    
    public static SeckillKey isGoodsOver = new SeckillKey(0, "go");
    public static SeckillKey goodsStock = new SeckillKey(0, "gs");
    public static SeckillKey seckillPending = new SeckillKey( 60, "sp");  // 60 seconds expiry

    public static final SeckillKey txStartTime = new SeckillKey(600, "txst"); // 10 min timeout

    public static final SeckillKey reservedStock = new SeckillKey(0, "rs"); // reserved stock count
}
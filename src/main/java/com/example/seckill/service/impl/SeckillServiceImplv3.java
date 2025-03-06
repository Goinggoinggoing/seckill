package com.example.seckill.service.impl;

import com.example.seckill.entity.SeckillOrder;
import com.example.seckill.mq.MQProducer;
import com.example.seckill.redis.SeckillKey;
import com.example.seckill.service.GoodsService;
import com.example.seckill.service.OrderService;
import com.example.seckill.service.RedisService;
import com.example.seckill.service.SeckillService;
import com.example.seckill.utils.RedisDistributedLock;
import com.example.seckill.vo.GoodsVo;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SeckillServiceImplv3 implements SeckillService {

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private OrderService orderService;
    
    @Autowired
    private RedisService redisService;

    private static final String LOCK_PREFIX = "init_stock_lock:";
    private static final int LOCK_EXPIRE_SECONDS = 10; // 10 seconds lock expiration
    private static final long LOCK_TIMEOUT_MS = 5000; // 5 seconds timeout for acquiring lock

    @Autowired
    private MQProducer mqProducer;

    /**
     * Seckill implementation optimized with MQ
     * 1. Pre-deduct stock (Redis)
     * 2. Fast fail
     * 3. Create order
     * 4. MQ asynchronous stock reduction
     */
    @Override
    public SeckillOrder seckill(Long userId, GoodsVo goodsVo) {
        // 1. First check if goods are already marked as sold out (fast-fail check)
        if (isGoodsOver(goodsVo.getId())) {
            return null;
        }

        // Lazy initialization of stock in Redis if needed
        initStockIfNeeded(goodsVo);

        // 2. Pre-deduct stock in Redis to reduce database access
        Long stock = redisService.decr(SeckillKey.goodsStock, "" + goodsVo.getId());
        
        // 3. Check if stock is sufficient
        if (stock < 0) {
            // If stock is insufficient, rollback Redis stock and mark goods as sold out
            redisService.incr(SeckillKey.goodsStock, "" + goodsVo.getId());
            setGoodsOver(goodsVo.getId());
            return null;
        }
        
        // 4. Using transaction message to create order and notify inventory service
        try {
            String transactionId = mqProducer.sendStockReductionTransactionMessage(userId, goodsVo);
                        
            // 5. Return a temporary order to indicate processing
            SeckillOrder order = new SeckillOrder();
            order.setUserId(userId);
            order.setGoodsId(goodsVo.getId());
            order.setTransactionId(transactionId);
            return order;
        } catch (Exception e) {
            log.error("Failed to send transaction message", e);
            // Rollback Redis stock on error
            redisService.incr(SeckillKey.goodsStock, "" + goodsVo.getId());
            return null;
        }
    }

    private void initStockIfNeeded(GoodsVo goodsVo) {
        Long goodsId = goodsVo.getId();
        
        // Check if stock already exists in Redis
        if (!redisService.exists(SeckillKey.goodsStock, "" + goodsId)) {
            // Create distributed lock with proper timeout and unique identifier
            String lockKey = LOCK_PREFIX + goodsId;
            RedisDistributedLock lock = new RedisDistributedLock(redisService, lockKey, LOCK_EXPIRE_SECONDS);
            
            boolean lockAcquired = lock.tryLock(LOCK_TIMEOUT_MS);
            if (lockAcquired) {
                try {
                    // Double-check if another thread has already initialized (check-lock-check pattern)
                    if (!redisService.exists(SeckillKey.goodsStock, "" + goodsId)) {
                        log.info("Initializing stock for goods: {}", goodsId);
                        // Get fresh stock count from database
                        GoodsVo freshGoodsInfo = goodsService.getGoodsVoByGoodsId(goodsId);
                        if (freshGoodsInfo != null) {
                            redisService.set(SeckillKey.goodsStock, "" + goodsId, freshGoodsInfo.getStockCount());
                            log.info("Stock initialized for goods {}: {}", goodsId, freshGoodsInfo.getStockCount());
                        } else {
                            log.error("Failed to get goods info for ID: {}", goodsId);
                        }
                    }
                } finally {
                    // Always release the lock when done, even if exception occurs
                    lock.unlock();
                }
            } else {
                // Another thread is initializing, wait briefly and continue
                try {
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Mark goods as sold out
     */
    private void setGoodsOver(Long goodsId) {
        redisService.set(SeckillKey.isGoodsOver, "" + goodsId, true);
    }
    
    /**
     * Check if goods are sold out
     */
    private boolean isGoodsOver(Long goodsId) {
        return redisService.exists(SeckillKey.isGoodsOver, "" + goodsId);
    }

    /**
     * Get seckill result
     * @return orderId: successful, -1: seckill failure
     */
    @Override
    public Long getSeckillResult(Long userId, Long goodsId) {
        // 1. Order creation is synchronous so just query - if an order is created, seckill is successful
        SeckillOrder order = orderService.getOrderByUserIdGoodsId(userId, goodsId);
        
        if (order != null) {
            // Seckill successful - order created
            return order.getId();
        }
        
        // 3. Check if goods are sold out
        boolean isOver = isGoodsOver(goodsId);
        if (isOver) {
            // Sold out, seckill failed
            return -1L;
        }
        
        // No order and goods not sold out, possibly processing hasn't started or an error occurred
        return -1L;
    }
}
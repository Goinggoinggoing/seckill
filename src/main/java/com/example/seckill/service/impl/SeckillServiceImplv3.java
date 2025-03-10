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

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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

    private static final boolean TIMEOUT_CANCEL_ORDER = false; // Whether to cancel order


    @Autowired
    private MQProducer mqProducer;

    // decrease stock and pre
    private static final String DECREASE_STOCK_SCRIPT = 
        "local stock = redis.call('decr', KEYS[1]) " +
        "if stock >= 0 then " +
        "  redis.call('incr', KEYS[2]) " +
        "  return stock " +
        "else " +
        "  redis.call('incr', KEYS[1]) " + // rollback if insufficient
        "  return -1 " +
        "end";

    // Script compiled once for efficiency
    private static final DefaultRedisScript<Long> DECREASE_STOCK_REDIS_SCRIPT = new DefaultRedisScript<>(DECREASE_STOCK_SCRIPT, Long.class);


    private static final String ROLLBACK_STOCK_SCRIPT = 
        "redis.call('incr', KEYS[1]) " +
        "redis.call('decr', KEYS[2]) " +
        "return 1";

    // Script compiled once for efficiency
    private static final DefaultRedisScript<Long> ROLLBACK_STOCK_REDIS_SCRIPT = 
        new DefaultRedisScript<>(ROLLBACK_STOCK_SCRIPT, Long.class);

    // Local cache for sold-out goods with 5 minutes expiration
    private final Cache<Long, Boolean> localSoldOutCache = CacheBuilder.newBuilder()
            .maximumSize(1000)  // Maximum items in cache
            .expireAfterWrite(5, TimeUnit.MINUTES)  // Cache entries expire after 5 minutes
            .build();

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
        Long result = redisService.executeScript(
            DECREASE_STOCK_REDIS_SCRIPT,
            Arrays.asList(
                redisService.getRealKey(SeckillKey.goodsStock, "" + goodsVo.getId()), 
                redisService.getRealKey(SeckillKey.reservedStock, "" + goodsVo.getId())
            )
        );

        // Check result
        if (result < 0) {
            // Stock insufficient, already rolled back in the script
            // redisService.incr(SeckillKey.goodsStock, "" + goodsVo.getId());
            setGoodsOver(goodsVo.getId());
            return null;
        }
        
        // 4. Using transaction message to create order and notify inventory service
        try {
            String transactionId = mqProducer.sendStockReductionTransactionMessage(userId, goodsVo);
            
            // send delay message to cancel order
            if (TIMEOUT_CANCEL_ORDER) {
                // Note: Since this is not a transactional message, there might be a scenario where the order exists but the message was not sent.
                mqProducer.sendOrderCancellationMessage(transactionId);
            }
                        
            // 5. Return a temporary order to indicate processing
            SeckillOrder order = new SeckillOrder();
            order.setUserId(userId);
            order.setGoodsId(goodsVo.getId());
            order.setTransactionId(transactionId);
            return order;
        } catch (Exception e) {
            log.error("Failed to send transaction message", e);
            // Atomically rollback both Redis stock and reserved stock on error
            redisService.executeScript(
                ROLLBACK_STOCK_REDIS_SCRIPT,
                Arrays.asList(
                    redisService.getRealKey(SeckillKey.goodsStock, "" + goodsVo.getId()),
                    redisService.getRealKey(SeckillKey.reservedStock, "" + goodsVo.getId())
                )
            );
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
        // Update Redis
        redisService.set(SeckillKey.isGoodsOver, "" + goodsId, true);
        
        // Update local cache
        localSoldOutCache.put(goodsId, true);
        log.debug("Goods {} marked as sold out in both Redis and local cache", goodsId);
    }
    
    /**
     * Check if goods are sold out using local cache first, then Redis
     */
    private boolean isGoodsOver(Long goodsId) {
        // First check local cache (much faster)
        Boolean isOver = localSoldOutCache.getIfPresent(goodsId);
        if (isOver != null && isOver) {
            return true;
        }
        
        // If not in local cache, check Redis
        boolean isSoldOutInRedis = redisService.exists(SeckillKey.isGoodsOver, "" + goodsId);
        
        // If found in Redis but not in local cache, update local cache
        if (isSoldOutInRedis) {
            localSoldOutCache.put(goodsId, true);
            log.debug("Goods {} sold-out status loaded from Redis to local cache", goodsId);
        }
        
        return isSoldOutInRedis;
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
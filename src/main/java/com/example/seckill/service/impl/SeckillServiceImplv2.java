package com.example.seckill.service.impl;

import com.example.seckill.entity.SeckillOrder;
import com.example.seckill.mq.StockReductionConsumer;
import com.example.seckill.redis.SeckillKey;
import com.example.seckill.service.GoodsService;
import com.example.seckill.service.OrderService;
import com.example.seckill.service.RedisService;
import com.example.seckill.service.SeckillService;
import com.example.seckill.utils.RedisDistributedLock;
import com.example.seckill.vo.GoodsVo;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SeckillServiceImplv2 implements SeckillService {

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private OrderService orderService;
    
    @Autowired
    private RedisService redisService;


    @Autowired
    private ApplicationContext applicationContext;

    private static final ExecutorService orderExecutor = Executors.newFixedThreadPool(5000);

    private static final String LOCK_PREFIX = "init_stock_lock:";
    private static final int LOCK_EXPIRE_SECONDS = 10; // 10 seconds lock expiration
    private static final long LOCK_TIMEOUT_MS = 5000; // 5 seconds timeout for acquiring lock



    /**
     * Redis优化后的秒杀实现
     * 1. 预减库存 (Redis)
     * 2. 快速失败
     * 3. 异步下单
     */
    @Override
    public SeckillOrder seckill(Long userId, GoodsVo goodsVo) {
        // 1. First check if goods are already marked as sold out (fast-fail check)
        if (isGoodsOver(goodsVo.getId())) {
            return null ;
        }

        // Lazy initialization of stock in Redis if needed
        initStockIfNeeded(goodsVo);

        // 2. Redis预减库存，减少对数据库的访问
        Long stock = redisService.decr(SeckillKey.goodsStock, "" + goodsVo.getId());
        
        // 3. 判断库存是否充足
        if (stock < 0) {
            // 库存不足，回滚Redis库存，设置商品已售罄标识
            redisService.incr(SeckillKey.goodsStock, "" + goodsVo.getId());
            setGoodsOver(goodsVo.getId());
            return null;
        }
        
        // 4. 减库存，下订单，写入订单（原子操作） 只有少部分请求会进来
        // SeckillOrder seckillOrder = createSeckillOrder(userId, goodsVo);  同步操作

        final Long finalUserId = userId;
        final GoodsVo finalGoodsVo = goodsVo;

        // Mark this order as pending in Redis
        redisService.set(SeckillKey.seckillPending, finalUserId + "_" + finalGoodsVo.getId(), System.currentTimeMillis());

        orderExecutor.submit(() -> {
            try {
                SeckillServiceImplv2 proxy = applicationContext.getBean(SeckillServiceImplv2.class);
                SeckillOrder order = proxy.createSeckillOrder(finalUserId, finalGoodsVo);
            } catch (Exception e) {
                log.error("Create order async error: ", e);
                redisService.incr(SeckillKey.goodsStock, "" + finalGoodsVo.getId());
            } finally {
                // Always clean up the pending status
                redisService.delete(SeckillKey.seckillPending, finalUserId + "_" + finalGoodsVo.getId());
            }
        });

        // 5. 返回一个临时订单，表示正在处理中
        SeckillOrder order = new SeckillOrder();
        order.setUserId(userId);
        order.setGoodsId(goodsVo.getId());
        return order;
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
     * 事务操作：减库存、创建订单
     */
    @Transactional
    public SeckillOrder createSeckillOrder(Long userId, GoodsVo goodsVo) {
        // 1. 减少数据库中的库存
        boolean success = goodsService.reduceStockWhenLeft(goodsVo.getSeckillGoodsId());
        if (!success) {
            // 如果数据库减库存失败，回滚Redis，并标记商品售罄
            redisService.incr(SeckillKey.goodsStock, "" + goodsVo.getId());
            setGoodsOver(goodsVo.getId());
            return null;
        }
        
        // 2. 创建订单
        return orderService.createOrder(userId, goodsVo);
    }
    
    /**
     * 标记商品已售罄
     */
    private void setGoodsOver(Long goodsId) {
        redisService.set(SeckillKey.isGoodsOver, "" + goodsId, true);
    }
    
    /**
     * 判断商品是否售罄
     */
    private boolean isGoodsOver(Long goodsId) {
        return redisService.exists(SeckillKey.isGoodsOver, "" + goodsId);
    }

    /**
     * 获取秒杀结果
     * @return orderId: 成功, -1: 秒杀失败, 0: 排队中, -2: 处理超时
     */
    @Override
    public Long getSeckillResult(Long userId, Long goodsId) {
        String pendingKey = userId + "_" + goodsId;

        // 1. 优先检查 pending 状态
        Long startTime = redisService.get(SeckillKey.seckillPending, pendingKey);
        if (startTime != null) {
            long diff = System.currentTimeMillis() - startTime;
            if (diff > 30000) { // 超时30秒
                redisService.delete(SeckillKey.seckillPending, pendingKey);
                return -2L; // 处理超时
            }
            return 0L; // 正在处理
        }

        // 2. 查询订单信息 - 如果订单已创建，表示秒杀成功
        SeckillOrder order = orderService.getOrderByUserIdGoodsId(userId, goodsId);
        
        if (order != null) {
            // 秒杀成功 - 订单已创建
            return order.getId();
        }
        
        // 3. 查询商品是否已售罄
        boolean isOver = isGoodsOver(goodsId);
        if (isOver) {
            // 已售罄，秒杀失败
            return -1L;
        }
        
        // 没有订单，商品也没卖完，且不在处理队列中 - 可能是请求尚未开始处理或处理出错
        return -1L;
    }
}
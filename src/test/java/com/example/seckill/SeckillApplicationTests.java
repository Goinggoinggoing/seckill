package com.example.seckill;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.example.seckill.entity.SeckillOrder;
import com.example.seckill.service.GoodsService;
import com.example.seckill.service.OrderService;
import com.example.seckill.service.RedisService;
import com.example.seckill.service.SeckillService;
import com.example.seckill.vo.GoodsVo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SeckillApplicationTests {

    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private GoodsService goodsService;
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private RedisService redisService;
    
    @Autowired
    @Qualifier("seckillServiceImplv3")
    private SeckillService seckillService;

    @Test
    void contextLoads() {
    }


    @Test
    void testSeckillConcurrency() throws Exception {
        // 测试配置
        final int CONCURRENT_USERS = 100 * 10000;  // 并发请求数
        final int GOODS_ID = 1;           // 测试商品ID
        final long POLL_INTERVAL_MS = 200; // 轮询间隔时间
        
        // 统计结果计数器
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failCount = new AtomicInteger(0);
        final AtomicInteger errorCount = new AtomicInteger(0);
        
        // 分别统计成功和失败场景的总消耗时间（毫秒）
        final AtomicLong totalSuccessTime = new AtomicLong(0);
        final AtomicLong totalFailTime = new AtomicLong(0);
        
        // 获取初始库存信息
        GoodsVo goodsVo = goodsService.getGoodsVoByGoodsId((long) GOODS_ID);
        int initialStock = goodsVo != null ? goodsVo.getStockCount() : 0;
        
        System.out.println("Starting load test with " + CONCURRENT_USERS + " concurrent users");
        System.out.println("Target: product ID " + GOODS_ID + " with initial stock: " + initialStock);
        
        // 使用线程池
        ExecutorService executor = Executors.newFixedThreadPool(1000);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_USERS);
        
        long startTime = System.currentTimeMillis();
        
        // 提交所有任务到线程池
        for (int i = 0; i < CONCURRENT_USERS; i++) {
            final long userId = 10000 + i;  // 生成唯一用户ID
            
            executor.submit(() -> {
                long opStart = System.currentTimeMillis();
                try {
                    // 直接调用业务层进行测试
                    SeckillOrder order = seckillService.seckill(userId, goodsVo);
                    
                    if (order != null) {
                        // 成功：轮询获取秒杀最终结果
                        // while (true) {
                        //     Long resultCode = seckillService.getSeckillResult(userId, goodsVo.getId());
                        //     // 当 resultCode 不为null且不等于0时认为已获得最终结果
                        //     if (resultCode != null && resultCode != 0L) {
                        //         break;
                        //     }
                        //     try {
                        //         Thread.sleep(POLL_INTERVAL_MS);
                        //     } catch (InterruptedException ie) {
                        //         Thread.currentThread().interrupt();
                        //         break;
                        //     }
                        // }
                        long opEnd = System.currentTimeMillis();
                        totalSuccessTime.addAndGet(opEnd - opStart);
                        successCount.incrementAndGet();
                    } else {
                        long opEnd = System.currentTimeMillis();
                        totalFailTime.addAndGet(opEnd - opStart);
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    System.err.println("Error for user " + userId + ": " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 等待所有任务完成（或者超时2分钟）
        latch.await(5, TimeUnit.MINUTES);
        long endTime = System.currentTimeMillis();
        
        executor.shutdown();

        try {
            Thread.sleep(30 * 1000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        
        // 获取测试后的库存信息
        GoodsVo finalGoodsInfo = goodsService.getGoodsVoByGoodsId((long) GOODS_ID);
        int finalStock = finalGoodsInfo != null ? finalGoodsInfo.getStockCount() : 0;
        
        // 获取数据库中实际订单数（需要在OrderService中实现该方法）
        int orderCount = orderService.countOrdersByGoodsId((long) GOODS_ID);
        
        // 计算平均运行时间
        double avgSuccessTime = successCount.get() > 0 ? totalSuccessTime.get() * 1.0 / successCount.get() : 0;
        double avgFailTime = failCount.get() > 0 ? totalFailTime.get() * 1.0 / failCount.get() : 0;
        
        // 打印测试结果
        System.out.println("\n--- Load Test Results ---");
        System.out.println("Test duration: " + (endTime - startTime) + "ms");
        System.out.println("Successful purchases (client side): " + successCount.get() + ", average time: " + avgSuccessTime + "ms");
        System.out.println("Failed purchases: " + failCount.get() + ", average time: " + avgFailTime + "ms");
        System.out.println("Errors: " + errorCount.get());
        
        // 检查是否出现超卖情况
        System.out.println("\n--- Overselling Check ---");
        System.out.println("Initial stock: " + initialStock);
        System.out.println("Final stock: " + finalStock);
        System.out.println("Actual orders in DB: " + orderCount);
        
        if (orderCount > initialStock) {
            System.err.println("OVERSELLING DETECTED! More orders than initial stock.");
        } else {
            System.out.println("No overselling detected.");
        }
        
        if (initialStock - orderCount != finalStock) {
            System.err.println("STOCK INCONSISTENCY! Initial - orders != final");
        } else {
            System.out.println("Stock counts are consistent.");
        }
    }
}

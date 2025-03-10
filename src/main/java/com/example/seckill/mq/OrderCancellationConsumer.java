package com.example.seckill.mq;

import com.example.seckill.dao.GoodsDao;
import com.example.seckill.entity.SeckillOrder;
import com.example.seckill.redis.SeckillKey;
import com.example.seckill.service.OrderService;
import com.example.seckill.service.RedisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RocketMQMessageListener(
    topic = MQProducer.TOPIC_ORDER_CANCEL,
    consumerGroup = "order-cancellation-consumer-group"
)
public class OrderCancellationConsumer implements RocketMQListener<String> {

    @Autowired
    private OrderService orderService;
    
    @Autowired
    private RedisService redisService;

    @Autowired
    private GoodsDao goodsDao;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationContext applicationContext;
    
    // Redis script to rollback stock
    private static final String ROLLBACK_STOCK_SCRIPT = 
        "redis.call('incr', KEYS[1]) " +
        "redis.call('decr', KEYS[2]) " +
        "return 1";

    private static final DefaultRedisScript<Long> ROLLBACK_STOCK_REDIS_SCRIPT = 
        new DefaultRedisScript<>(ROLLBACK_STOCK_SCRIPT, Long.class);
    
    @Override
    public void onMessage(String message) {
        log.info("Received order cancellation message: {}", message);
        try {
            Map<String, Object> msgMap = objectMapper.readValue(message, HashMap.class);
            String transactionId = (String) msgMap.get("transactionId");
            
            // Process order cancellation
            if (transactionId != null && !transactionId.isEmpty()) {
                OrderCancellationConsumer proxy = applicationContext.getBean(OrderCancellationConsumer.class);
                proxy.processOrderCancellation(transactionId);
            } else {
                log.error("Invalid order cancellation message, transactionId is missing: {}", message);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse order cancellation message: {}", message, e);
        }
    }
    
    @Transactional
    public void processOrderCancellation(String transactionId) {
        // 1. Get order information
        SeckillOrder order = orderService.getOrderByTransactionId(transactionId);
        
        if (order == null) {
            log.info("Order not found for cancellation, transactionId: {}", transactionId);
            return;
        }
        
        // 2. Check order status, only unpaid orders can be cancelled
        if (order.getStatus() != 0) {
            log.info("Order already processed (paid or cancelled), transactionId: {}, status: {}", 
                    transactionId, order.getStatus());
            return;
        }
        
        // 3. Update order status to cancelled
        boolean success = orderService.cancelOrder(transactionId);
        
        if (success) {
            log.info("Order cancelled successfully: {}", transactionId);
            
            // 4. Rollback stock
            goodsDao.rollbackStock(order.getGoodsId());
            rollbackRedisStock(order.getGoodsId());

            // Ideally, we also need to clear the out-of-stock flag, but this is simplified here
        } else {
            log.error("Failed to cancel order: {}", transactionId);
        }
    }
    
    private void rollbackRedisStock(Long goodsId) {
        redisService.executeScript(
            ROLLBACK_STOCK_REDIS_SCRIPT,
            Arrays.asList(
                redisService.getRealKey(SeckillKey.goodsStock, "" + goodsId),
                redisService.getRealKey(SeckillKey.reservedStock, "" + goodsId)
            )
        );
    }
}
package com.example.seckill.mq;

import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import com.example.seckill.entity.SeckillOrder;
import com.example.seckill.redis.SeckillKey;
import com.example.seckill.service.GoodsService;
import com.example.seckill.service.OrderService;
import com.example.seckill.service.RedisService;
import com.example.seckill.vo.GoodsVo;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RocketMQTransactionListener
public class SeckillTransactionListener implements RocketMQLocalTransactionListener {

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private OrderService orderService;
    
    @Autowired
    private RedisService redisService;
    
    // Store transaction execution results for check mechanism
    private final ConcurrentHashMap<String, RocketMQLocalTransactionState> localTransactionMap = new ConcurrentHashMap<>();
    
    // Constants for transaction management
    private static final boolean NEED_CHECK_TIMEOUT = true;

    /**
     * Execute local transaction after half message is sent
     */
    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        try {
            log.info("Executing local transaction for message: {}", msg);
            
            // Parse the message body and arguments
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) arg;
            Long userId = (Long) params.get("userId");
            GoodsVo goodsVo = (GoodsVo) params.get("goodsVo");
            String transactionId = (String) params.get("transactionId");
            
            // Execute local transaction - create order in database with transaction ID
            boolean success = createOrderInDB(userId, goodsVo, transactionId);
            
            // Record transaction result
            RocketMQLocalTransactionState state = success ? 
                    RocketMQLocalTransactionState.COMMIT : 
                    RocketMQLocalTransactionState.ROLLBACK;
            
            localTransactionMap.put(transactionId, state);
            
            log.info("Local transaction executed with result: {}, txId: {}", state, transactionId);
            return state;
            
        } catch (Exception e) {
            log.error("Error executing local transaction", e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    /**
     * Check local transaction status when MQ server asks for transaction status
     * This is called by the RocketMQ broker to determine the final state of a transaction
     * when it hasn't received a definitive commit or rollback
     */
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        try {
            // Extract transaction id from message headers
            String transactionId = (String) msg.getHeaders().get("transactionId");
            log.info("Checking transaction status for txId: {}", transactionId);
            
            // Get cached transaction state (for performance)
            RocketMQLocalTransactionState state = localTransactionMap.get(transactionId);
            
            if (state != null) {
                log.info("Found transaction state in cache: {}", state);
                return state;
            }
            
            // If not found in memory (e.g., after service restart), query database
            log.info("Transaction state not in cache, checking database...");
            
            // 1. Check if order exists with this transaction ID
            SeckillOrder order = orderService.getOrderByTransactionId(transactionId);
            
            if (order != null) {
                // Order exists, transaction was successful
                log.info("Transaction found in database, order exists: {}", order.getId());
                localTransactionMap.put(transactionId, RocketMQLocalTransactionState.COMMIT);
                return RocketMQLocalTransactionState.COMMIT;
            }
            
            // 2. Check if transaction is still within valid time window
            if (NEED_CHECK_TIMEOUT && isTransactionExpired(transactionId)) {
                log.info("Transaction considered failed: timeout reached");
                localTransactionMap.put(transactionId, RocketMQLocalTransactionState.ROLLBACK);
                return RocketMQLocalTransactionState.ROLLBACK;
            }
            
            // 3. Still within processing window, return UNKNOWN to trigger retry
            log.info("Transaction status still unknown, will retry check later");
            return RocketMQLocalTransactionState.UNKNOWN;
            
        } catch (Exception e) {
            log.error("Error checking local transaction status", e);
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }
    
    /**
     * Create order in database (actual local transaction)
     */
    private boolean createOrderInDB(Long userId, GoodsVo goodsVo, String transactionId) {
        try {
            // Create order with transaction ID
            SeckillOrder order = orderService.createOrderWithTransactionId(userId, goodsVo, transactionId);
            return order != null;
        } catch (Exception e) {
            log.error("Error creating order in database", e);
            return false;
        }
    }
    
    /**
     * Check if transaction has expired based on its start time
     */
    private boolean isTransactionExpired(String transactionId) {
        // Get transaction start time from Redis
        Long startTime = redisService.get(SeckillKey.txStartTime, transactionId);
        if (startTime == null) {
            // If no record exists, assume it's an old transaction
            log.warn("Transaction start time not found for txId: {}", transactionId);
            return true;
        }
        
        return false;
    }
}
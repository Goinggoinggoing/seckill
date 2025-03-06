package com.example.seckill.mq;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import com.example.seckill.redis.SeckillKey;
import com.example.seckill.service.RedisService;
import com.example.seckill.vo.GoodsVo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class MQProducer {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisService redisService;
    
    private static final String TOPIC_TEST = "topic-test";
    private static final String TOPIC_STOCK_REDUCTION = "topic-stock-reduction";

    /**
     * Send a simple message
     */
    public void sendMessage(String message) {
        rocketMQTemplate.convertAndSend(TOPIC_TEST, message);
        System.out.println("Message sent: " + message);
    }
    
    /**
     * Send object message
     */
    public void sendObjectMessage(Object obj) {
        rocketMQTemplate.convertAndSend(TOPIC_TEST, obj);
        System.out.println("Object message sent: " + obj);
    }

    /**
     * Send delayed message
     */
    public void sendDelayedMessage(String message, int delayLevel) {
        rocketMQTemplate.syncSend(TOPIC_TEST, 
            MessageBuilder.withPayload(message).build(),
            2000, 
            delayLevel);
        System.out.println("Delayed message sent: " + message + " with delay level: " + delayLevel);
    }
    
    /**
     * Send transaction message for stock reduction
     * This ensures the inventory is reduced only if the order is successfully created
     */
    public String sendStockReductionTransactionMessage(Long userId, GoodsVo goodsVo) throws JsonProcessingException {
        String transactionId = UUID.randomUUID().toString();
        
        // Message = what downstream services need
        // Args = what local transaction needs

        // Prepare stock reduction message data
        Map<String, Object> payload = new HashMap<>();
        payload.put("goodsId", goodsVo.getId());
        payload.put("transactionId", transactionId);

        
        // Create the message with transaction ID in headers
        Message<String> message = MessageBuilder.withPayload(objectMapper.writeValueAsString(payload))
                .setHeader("transactionId", transactionId)
                .build();
        
        // Prepare transaction arguments to be used in the local transaction execution
        Map<String, Object> transactionArgs = new HashMap<>();
        transactionArgs.put("userId", userId);
        transactionArgs.put("goodsVo", goodsVo);
        transactionArgs.put("transactionId", transactionId);
        

        log.info("begin Transaction message sent for order creation with txId: {}", transactionId);

        // Store transaction start time in Redis with expiration
        redisService.set(SeckillKey.txStartTime, transactionId, System.currentTimeMillis());

        // Send transactional message
        rocketMQTemplate.sendMessageInTransaction(
                TOPIC_STOCK_REDUCTION, 
                message, 
                transactionArgs);
        
        log.info("Transaction message sent for order creation with txId: {}", transactionId);

        return transactionId;
    }
}
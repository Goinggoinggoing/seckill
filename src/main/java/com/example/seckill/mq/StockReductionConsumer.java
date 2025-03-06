package com.example.seckill.mq;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.seckill.dao.IdempotenceRecordDao;
import com.example.seckill.entity.IdempotenceRecord;
import com.example.seckill.service.GoodsService;
import com.example.seckill.service.RedisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Map;

@Slf4j
@Component
@RocketMQMessageListener(
    topic = "topic-stock-reduction",
    consumerGroup = "stock-reduction-consumer-group"
)
public class StockReductionConsumer implements RocketMQListener<String> {

    private static final Integer MESSAGE_RECORD_EXPIRE_TIME = 3 * 24 * 60 * 60;
    
    @Autowired
    private GoodsService goodsService;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisService redisService;

    @Autowired
    private IdempotenceRecordDao idempotenceRecordDao;

    @Override
    public void onMessage(String message) {
        try {
            log.info("Received stock reduction message: {}", message);
            
            // Parse the message
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(message, Map.class);
            Long goodsId = Long.valueOf(payload.get("goodsId").toString());
            String transactionId = payload.get("transactionId").toString();
            
            // boolean success = reduceStockAtLeastOnce(goodsId);

            boolean success = processStockReductionWithIdempotence(goodsId, transactionId);

            if (!success) {
                log.warn("Failed to reduce stock in database for goods: {}", goodsId);
            }
            log.info("Inventory updated successfully for goods: {}", goodsId);
            
            
        } catch (JsonProcessingException e) {
            log.error("Error parsing stock reduction message", e);
        } catch (Exception e) {
            log.error("Error processing stock reduction", e);
            throw e; // Throw exception to let MQ retry
        }
    }

    public boolean reduceStockAtLeastOnce(Long goodsId){
        // The message ack might be lost, leading to repeated deductions
        return goodsService.reduceStockWhenLeft(goodsId);
    }

    public boolean reduceStockAtMostOnce(Long goodsId, String transactionId){
        String idempotentKey = "stock:reduction:" + transactionId;
        
        // Use Redis SETNX to implement idempotence check. If SETNX fails due to a crash,
        // there will be no chance to deduct the stock again, so it is at most once deduction.
        boolean isFirstProcess = redisService.setIfNotExists(idempotentKey, "PROCESSED", MESSAGE_RECORD_EXPIRE_TIME);
        
        // If already processed, return directly
        if (!isFirstProcess) {
            log.info("Message already processed, transactionId: {}", transactionId);
            return false;
        }
        
        // Execute stock reduction logic
        return goodsService.reduceStockWhenLeft(goodsId);
    }

    public boolean processStockReductionWithIdempotence(Long goodsId, String transactionId){
        // 1. Check if the message has been processed
        IdempotenceRecord existingRecord = idempotenceRecordDao.findByTransactionId(transactionId);

        // 2. If the message was already processed, return the processing result
        if (existingRecord != null) {
            log.info("Message already processed, transactionId: {}", transactionId);
            return existingRecord.getProcessed() != null && existingRecord.getProcessed();
        }
        // 3. Execute stock reduction and record the message in the same transaction
        try {
            // 3. Execute stock reduction and record the message in the same transaction
            return processWithTransaction(goodsId, transactionId);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Catch the unique index violation exception, indicating concurrent processing record already exists
            log.info("Duplicate processing detected, transactionId: {}", transactionId);
            // Since the transaction is rolled back, the stock reduction is also rolled back,
            // re-query the record to get the correct result.
            IdempotenceRecord record = idempotenceRecordDao.findByTransactionId(transactionId);
            return record != null && record.getProcessed() != null && record.getProcessed();
        }
    }

    @Transactional
    private boolean processWithTransaction(Long goodsId, String transactionId) {
        // 1. Execute business logic - reduce inventory
        boolean success = goodsService.reduceStockWhenLeft(goodsId);
        
        // 2. Record the processing result regardless of success or failure.
        // The database unique index ensures that when multiple calls enter this function simultaneously, only one will succeed and others will roll back.
        // alse you can try to use distributedLock without unique index
        IdempotenceRecord record = new IdempotenceRecord();
        record.setTransactionId(transactionId);
        record.setProcessed(success);
        record.setCreateTime(new Date());
        idempotenceRecordDao.insertRecord(record); 
        
        return success;
    }
}
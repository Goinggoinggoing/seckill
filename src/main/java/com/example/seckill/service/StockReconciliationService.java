package com.example.seckill.service;

import com.example.seckill.redis.SeckillKey;
import com.example.seckill.service.GoodsService;
import com.example.seckill.service.RedisService;
import com.example.seckill.vo.GoodsVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class StockReconciliationService {

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private RedisService redisService;
    
    // Configuration parameters
    private static final int ALERT_THRESHOLD = 3; // Alert after this many consecutive retries
    private static final int LOW_STOCK_THRESHOLD_PERCENTAGE = 30; // Consider low stock when below 30%
    private static final boolean RECONCILE_ALL_ITEMS = true; // Whether to reconcile all products
    private static final long RETRY_DELAY_MS = 1000; // 1 second delay between retries

    private static final String GET_STOCK_VALUES_SCRIPT = 
        "local stockValue = redis.call('get', KEYS[1]) " +
        "local reservedValue = redis.call('get', KEYS[2]) " +
        "local result = {} " +
        "if stockValue == false then result[1] = '' else result[1] = stockValue end " +
        "if reservedValue == false then result[2] = '' else result[2] = reservedValue end " +
        "return result";

    private static final DefaultRedisScript<List> GET_STOCK_VALUES_SCRIPT_OBJ = 
        new DefaultRedisScript<>(GET_STOCK_VALUES_SCRIPT, List.class);

    /**
     * Scheduled reconciliation task, runs every 5 minutes
     */
    @Scheduled(fixedRate = 1 * 30 * 1000) // Run every 5 minutes
    public void scheduledReconciliation() {
        log.info("Starting scheduled stock reconciliation");
        List<GoodsVo> goodsList = goodsService.listGoodsVo();
        
        if (goodsList == null || goodsList.isEmpty()) {
            log.warn("No goods found for reconciliation");
            return;
        }
        
        for (GoodsVo goods : goodsList) {
            // Determine if reconciliation is needed based on watermark
            if (shouldReconcile(goods)) {
                reconcileStock(goods.getId());
            }
        }
    }

    /**
     * Determine if reconciliation is needed
     * 1. If configured to reconcile all products, then reconcile all
     * 2. Otherwise, only reconcile products with stock below the watermark
     */
    private boolean shouldReconcile(GoodsVo goods) {
        if (RECONCILE_ALL_ITEMS) {
            return true;
        }
        
        // Check stock in Redis
        Integer redisStock = redisService.get(SeckillKey.goodsStock, "" + goods.getId());
        
        // If the product stock doesn't exist in Redis, return false
        if (redisStock == null) {
            return false;
        }
        
        // Calculate current stock percentage
        int initialStock = goods.getTotalStock();
        if (initialStock <= 0) {
            return true; // If initial stock is abnormal, perform reconciliation
        }
        
        int stockPercentage = (redisStock * 100) / initialStock;
        return stockPercentage <= LOW_STOCK_THRESHOLD_PERCENTAGE;
    }

    /**
     * Perform stock reconciliation with retry logic
     */
    public void reconcileStock(Long goodsId) {
        log.debug("Reconciling stock for goods ID: {}", goodsId);
        
        int retryCount = 0;
        boolean isConsistent = false;
        
        // Retry up to ALERT_THRESHOLD times
        while (!isConsistent && retryCount < ALERT_THRESHOLD) {
            try {
                // Get current stock from DB
                GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
                if (goods == null) {
                    log.error("Failed to get goods info for ID: {} during reconciliation", goodsId);
                    return;
                }
                
                Integer dbStock = goods.getStockCount();

                String stockKey = redisService.getRealKey(SeckillKey.goodsStock, "" + goodsId);
                String reservedKey = redisService.getRealKey(SeckillKey.reservedStock, "" + goodsId);
                // Get stock and reserved stock from Redis
                // Integer redisStock = redisService.get(SeckillKey.goodsStock, "" + goodsId);
                // Integer reservedStock = redisService.get(SeckillKey.reservedStock, "" + goodsId);

                // Get stock and reserved stock from Redis concurently using MGET
                // List<Integer> values = redisService.mget(stockKey, reservedKey);
                // Integer redisStock = values.get(0);
                // Integer reservedStock = values.get(1) != null ? values.get(1) : 0;

                // Get stock and reserved stock from Redis atomically using lua
                List<Object> values = redisService.executeScript(
                    GET_STOCK_VALUES_SCRIPT_OBJ, 
                    Arrays.asList(stockKey, reservedKey)
                );

                Integer redisStock = values.get(0) != null && !values.get(0).toString().isEmpty() ? Integer.parseInt(values.get(0).toString()) : null;
                Integer reservedStock = values.get(1) != null  && !values.get(1).toString().isEmpty() ? Integer.parseInt(values.get(1).toString()) : 0;
                
                // If stock information doesn't exist in Redis, log and skip
                if (redisStock == null) {
                    log.warn("Redis stock not found for goods: {}", goodsId);
                    return;
                }
                
                // Reserved stock might not exist, default to 0
                reservedStock = reservedStock == null ? 0 : reservedStock;
                
                // Calculate theoretical consistency: Redis stock + Reserved stock = DB stock
                isConsistent = (redisStock + reservedStock == dbStock);
                
                if (!isConsistent) {
                    retryCount++;
                    log.warn("Stock inconsistency detected for goods {}: Redis({}) + Reserved({}) = All({}) != DB({}), retry: {}/{}", 
                            goodsId, redisStock, reservedStock, redisStock + reservedStock, dbStock, retryCount, ALERT_THRESHOLD);
                    
                    if (retryCount >= ALERT_THRESHOLD) {
                        // Send alert when threshold exceeded
                        sendAlert(goodsId, redisStock, reservedStock, dbStock);
                        
                        // dont do this
                        // Auto-correction strategy - use DB as source of truth
                        // if (dbStock >= 0) {
                        //     log.info("Auto-correcting Redis stock for goods {}: Setting to {}", goodsId, dbStock - reservedStock);
                        //     redisService.set(SeckillKey.goodsStock, "" + goodsId, dbStock - reservedStock);
                        // }
                    } else {
                        // Wait before retrying
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.error("Retry delay interrupted", e);
                        }
                    }
                } else {
                    log.info("Stock reconciliation successful for goods {}: Redis({}) + Reserved({}) = DB({})", 
                            goodsId, redisStock, reservedStock, dbStock);
                }
            } catch (Exception e) {
                retryCount++;
                log.error("Error during stock reconciliation for goods: {}, retry: {}/{}", 
                         goodsId, retryCount, ALERT_THRESHOLD, e);
                
                if (retryCount < ALERT_THRESHOLD) {
                    // Wait before retrying
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Retry delay interrupted", ie);
                    }
                }
            }
        }
    }

    /**
     * Send alert
     */
    private void sendAlert(Long goodsId, Integer redisStock, Integer reservedStock, Integer dbStock) {
        // Integration with various alert systems (email, SMS, enterprise WeChat, etc.)
        log.error("ALERT: Persistent stock inconsistency for goods {}: Redis({}) + Reserved({}) != DB({})", 
                goodsId, redisStock, reservedStock, dbStock);
        
        // In production, add alerting code such as sending emails or messages
        // alertService.send(...);
    }

    /**
     * Manually trigger reconciliation for a specific product
     */
    public void manualReconcile(Long goodsId) {
        GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
        if (goods != null) {
            reconcileStock(goodsId);
        } else {
            log.error("Cannot reconcile - goods not found: {}", goodsId);
        }
    }
}
package com.example.seckill.service;

import com.example.seckill.redis.SeckillKey;
import com.example.seckill.vo.GoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SeckillInitService implements CommandLineRunner {

    @Autowired
    private GoodsService goodsService;
    
    @Autowired
    private RedisService redisService;

    @Override
    public void run(String... args) throws Exception {
        // Load all seckill goods and initialize Redis stock
        List<GoodsVo> goodsList = goodsService.listGoodsVo();
        if (goodsList == null) {
            return;
        }
        
        // Clear previous data
        for (GoodsVo goods : goodsList) {
            Long goodsId = goods.getId();
            redisService.delete(SeckillKey.isGoodsOver, "" + goodsId);
            redisService.delete(SeckillKey.reservedStock, "" + goodsId);
        }
        
        // Initialize stock in Redis
        for (GoodsVo goods : goodsList) {
            redisService.set(SeckillKey.goodsStock, "" + goods.getId(), goods.getStockCount());
        }
        
        System.out.println("Seckill goods stock initialized in Redis");
    }
}
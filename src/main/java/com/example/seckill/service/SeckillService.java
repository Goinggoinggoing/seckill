package com.example.seckill.service;

import com.example.seckill.entity.SeckillOrder;
import com.example.seckill.vo.GoodsVo;

public interface SeckillService {
    /**
     * 执行秒杀操作
     */
    SeckillOrder seckill(Long userId, GoodsVo goodsVo);
    
    /**
     * 检查秒杀状态
     * @return orderId: 成功, -1: 秒杀失败, 0: 排队中
     */
    Long getSeckillResult(Long userId, Long goodsId);
}

package com.example.seckill.service.impl;

import com.example.seckill.entity.SeckillOrder;
import com.example.seckill.service.GoodsService;
import com.example.seckill.service.OrderService;
import com.example.seckill.service.SeckillService;
import com.example.seckill.vo.GoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeckillServiceImpl implements SeckillService {

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private OrderService orderService;

    @Override
    @Transactional
    public SeckillOrder seckill(Long userId, GoodsVo goodsVo) {
//        // v0 错误示范 未考虑并发
//        boolean success = goodsService.reduceStockIncorrect(goodsVo.getSeckillGoodsId());
//
//        // v1 悲观锁实现
//        boolean success = goodsService.reduceStockByPessimisticLock(goodsVo.getSeckillGoodsId());
//
//        // v2 版本号实现
//        boolean success = goodsService.reduceStockByVersion(goodsVo.getSeckillGoodsId());

        // v3. 减少库存 - 最优方法
        boolean success = goodsService.reduceStockWhenLeft(goodsVo.getSeckillGoodsId());
        if (success) {
            // 2. 创建订单
            return orderService.createOrder(userId, goodsVo);
        }
        // 库存减少失败，秒杀失败
        return null;
    }


    @Override
    public Long getSeckillResult(Long userId, Long goodsId) {
        SeckillOrder order = orderService.getOrderByUserIdGoodsId(userId, goodsId);
        if (order != null) {
            // 秒杀成功
            return order.getId();
        } else {
            // 判断商品是否已售罄
            GoodsVo goodsVo = goodsService.getGoodsVoByGoodsId(goodsId);
            if (goodsVo.getStockCount() <= 0) {
                // 已售罄
                return -1L;
            } else {
                // 正在排队中
                return 0L;
            }
        }
    }
}
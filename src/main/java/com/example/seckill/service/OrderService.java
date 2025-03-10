package com.example.seckill.service;

import com.example.seckill.entity.SeckillOrder;
import com.example.seckill.vo.GoodsVo;
import com.example.seckill.vo.OrderVo;
import java.util.List;

public interface OrderService {
    
    /**
     * 创建订单
     */
    SeckillOrder createOrder(Long userId, GoodsVo goodsVo);
    
    /**
     * 根据用户ID和商品ID获取订单
     */
    SeckillOrder getOrderByUserIdGoodsId(Long userId, Long goodsId);
    
    /**
     * 根据订单号获取订单详情
     */
    OrderVo getOrderByOrderNo(String orderNo);
    
    /**
     * 获取用户所有订单
     */
    List<OrderVo> getOrdersByUserId(Long userId);

    Integer countOrdersByGoodsId(long goodsId);

    SeckillOrder getOrderByTransactionId(String transactionId);
    SeckillOrder createOrderWithTransactionId(Long userId, GoodsVo goodsVo, String transactionId);

    boolean cancelOrder(String transactionId);
}
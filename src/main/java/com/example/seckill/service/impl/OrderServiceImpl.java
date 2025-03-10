package com.example.seckill.service.impl;

import com.example.seckill.dao.OrderDao;
import com.example.seckill.entity.SeckillOrder;
import com.example.seckill.service.OrderService;
import com.example.seckill.util.UUIDUtil;
import com.example.seckill.vo.GoodsVo;
import com.example.seckill.vo.OrderVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Date;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {
    
    @Autowired
    private OrderDao orderDao;
    
    @Override
    public SeckillOrder createOrder(Long userId, GoodsVo goodsVo) {
        SeckillOrder order = new SeckillOrder();
        order.setUserId(userId);
        order.setGoodsId(goodsVo.getId());
        order.setSeckillGoodsId(goodsVo.getSeckillGoodsId());
        order.setOrderNo(UUIDUtil.generateOrderNo());
        order.setStatus(0); // 0: 新建未支付
        order.setCreateTime(new Date());
        order.setPayAmount(goodsVo.getSeckillPrice());
        
        // 插入订单
        orderDao.insertOrder(order);
        return order;
    }
    
    @Override
    public SeckillOrder getOrderByUserIdGoodsId(Long userId, Long goodsId) {
        return orderDao.getOrderByUserIdGoodsId(userId, goodsId);
    }
    
    @Override
    public OrderVo getOrderByOrderNo(String orderNo) {
        return orderDao.getOrderByOrderNo(orderNo);
    }
    
    @Override
    public List<OrderVo> getOrdersByUserId(Long userId) {
        return orderDao.getOrdersByUserId(userId);
    }

    @Override
    public Integer countOrdersByGoodsId(long goodsId) {
        return orderDao.countOrdersByGoodsId(goodsId);
    }

    @Override
    public SeckillOrder createOrderWithTransactionId(Long userId, GoodsVo goodsVo, String transactionId) {
        SeckillOrder order = new SeckillOrder();
        order.setUserId(userId);
        order.setGoodsId(goodsVo.getId());
        order.setSeckillGoodsId(goodsVo.getSeckillGoodsId());
        order.setOrderNo(UUIDUtil.generateOrderNo());
        order.setStatus(0); // 0: 新建未支付
        order.setCreateTime(new Date());
        order.setPayAmount(goodsVo.getSeckillPrice());
        order.setTransactionId(transactionId);
        
        // 插入订单
        orderDao.insertOrder(order);
        return order;
    }

    @Override
    public SeckillOrder getOrderByTransactionId(String transactionId) {
        return orderDao.getOrderByTransactionId(transactionId);
    }

    @Override
    public boolean cancelOrder(String transactionId) {
        return orderDao.cancelOrder(transactionId) > 0;
    }
}
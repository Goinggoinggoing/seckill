package com.example.seckill.dao;

import com.example.seckill.entity.SeckillOrder;
import com.example.seckill.vo.OrderVo;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OrderDao {
    
    /**
     * 根据用户ID和商品ID获取订单
     */
    SeckillOrder getOrderByUserIdGoodsId(@Param("userId") Long userId, @Param("goodsId") Long goodsId);
    
    /**
     * 插入订单
     */
    int insertOrder(SeckillOrder order);
    
    /**
     * 根据订单号获取订单
     */
    OrderVo getOrderByOrderNo(@Param("orderNo") String orderNo);
    
    /**
     * 获取用户所有订单
     */
    List<OrderVo> getOrdersByUserId(@Param("userId") Long userId);

    Integer countOrdersByGoodsId(long goodsId);

    SeckillOrder getOrderByTransactionId(String transactionId);

    int cancelOrder(String transactionId);
}
package com.example.seckill.controller;

import com.example.seckill.service.OrderService;
import com.example.seckill.vo.OrderVo;
import com.example.seckill.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 根据订单号获取订单详情
     */
    @GetMapping("/detail/{orderNo}")
    public Result<OrderVo> getOrderDetail(@PathVariable("orderNo") String orderNo) {
        OrderVo order = orderService.getOrderByOrderNo(orderNo);
        if (order == null) {
            return Result.error(404, "订单不存在");
        }
        return Result.success(order);
    }

    /**
     * 获取用户所有订单
     */
    @GetMapping("/list/{userId}")
    public Result<List<OrderVo>> getOrderList(@PathVariable("userId") Long userId) {
        List<OrderVo> orders = orderService.getOrdersByUserId(userId);
        return Result.success(orders);
    }
}
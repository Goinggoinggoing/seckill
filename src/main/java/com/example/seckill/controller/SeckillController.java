package com.example.seckill.controller;

import com.example.seckill.annotation.RateLimit;
import com.example.seckill.entity.SeckillOrder;
import com.example.seckill.service.GoodsService;
import com.example.seckill.service.OrderService;
import com.example.seckill.service.SeckillService;
import com.example.seckill.vo.GoodsVo;
import com.example.seckill.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;
@RestController
@RequestMapping("/seckill")
public class SeckillController {

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private OrderService orderService;

    @Autowired
    @Resource(name = "seckillServiceImplv3")
    private SeckillService seckillService;

    /**
     * 执行秒杀
     */
    @PostMapping("/{userId}/{goodsId}")
    @RateLimit(
        key = "seckill", 
        type = RateLimit.RateLimitType.IP, 
        rate = 0.2, 
        capacity = 1, 
        tokens = 1, 
        message = "操作频率超限，请稍后再试"
    )
    public Result<String> seckill(
            @PathVariable("userId") Long userId,
            @PathVariable("goodsId") Long goodsId) {

        // 1. 判断用户是否存在（实际项目中应该在登录时验证）
        if (userId <= 0) {
            return Result.error(400, "用户不存在");
        }

        // 2. 判断商品是否存在
        GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
        if (goods == null) {
            return Result.error(400, "商品不存在");
        }

        // 3. 判断秒杀是否开始或已结束
        Date now = new Date();
        if (now.before(goods.getStartTime())) {
            return Result.error(400, "秒杀尚未开始");
        }
        if (now.after(goods.getEndTime())) {
            return Result.error(400, "秒杀已结束");
        }

        // 4. 执行秒杀
        SeckillOrder seckillOrder = seckillService.seckill(userId, goods);
        if (seckillOrder == null) {
            return Result.error(500, "秒杀失败，商品已售罄");
        }

        return Result.success("秒杀成功，订单号：" + seckillOrder.getTransactionId());
    }

    /**
     * 获取秒杀结果
     */
    @RateLimit(
        key = "result", 
        type = RateLimit.RateLimitType.USER, 
        rate = 1.5, 
        capacity = 3, 
        tokens = 1,
        message = "查询太频繁，请稍后再试"
    )
    @GetMapping("/result/{userId}/{goodsId}")
    public Result<Map<String, Object>> getSeckillResult(
            @PathVariable("userId") Long userId,
            @PathVariable("goodsId") Long goodsId) {

        // 获取秒杀结果
        Long result = seckillService.getSeckillResult(userId, goodsId);
        Map<String, Object> data = new HashMap<>();

        if (result > 0) {
            // 秒杀成功，返回订单信息
            SeckillOrder order = orderService.getOrderByUserIdGoodsId(userId, goodsId);
            data.put("status", 1);
            data.put("orderNo", order.getOrderNo());
        } else if (result == 0) {
            // 排队中
            data.put("status", 0);
        } else {
            // 秒杀失败
            data.put("status", -1);
        }

        return Result.success(data);
    }
}

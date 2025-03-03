package com.example.seckill.controller;

import com.example.seckill.service.GoodsService;
import com.example.seckill.vo.GoodsVo;
import com.example.seckill.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/goods")
public class GoodsController {
    
    @Autowired
    private GoodsService goodsService;
    
    /**
     * 商品列表
     */
    @GetMapping("/list")
    public Result<List<GoodsVo>> list() {
        List<GoodsVo> goodsList = goodsService.listGoodsVo();
        
        // 计算秒杀状态和倒计时
        Date now = new Date();
        for (GoodsVo goods : goodsList) {
            calculateSeckillStatus(goods, now);
        }
        
        return Result.success(goodsList);
    }
    
    /**
     * 商品详情
     */
    @GetMapping("/detail/{goodsId}")
    public Result<GoodsVo> detail(@PathVariable("goodsId") Long goodsId) {
        GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
        if (goods == null) {
            return Result.error(404, "商品不存在");
        }
        
        // 计算秒杀状态和倒计时
        calculateSeckillStatus(goods, new Date());
        
        return Result.success(goods);
    }
    
    /**
     * 计算秒杀状态和倒计时
     */
    private void calculateSeckillStatus(GoodsVo goods, Date now) {
        // 秒杀状态
        int remainSeconds;
        
        // 秒杀还未开始
        if (now.before(goods.getStartTime())) {
            remainSeconds = (int) ((goods.getStartTime().getTime() - now.getTime()) / 1000);
        }
        // 秒杀已结束
        else if (now.after(goods.getEndTime())) {
            remainSeconds = -1;
        }
        // 秒杀进行中
        else {
            remainSeconds = 0;
        }
        
        goods.setRemainSeconds(remainSeconds);
    }
}
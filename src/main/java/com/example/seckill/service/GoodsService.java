package com.example.seckill.service;

import com.example.seckill.vo.GoodsVo;
import java.util.List;

public interface GoodsService {
    
    /**
     * 获取商品列表
     */
    List<GoodsVo> listGoodsVo();
    
    /**
     * 根据商品ID获取商品详情
     */
    GoodsVo getGoodsVoByGoodsId(Long goodsId);


    // 错误示范：未加锁查询库存
    boolean reduceStockIncorrect(Long seckillGoodsId);


    // 悲观锁实现
    boolean reduceStockByPessimisticLock(Long seckillGoodsId);

    // 乐观锁：使用版本号进行并发控制
    boolean reduceStockByVersion(Long seckillGoodsId);


    /**
     * 减少库存（悲观锁）
     */
    boolean reduceStockWhenLeft(Long seckillGoodsId);
}
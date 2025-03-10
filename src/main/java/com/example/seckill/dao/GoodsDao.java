package com.example.seckill.dao;

import com.example.seckill.entity.SeckillGoods;
import com.example.seckill.vo.GoodsVo;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface GoodsDao {
    
    /**
     * 获取商品列表
     */
    List<GoodsVo> listGoodsVo();
    
    /**
     * 根据商品ID获取商品详情
     */
    GoodsVo getGoodsVoByGoodsId(@Param("goodsId") Long goodsId);

    SeckillGoods getStock(@Param("seckillGoodsId") Long seckillGoodsId);

    int updateStock(@Param("seckillGoodsId") Long seckillGoodsId);

    // 排他锁获取库存
    SeckillGoods getSeckillGoodsForUpdate(Long seckillGoodsId);

    /**
     * 减少库存，使用乐观锁
     */
    int reduceStockByVersion(@Param("seckillGoodsId") Long seckillGoodsId, @Param("version") Integer version);


    /**
     * 减少库存，并且在有库存条件下
     */
    int reduceStockWhenLeft(@Param("seckillGoodsId") Long seckillGoodsId);

    /**
     * 库存回退
     */
    int rollbackStock(@Param("seckillGoodsId") Long seckillGoodsId);
    


}
package com.example.seckill.service.impl;

import com.example.seckill.dao.GoodsDao;
import com.example.seckill.entity.SeckillGoods;
import com.example.seckill.service.GoodsService;
import com.example.seckill.vo.GoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class GoodsServiceImpl implements GoodsService {
    
    @Autowired
    private GoodsDao goodsDao;
    
    @Override
    public List<GoodsVo> listGoodsVo() {
        return goodsDao.listGoodsVo();
    }
    
    @Override
    public GoodsVo getGoodsVoByGoodsId(Long goodsId) {
        return goodsDao.getGoodsVoByGoodsId(goodsId);
    }

    @Override
    public boolean reduceStockIncorrect(Long seckillGoodsId) {
        // 1. 查询库存（未加锁）
        SeckillGoods goods = goodsDao.getStock(seckillGoodsId);
        Integer stock = goods.getStockCount();
        if (stock > 0) {
            // 2. 扣减库存
            int affectedRows = goodsDao.updateStock(seckillGoodsId);
            return affectedRows > 0;
        }
        return false;
    }

    @Override
    public boolean reduceStockByPessimisticLock(Long seckillGoodsId) {
        // 1. 查询时加锁（FOR UPDATE）
        SeckillGoods goods = goodsDao.getSeckillGoodsForUpdate(seckillGoodsId);
        if (goods != null && goods.getStockCount() > 0) {
            // 2. 扣减库存
            int affectedRows = goodsDao.updateStock(seckillGoodsId);
            return affectedRows > 0;
        }
        return false;
    }

    @Override
    public boolean reduceStockByVersion(Long seckillGoodsId) {
        // 1. 获取当前版本号 以及库存
        SeckillGoods goods = goodsDao.getStock(seckillGoodsId);
        Integer stock = goods.getStockCount();
        Integer version = goods.getVersion();

        if (version == null || stock < 0) {
            return false;
        }
        // 2. 扣减库存（CAS 更新）
        int affectedRows = goodsDao.reduceStockByVersion(seckillGoodsId, version);
        return affectedRows > 0;
    }

    @Override
    public boolean reduceStockWhenLeft(Long seckillGoodsId) {
        // 使用悲观锁减库存，返回影响的行数
        int affectedRows = goodsDao.reduceStockWhenLeft(seckillGoodsId);
        return affectedRows > 0;
    }

}
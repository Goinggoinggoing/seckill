package com.example.seckill.vo;

import com.example.seckill.entity.Goods;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class GoodsVo extends Goods {
    private BigDecimal seckillPrice;
    private Integer stockCount;
    private Integer totalStock;
    private Date startTime;
    private Date endTime;
    private Long seckillGoodsId;
    private Integer remainSeconds;
}
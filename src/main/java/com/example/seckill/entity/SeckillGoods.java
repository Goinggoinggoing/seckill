package com.example.seckill.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class SeckillGoods {
    private Long id;
    private Long goodsId;
    private BigDecimal seckillPrice;
    private Integer stockCount;
    private Integer totalStock;
    private Date startTime;
    private Date endTime;
    private Integer version;
}
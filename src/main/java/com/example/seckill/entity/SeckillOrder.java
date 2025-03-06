package com.example.seckill.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class SeckillOrder {
    private Long id;
    private Long userId;
    private Long goodsId;
    private Long seckillGoodsId;
    private String orderNo;
    private Integer status;
    private Date createTime;
    private Date payTime;
    private BigDecimal payAmount;
    private String transactionId;
}
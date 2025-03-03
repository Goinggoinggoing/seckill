package com.example.seckill.vo;

import com.example.seckill.entity.SeckillOrder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class OrderVo extends SeckillOrder {
    private String goodsName;
    private String goodsImg;
    private BigDecimal goodsPrice;
}
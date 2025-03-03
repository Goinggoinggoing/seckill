package com.example.seckill.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class Goods {
    private Long id;
    private String goodsName;
    private String goodsTitle;
    private String goodsImg;
    private String goodsDetail;
    private BigDecimal goodsPrice;
    private Integer goodsStock;
    private Date createTime;
    private Date updateTime;
}
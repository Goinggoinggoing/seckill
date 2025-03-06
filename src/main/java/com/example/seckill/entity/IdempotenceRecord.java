package com.example.seckill.entity;

import lombok.Data;
import java.util.Date;

@Data
public class IdempotenceRecord {
    private Long id;
    private String transactionId;  // 事务/消息的唯一标识
    private Boolean processed;     // 是否处理成功
    private Date createTime;       // 创建时间
}
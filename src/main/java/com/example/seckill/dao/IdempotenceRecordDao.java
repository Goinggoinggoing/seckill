package com.example.seckill.dao;

import com.example.seckill.entity.IdempotenceRecord;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotenceRecordDao {
    
    /**
     * 创建幂等记录
     */
    int insertRecord(IdempotenceRecord record);
    
    /**
     * 根据事务ID查询幂等记录
     */
    IdempotenceRecord findByTransactionId(@Param("transactionId") String transactionId);
}
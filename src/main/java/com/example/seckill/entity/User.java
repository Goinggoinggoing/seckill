package com.example.seckill.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class User {
    private Long id;
    private String username;
    private String password;
    private String salt;
    private String phone;
    private String email;
    private String head;
    private Date registerDate;
    private Date lastLoginDate;
    private Integer loginCount;
}
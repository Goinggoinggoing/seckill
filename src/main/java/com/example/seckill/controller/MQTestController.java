package com.example.seckill.controller;

import com.example.seckill.entity.Goods;
import com.example.seckill.mq.MQProducer;
import com.example.seckill.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mq")
public class MQTestController {

    @Autowired
    private MQProducer mqProducer;
    
    @GetMapping("/test")
    public Result<String> testMQ(@RequestParam(defaultValue = "Hello RocketMQ!") String message) {
        try {
            mqProducer.sendMessage(message);
            return Result.success("Message sent successfully: " + message);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(500, "Failed to send message: " + e.getMessage());
        }
    }

    @GetMapping("/testObj")
    public Result<String> testMQObj(@RequestParam(defaultValue = "Hello RocketMQ!") String message) {
        try {
            Goods g = new Goods();
            g.setGoodsName(message);
            mqProducer.sendObjectMessage(g);
            return Result.success("Message sent successfully: " + message);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(500, "Failed to send message: " + e.getMessage());
        }
    }
    
    @GetMapping("/test-delay")
    public Result<String> testDelayedMQ(
            @RequestParam(defaultValue = "Delayed Hello RocketMQ!") String message,
            @RequestParam(defaultValue = "1") int delayLevel) {
        try {
            mqProducer.sendDelayedMessage(message, delayLevel);
            return Result.success("Delayed message sent successfully: " + message);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(500, "Failed to send delayed message: " + e.getMessage());
        }
    }
}
package com.example.seckill.mq;

import java.util.concurrent.TimeUnit;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import com.example.seckill.entity.Goods;

@Component
@RocketMQMessageListener(
    topic = "topic-test",
    consumerGroup = "consumer-group-test",
    consumeThreadNumber = 5,
    maxReconsumeTimes = 5
)
@Slf4j
public class MQConsumerTest implements RocketMQListener<String> {

    private final ObjectMapper objectMapper = new ObjectMapper();


    @Override
    public void onMessage(String message) {
        log.info("Received message: {}", message);

        // try {
        //     TimeUnit.MILLISECONDS.sleep(10000);
        // } catch (InterruptedException e) {
        //     Thread.currentThread().interrupt();
        // }

        // log.info("finish message: {}", message);

        try {
            Goods goods = objectMapper.readValue(message, Goods.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse message: {}", message, e);
        }
        
        // Process the message here
    }
}
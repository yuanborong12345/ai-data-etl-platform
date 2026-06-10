package com.yuan.user.service.impl;

import com.yuan.constant.MqConstant;
import com.yuan.model.dto.processor.FileProcessMessage;
import com.yuan.user.service.RabbitMqService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RabbitMqServiceImpl implements RabbitMqService {

    private final AmqpTemplate amqpTemplate;

    @Override
    public void sendFileProcessMessage(FileProcessMessage message) {
        amqpTemplate.convertAndSend(MqConstant.EXCHANGE_DATA, MqConstant.RK_FILE_PROCESS, message);
    }
}

package com.yuan.user.service.impl;

import com.yuan.constant.MqConstant;
import com.yuan.model.dto.processor.FileProcessMessage;
import com.yuan.user.service.RabbitMqService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RabbitMqServiceImpl implements RabbitMqService {

    private final RabbitTemplate rabbitTemplate; //@RequiredArgsConstructor + private final 的构造器注入

    @Override
    public void sendFileProcessMessage(FileProcessMessage message) {
        //每条消息绑定一个唯一标识（这里用 taskId 任务 ID）
        //当回调触发（成功 / 失败）
        CorrelationData correlationData = new CorrelationData(message.getTaskId());
        rabbitTemplate.convertAndSend(MqConstant.EXCHANGE_DATA, MqConstant.RK_FILE_PROCESS, message, correlationData);
    }
}

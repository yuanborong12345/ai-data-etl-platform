package com.yuan.user.service;

import com.yuan.model.dto.processor.FileProcessMessage;

/**
 * RabbitMQ 消息发送服务
 */
public interface RabbitMqService {

    /**
     * 发送文件处理消息到 Processor 队列
     */
    void sendFileProcessMessage(FileProcessMessage message);
}

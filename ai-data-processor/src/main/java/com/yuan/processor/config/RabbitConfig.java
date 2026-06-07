package com.yuan.processor.config;


import com.yuan.constant.MqConstant;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Processor 模块 RabbitMQ 配置
 * 声明交换机 + 文件处理队列（本模块消费），并提供消息发送模板
 */
@Configuration
public class RabbitConfig {

    /* ======================== 交换机 ======================== */

    @Bean
    public DirectExchange dataExchange() {
        return ExchangeBuilder.directExchange(MqConstant.EXCHANGE_DATA)
                .durable(true)
                .build();
    }

    /* ======================== 队列定义 ======================== */

    /**
     * 文件处理队列：User 服务上传完成后投递，Processor 消费执行 ETL
     */
    @Bean
    public Queue fileProcessQueue() {
        return QueueBuilder.durable(MqConstant.QUEUE_FILE_PROCESS)
                .build();
    }

    /* ======================== 绑定关系 ======================== */

    @Bean
    public Binding fileProcessBinding(Queue fileProcessQueue, DirectExchange dataExchange) {
        return BindingBuilder.bind(fileProcessQueue)
                .to(dataExchange)
                .with(MqConstant.RK_FILE_PROCESS);
    }

    /* ======================== 消息发送模板 ======================== */

    @Bean
    public AmqpTemplate amqpTemplate(ConnectionFactory connectionFactory) {
        return new RabbitTemplate(connectionFactory);
    }
}

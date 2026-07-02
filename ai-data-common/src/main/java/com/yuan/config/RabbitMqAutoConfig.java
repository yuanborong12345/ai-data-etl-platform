package com.yuan.config;

import com.yuan.constant.MqConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 共享自动配置（各模块复用）
 * 仅在 AMQP 依赖存在的模块中加载，Gateway（WebFlux）自动跳过。
 */
@Slf4j
@Configuration
@ConditionalOnClass(AmqpTemplate.class)
public class RabbitMqAutoConfig {

    /* ======================== 死信队列 ======================== */

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(MqConstant.EXCHANGE_DLX)
                .durable(true)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(MqConstant.QUEUE_DEAD_LETTER)
                .build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(MqConstant.RK_DEAD_LETTER);
    }

    /* ======================== 消息转换器 & 回调 ======================== */

    @Bean
    @ConditionalOnMissingBean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 发送确认回调（默认实现），需定制的模块自行声明同名 Bean 覆盖。
     */
    @Bean
    @ConditionalOnMissingBean
    public RabbitTemplate.ConfirmCallback confirmCallback() {
        return (CorrelationData correlationData, boolean ack, String cause) -> {
            String id = correlationData != null ? correlationData.getId() : "unknown";
            if (ack) {
                log.info("消息投递成功, correlationDataId={}", id);
            } else {
                log.error("消息投递失败, correlationDataId={}, cause={}", id, cause);
            }
        };
    }

    /**
     * 消息退回回调（路由失败）
     */
    @Bean
    @ConditionalOnMissingBean
    public RabbitTemplate.ReturnsCallback returnsCallback(){
        return returnedMessage -> {
            log.error("消息路由失败，消息体:{}", returnedMessage.getMessage());
            // 路由异常兜底处理
        };
    }
}

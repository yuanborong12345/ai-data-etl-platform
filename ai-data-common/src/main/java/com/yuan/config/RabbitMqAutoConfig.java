package com.yuan.config;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 共享自动配置（各模块复用）
 * 仅在 AMQP 依赖存在的模块中加载，Gateway（WebFlux）自动跳过。
 */
@Configuration
@ConditionalOnClass(AmqpTemplate.class)
public class RabbitMqAutoConfig {

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

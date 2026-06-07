package com.yuan.monitor.config;

import com.yuan.constant.MqConstant;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Monitor 模块 RabbitMQ 配置
 * 声明 Token 计费队列（本模块消费），用于异步记录 AI 调用成本
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
     * Token 计费队列：Intelligence AI 分析完成后投递，Monitor 消费进行计费审计
     */
    @Bean
    public Queue tokenBillingQueue() {
        return QueueBuilder.durable(MqConstant.QUEUE_TOKEN_BILLING)
                .build();
    }

    /* ======================== 绑定关系 ======================== */

    @Bean
    public Binding tokenBillingBinding(Queue tokenBillingQueue, DirectExchange dataExchange) {
        return BindingBuilder.bind(tokenBillingQueue)
                .to(dataExchange)
                .with(MqConstant.RK_TOKEN_BILLING);
    }
}

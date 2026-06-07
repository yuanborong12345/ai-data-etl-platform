package com.yuan.intelligence.config;

import com.yuan.constant.MqConstant;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Intelligence 模块 RabbitMQ 配置
 * 声明数据分析队列（本模块消费），以及 Token 计费队列的发送能力
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
     * 数据分析队列：Processor ETL 完成后投递，Intelligence 消费执行 AI 分析
     */
    @Bean
    public Queue dataAnalysisQueue() {
        return QueueBuilder.durable(MqConstant.QUEUE_DATA_ANALYSIS)
                .build();
    }

    /* ======================== 绑定关系 ======================== */

    @Bean
    public Binding dataAnalysisBinding(Queue dataAnalysisQueue, DirectExchange dataExchange) {
        return BindingBuilder.bind(dataAnalysisQueue)
                .to(dataExchange)
                .with(MqConstant.RK_DATA_ANALYSIS);
    }
}

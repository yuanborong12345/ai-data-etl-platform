package com.yuan.constant;

/**
 * RabbitMQ 队列与交换机常量
 */
public interface MqConstant {

    // ======================== 交换机 ========================

    /** 数据交换主交换机（Direct 类型） */
    String EXCHANGE_DATA = "ai-data.exchange";

    // ======================== 队列 ========================

    /** 文件处理队列：User → Processor（ETL 管道入口） */
    String QUEUE_FILE_PROCESS = "ai-data.queue.file.process";

    /** 数据分析队列：Processor → Intelligence（AI 分析入口） */
    String QUEUE_DATA_ANALYSIS = "ai-data.queue.data.analysis";

    /** Token 计费队列：Intelligence → Monitor（审计入口） */
    String QUEUE_TOKEN_BILLING = "ai-data.queue.token.billing";

    // ======================== Routing Keys ========================

    /** 文件处理路由键 */
    String RK_FILE_PROCESS = "ai-data.rk.file.process";

    /** 数据分析路由键 */
    String RK_DATA_ANALYSIS = "ai-data.rk.data.analysis";

    /** Token 计费路由键 */
    String RK_TOKEN_BILLING = "ai-data.rk.token.billing";
}

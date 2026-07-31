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

    // ======================== Routing Keys ========================

    /** 文件处理路由键 */
    String RK_FILE_PROCESS = "ai-data.rk.file.process";

    /** 数据分析路由键 */
    String RK_DATA_ANALYSIS = "ai-data.rk.data.analysis";

    // ======================== 死信队列 ========================

    /** 死信交换机 */
    String EXCHANGE_DLX = "ai-data.dlx";

    /** 死信队列 */
    String QUEUE_DEAD_LETTER = "ai-data.queue.dead.letter";

    /** 死信路由键 */
    String RK_DEAD_LETTER = "ai-data.rk.dead.letter";

    // ======================== 通用 ========================
    Integer DEFAULT_MAX_RETRY_COUNT = 3;
}

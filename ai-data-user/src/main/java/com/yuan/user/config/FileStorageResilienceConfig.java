package com.yuan.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件存储容错配置。
 *
 * <p>提供重试（retry）和熔断（circuit breaker）两种容错机制，防止 MinIO 等
 * 对象存储服务短暂不可用时雪崩至应用层。对应配置前缀 {@code file.storage.resilience}。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "file.storage.resilience")
public class FileStorageResilienceConfig {

    /** 是否启用重试机制。默认启用。 */
    private boolean retryEnabled = true;

    /** 最大重试次数（首次调用不计入）。默认 3 次。 */
    private int maxAttempts = 3;

    /** 重试间隔毫秒数。默认 200ms。 */
    private long backoffMillis = 200L;

    /** 是否启用熔断机制。默认启用。 */
    private boolean circuitEnabled = true;

    /** 熔断器触发阈值：连续失败超过此次数后断开。默认 5 次。 */
    private int failureThreshold = 5;

    /** 熔断器断开持续时间（毫秒）。到期后半开尝试恢复。默认 30 秒。 */
    private long openDurationMillis = 30000L;

}

package com.yuan.user.service.storage.support;

import com.yuan.common.ErrorCode;
import com.yuan.exception.BusinessException;
import com.yuan.user.config.FileStorageResilienceConfig;
import io.minio.errors.ErrorResponseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 文件存储操作防护门面。
 *
 * <p>为文件存储操作提供重试（retry）和熔断（circuit breaker）两种容错能力。
 * 通过 {@link FileStorageResilienceConfig} 驱动，对所有存储策略的远程调用进行包装，
 * 避免因 MinIO 等外部存储短暂不可用而导致请求直接失败。</p>
 *
 * <p>熔断状态机：CLOSED（正常）→ OPEN（断开）→ HALF_OPEN（半开尝试）→ CLOSED。</p>
 */
@Slf4j
@Component
public class FileStorageGuard {

    /**  */
    private final FileStorageResilienceConfig properties;

    /** 各操作对应的熔断器状态（key=操作名称，如 minio.save）。 */
    private final ConcurrentMap<String, CircuitState> circuits = new ConcurrentHashMap<>();

    public FileStorageGuard(FileStorageResilienceConfig properties) {
        this.properties = properties;
    }

    /**
     * 执行存储操作，自动附加重试与熔断保护。
     *
     * @param operationName 操作名称（用于日志和熔断器标识）
     * @param callable      实际存储调用
     * @param <T>           返回类型
     * @return 存储调用结果
     */
    public <T> T execute(String operationName, StorageCallable<T> callable) {
        // 1. 检查熔断器状态，已断开则快速失败
        CircuitState circuit = circuits.computeIfAbsent(operationName, ignored -> new CircuitState());
        if (isCircuitOpen(operationName, circuit)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "文件存储服务暂时不可用，operation=" + operationName);
        }

        // 2. 按重试策略执行调用
        int maxAttempts = resolveMaxAttempts();
        Throwable lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T result = callable.call();
                // 调用成功 → 重置熔断器
                onSuccess(circuit);
                return result;
            } catch (Throwable e) {
                lastFailure = e;
                // 判断是否应该继续重试
                if (!shouldRetry(e, attempt, maxAttempts)) {
                    onFailure(operationName, circuit, e);
                    throw convertException(operationName, e);
                }
                log.warn("文件存储操作失败，即将重试。operation={}，attempt={}，maxAttempts={}，cause={}",
                        operationName, attempt, maxAttempts, e.toString());
                sleepBeforeRetry();
            }
        }

        // 3. 所有重试均失败
        onFailure(operationName, circuit, lastFailure);
        throw convertException(operationName, lastFailure);
    }

    /**
     * 检查熔断器是否处于断开状态。
     *
     * <p>OPEN 状态的熔断器在达到 {@code openDurationMillis} 后会转为 HALF_OPEN，
     * 放行一次试探请求。</p>
     *
     * @param operationName 操作名称（仅用于日志）
     * @param circuit       熔断器状态
     * @return true 表示已熔断，应快速失败
     */
    private boolean isCircuitOpen(String operationName, CircuitState circuit) {
        if (!properties.isCircuitEnabled() || circuit.status == CircuitStatus.CLOSED) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (circuit.status == CircuitStatus.OPEN) {
            // 熔断持续时间未到，仍为 OPEN
            if (now - circuit.openedAt < Math.max(properties.getOpenDurationMillis(), 1L)) {
                return true;
            }
            // 到期 → 转为 HALF_OPEN，允许一次试探
            circuit.status = CircuitStatus.HALF_OPEN;
            log.info("文件存储熔断器半开，operation={}", operationName);
        }
        return false;
    }

    /**
     * 判断当前异常是否应触发重试。
     *
     * @param e           当前异常
     * @param attempt     当前已尝试次数
     * @param maxAttempts 最大允许次数
     * @return true 表示应重试
     */
    private boolean shouldRetry(Throwable e, int attempt, int maxAttempts) {
        return properties.isRetryEnabled()
                && attempt < maxAttempts
                && isRetryable(e);
    }

    /**
     * 判断异常是否属于可重试类型。
     *
     * <p>可重试的异常包括：网络超时、连接异常、DNS 解析失败、
     * MinIO 服务端错误（5xx）以及临时性 MinIO 错误码。</p>
     *
     * @param e 异常
     * @return true 表示可重试
     */
    private boolean isRetryable(Throwable e) {
        // BusinessException 表示业务校验失败，不重试
        if (e instanceof BusinessException) {
            return false;
        }
        // 网络层面的可重试异常
        if (e instanceof SocketTimeoutException
                || e instanceof ConnectException
                || e instanceof UnknownHostException) {
            return true;
        }
        // MinIO SDK 异常：服务端错误（5xx）或临时性错误码可重试
        if (e instanceof ErrorResponseException errorResponseException) {
            String code = errorCode(errorResponseException);
            int statusCode = errorResponseException.response() == null
                    ? 0 : errorResponseException.response().code();
            return statusCode >= 500 || isTemporaryMinioCode(code);
        }
        // 检查根因是否可重试
        Throwable cause = e.getCause();
        if (cause != null && cause != e && isRetryable(cause)) {
            return true;
        }
        // 兜底：IO 异常视为可重试
        return e instanceof IOException;
    }

    /**
     * 将存储层异常转换为统一的 {@link BusinessException}。
     *
     * @param operationName 操作名称
     * @param e             原始异常
     * @return 业务异常
     */
    private BusinessException convertException(String operationName, Throwable e) {
        if (e instanceof BusinessException businessException) {
            return businessException;
        }
        if (e instanceof ErrorResponseException errorResponseException) {
            // 文件不存在 → NOT_FOUND_ERROR
            if (isNotFound(errorResponseException)) {
                return new BusinessException(ErrorCode.NOT_FOUND_ERROR,
                        "文件存储对象不存在，operation=" + operationName);
            }
            // 认证授权失败 → OPERATION_ERROR
            if (isAuthError(errorResponseException)) {
                return new BusinessException(ErrorCode.OPERATION_ERROR,
                        "文件存储认证失败，operation=" + operationName);
            }
        }
        return new BusinessException(ErrorCode.OPERATION_ERROR,
                "文件存储操作失败，operation=" + operationName);
    }

    /**
     * 调用成功时重置熔断器至 CLOSED 状态。
     */
    private void onSuccess(CircuitState circuit) {
        // 成功调用重置失败计数，避免误触发熔断
        circuit.failureCount = 0;
        // 无论之前状态如何，成功都重置为 CLOSED
        circuit.status = CircuitStatus.CLOSED;
        // openedAt 仅在 OPEN 状态有意义，重置为 0
        circuit.openedAt = 0L;
    }

    /**
     * 调用失败时累加失败计数，达到阈值则断开熔断器。
     *
     * @param operationName 操作名称
     * @param circuit       熔断器状态
     * @param e             失败异常
     */
    private void onFailure(String operationName, CircuitState circuit, Throwable e) {
        if (!properties.isCircuitEnabled()) {
            return;
        }
        circuit.failureCount++;
        // HALF_OPEN 状态下失败立即断开；或连续失败达到阈值
        if (circuit.status == CircuitStatus.HALF_OPEN
                || circuit.failureCount >= Math.max(properties.getFailureThreshold(), 1)) {
            circuit.status = CircuitStatus.OPEN;
            circuit.openedAt = System.currentTimeMillis();
            log.warn("文件存储熔断器已断开，operation={}，failureCount={}，cause={}",
                    operationName, circuit.failureCount, e == null ? "未知" : e.toString());
        }
    }

    /**
     * 重试间隔休眠。
     */
    private void sleepBeforeRetry() {
        long backoffMillis = properties.getBackoffMillis();
        if (backoffMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件存储重试被中断");
        }
    }

    /**
     * 解析最大重试次数（熔断禁用时只执行 1 次）。
     *
     * @return 最大执行次数（含首次）
     */
    private int resolveMaxAttempts() {
        if (!properties.isRetryEnabled()) {
            return 1;
        }
        return Math.max(properties.getMaxAttempts(), 1);
    }

    /**
     * 判断 MinIO 异常是否为"文件/桶不存在"。
     */
    private boolean isNotFound(ErrorResponseException e) {
        String code = errorCode(e);
        return "NoSuchKey".equals(code) || "NoSuchBucket".equals(code) || "NoSuchObject".equals(code);
    }

    /**
     * 判断 MinIO 异常是否为认证/授权错误。
     */
    private boolean isAuthError(ErrorResponseException e) {
        String code = errorCode(e);
        int statusCode = e.response() == null ? 0 : e.response().code();
        return statusCode == 401
                || statusCode == 403
                || "AccessDenied".equals(code)
                || "InvalidAccessKeyId".equals(code)
                || "SignatureDoesNotMatch".equals(code);
    }

    /**
     * 判断 MinIO 错误码是否为临时性错误（可重试）。
     */
    private boolean isTemporaryMinioCode(String code) {
        if (code == null) {
            return false;
        }
        String normalized = code.toLowerCase(Locale.ROOT);
        return normalized.contains("timeout")
                || normalized.contains("temporar")
                || normalized.contains("slowdown")
                || normalized.contains("unavailable")
                || normalized.contains("internalerror");
    }

    /**
     * 从 MinIO 异常中提取错误码。
     */
    private String errorCode(ErrorResponseException e) {
        return e.errorResponse() == null ? null : e.errorResponse().code();
    }

    /**
     * 熔断器状态枚举。
     */
    private enum CircuitStatus {
        /** 正常状态，请求正常通过。 */
        CLOSED,
        /** 断开状态，请求快速失败。 */
        OPEN,
        /** 半开状态，放行一次试探请求。 */
        HALF_OPEN
    }

    /**
     * 熔断器内部状态（失败计数、断开时间戳、当前状态）。
     */
    private static class CircuitState {
        private int failureCount;
        private long openedAt;
        private CircuitStatus status = CircuitStatus.CLOSED;
    }
}

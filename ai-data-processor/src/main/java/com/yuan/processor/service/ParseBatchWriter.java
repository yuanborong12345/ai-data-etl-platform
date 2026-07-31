package com.yuan.processor.service;

import com.yuan.common.ErrorCode;
import com.yuan.exception.BusinessException;
import com.yuan.model.entity.ParseData;
import com.yuan.processor.mapper.ParseDataMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分批异步落库写入器。
 *
 * 线程池 fixed=4，有界队列=20，CallerRunsPolicy 背压：
 * 队列满时调用者线程（EasyExcel 读取线程）直接执行 insert，
 * Excel 读取速度自动匹配 DB 写入速度。
 */
@Slf4j
@Component
public class ParseBatchWriter {

    private final ThreadPoolExecutor executor;
    private final ParseDataMapper parseDataMapper;

    public ParseBatchWriter(ParseDataMapper parseDataMapper) {
        this.parseDataMapper = parseDataMapper;
        this.executor = new ThreadPoolExecutor(
                4, 4,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(20),
                r -> {
                    Thread t = new Thread(r, "parse-batch-writer");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        this.executor.allowCoreThreadTimeOut(true);
    }

    @PreDestroy
    public void shutdown() {
        log.info("ParseBatchWriter 线程池开始关闭...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("线程池未在30秒内完成，强制关闭");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 每次 parse 调用新建一个 session，跟踪本次解析的所有异步写入。
     */
    public BatchSession newSession() {
        return new BatchSession();
    }

    public class BatchSession {

        private final List<Future<?>> futures = new ArrayList<>();
        private final AtomicInteger submittedCount = new AtomicInteger(0);
        private volatile boolean completed;

        void submitBatch(List<ParseData> batch) {
            submittedCount.addAndGet(batch.size());
            futures.add(executor.submit(() -> parseDataMapper.insertBatch(batch)));
        }

        /**
         * 正常流程中等待所有批次写入完成。
         */
        void awaitCompletion() {
            if (completed) return;
            waitFutures();
            completed = true;
        }

        /**
         * finally 保底：防止异常时已提交的批次无人等待。
         * 与 awaitCompletion 互斥（completed 标记），可安全重复调用。
         */
        void ensureCompleted() {
            if (completed) return;
            log.warn("解析异常，等待已提交批次写入完成...");
            waitFutures();
            completed = true;
        }

        private void waitFutures() {
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    log.error("分批落库失败", e.getCause());
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,
                            "分批落库失败: " + e.getCause().getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "分批落库被中断");
                }
            }
        }

        public int getSubmittedCount() {
            return submittedCount.get();
        }
    }
}

package com.yuan.processor.service;

import com.yuan.common.ErrorCode;
import com.yuan.exception.BusinessException;
import com.yuan.model.dto.storage.PresignedDownloadResult;
import com.yuan.serviceclient.feign.FileFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;

/**
 * 预签名 URL 直传下载服务。
 *
 * <p>不再通过 User 模块中转文件流，改为两步：
 * 1. Feign 拿 MinIO 预签名临时下载 URL
 * 2. RestTemplate 直连 MinIO HTTP GET 下载
 * User 模块零文件负载，Processor 无需 MinIO SDK。</p>
 */
@Slf4j
@Service
public class FileDownloadService {

    private static final long PRESIGNED_EXPIRES_SECONDS = 600L;

    private final FileFeignClient fileFeignClient;
    private final RestTemplate restTemplate;

    public FileDownloadService(FileFeignClient fileFeignClient, RestTemplate restTemplate) {
        this.fileFeignClient = fileFeignClient;
        this.restTemplate = restTemplate;
    }

    /**
     * 通过预签名 URL 直连 MinIO 下载文件。
     *
     * @param fileId 文件 ID
     * @return 文件内容输入流
     */
    public InputStream download(Long fileId) {
        // 1. 从 user 模块获取预签名下载 URL
        log.info("获取预签名下载URL: fileId={}", fileId);
        PresignedDownloadResult presigned = fileFeignClient
                .createDownloadPresigned(fileId, PRESIGNED_EXPIRES_SECONDS)
                .getData();
        String downloadUrl = presigned.getDownloadUrl();
        log.info("预签名URL已获取: fileId={}, expiresAt={}, urlPrefix={}",
                fileId, presigned.getExpiresAt(), downloadUrl.substring(0, downloadUrl.indexOf('?')));

        // 2. 直连 MinIO 下载，使用 URI 避免 RestTemplate 对签名参数二次编码
        URI uri = URI.create(downloadUrl);
        byte[] content = restTemplate.getForObject(uri, byte[].class);
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "MinIO 下载失败，文件内容为空: fileId=" + fileId);
        }
        log.info("MinIO直传下载完成: fileId={}, size={} bytes", fileId, content.length);
        return new ByteArrayInputStream(content);
    }
}

package com.yuan.processor.service;

import com.yuan.common.ErrorCode;
import com.yuan.exception.BusinessException;
import com.yuan.model.dto.storage.PresignedDownloadResult;
import com.yuan.serviceclient.feign.FileFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * 预签名 URL 直传下载服务。
 *
 * 1. Feign 拿 MinIO 预签名临时下载 URL
 * 2. RestTemplate 直连 MinIO HTTP GET 流式下载
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
     * 通过预签名 URL 直连 MinIO 流式下载文件。
     *
     * 返回的 InputStream 直接映射 HTTP 响应体，不会全量加载到内存。
     * 调用方关闭流时自动释放 HTTP 连接。
     *
     * @param fileId 文件 ID
     * @return 文件内容输入流（调用方负责关闭）
     */
    public InputStream download(Long fileId) {
        log.info("获取预签名下载URL: fileId={}", fileId);
        PresignedDownloadResult presigned = fileFeignClient
                .createDownloadPresigned(fileId, PRESIGNED_EXPIRES_SECONDS)
                .getData();
        String downloadUrl = presigned.getDownloadUrl();
        log.info("预签名URL已获取: fileId={}, expiresAt={}", fileId, presigned.getExpiresAt());

        URI uri = URI.create(downloadUrl);
        try {
            ClientHttpResponse response = restTemplate.getRequestFactory()
                    .createRequest(uri, HttpMethod.GET)
                    .execute();
            if (!response.getStatusCode().is2xxSuccessful()) {
                String statusText = response.getStatusCode() + " " + response.getStatusText();
                response.close();
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "MinIO 下载失败: " + statusText + ", fileId=" + fileId);
            }
            InputStream body = response.getBody();
            if (body == null) {
                response.close();
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "MinIO 下载失败，文件内容为空: fileId=" + fileId);
            }
            log.info("MinIO流式下载已建立: fileId={}", fileId);
            return new ResponseInputStream(response, body);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "MinIO 下载连接失败: " + e.getMessage());
        }
    }

    private static class ResponseInputStream extends FilterInputStream {
        private final ClientHttpResponse response;

        ResponseInputStream(ClientHttpResponse response, InputStream body) {
            super(body);
            this.response = response;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                response.close();
            }
        }
    }
}

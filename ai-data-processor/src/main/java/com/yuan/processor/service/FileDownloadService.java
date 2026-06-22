package com.yuan.processor.service;

import com.yuan.serviceclient.feign.FileFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * 文件下载服务，通过 Feign 远程调用 user 模块下载文件内容。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileDownloadService {

    private final FileFeignClient fileFeignClient;

    /**
     * 根据文件 ID 下载文件内容，返回输入流。
     *
     * @param fileId 文件 ID
     * @return 文件内容输入流
     */
    public InputStream download(Long fileId) {
        log.info("远程下载文件: fileId={}", fileId);
        byte[] content = fileFeignClient.download(fileId);
        log.info("文件下载完成: fileId={}, size={} bytes", fileId, content.length);
        return new ByteArrayInputStream(content);
    }
}

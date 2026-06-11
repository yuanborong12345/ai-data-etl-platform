package com.yuan.user.service.storage.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Duration;

/**
 * 预签名上传请求
 */
@Data
public class PresignedUploadRequest {

    private String fileName;

    private String contentType;

    private String pathPrefix;

    private Duration expires = Duration.ofMinutes(15);
}

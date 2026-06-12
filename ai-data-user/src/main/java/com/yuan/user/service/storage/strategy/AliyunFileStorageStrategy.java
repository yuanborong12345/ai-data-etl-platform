package com.yuan.user.service.storage.strategy;

import com.yuan.common.ErrorCode;
import com.yuan.exception.BusinessException;
import com.yuan.model.enums.FileStorageType;
import com.yuan.user.config.FileStorageConfig;
import com.yuan.model.dto.storage.FileStorageObject;
import com.yuan.model.dto.storage.FileStorageResult;
import com.yuan.model.dto.storage.FileStorageStat;
import com.yuan.model.dto.storage.PresignedDownloadResult;
import com.yuan.model.dto.storage.PresignedUploadRequest;
import com.yuan.model.dto.storage.PresignedUploadResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/**
 * 阿里云 OSS 文件存储策略（待实现）。
 *
 * <p>该策略当前为骨架实现，所有业务方法抛出 {@code "阿里云 OSS 文件存储暂未实现"} 异常。
 * 待对接阿里云 OSS SDK 后按 {@link FileStorageStrategy} 契约完整实现。</p>
 */
@Service
@ConditionalOnProperty(prefix = "file.storage.aliyun", name = "enabled", havingValue = "true")
public class AliyunFileStorageStrategy extends AbstractFileStorageStrategy {

    public AliyunFileStorageStrategy(FileStorageConfig fileStorageConfig) {
        super(fileStorageConfig);
    }

    @Override
    public FileStorageType getStorageType() {
        return FileStorageType.ALIYUN;
    }

    @Override
    public FileStorageResult save(MultipartFile file) {
        validateConfig();
        validateFileExtension(file.getOriginalFilename());
        throw unsupported();
    }

    @Override
    public FileStorageObject getObject(String storagePath) {
        validateConfig();
        throw unsupported();
    }

    @Override
    public FileStorageStat stat(String storagePath) {
        validateConfig();
        throw unsupported();
    }

    @Override
    public boolean exists(String storagePath) {
        validateConfig();
        throw unsupported();
    }

    @Override
    public boolean delete(String storagePath) {
        validateConfig();
        throw unsupported();
    }

    @Override
    public PresignedUploadResult createUploadPresigned(PresignedUploadRequest request) {
        validateConfig();
        throw unsupported();
    }

    @Override
    public PresignedDownloadResult createDownloadPresigned(String storagePath, Duration expires) {
        validateConfig();
        throw unsupported();
    }

    @Override
    public String copy(String sourceStoragePath, String targetStoragePath) {
        validateConfig();
        throw unsupported();
    }

    @Override
    public String move(String sourceStoragePath, String targetStoragePath) {
        validateConfig();
        throw unsupported();
    }

    @Override
    public String promote(String storagePath) {
        validateConfig();
        throw unsupported();
    }

    private void validateConfig() {
        fileStorageConfig.validateEnabled(getStorageType());
        FileStorageConfig.Aliyun aliyun = fileStorageConfig.getAliyun();
        required(aliyun.getEndpoint(), "file.storage.aliyun.endpoint");
        required(aliyun.getBucketName(), "file.storage.aliyun.bucket-name");
        required(aliyun.getAccessKeyId(), "file.storage.aliyun.access-key-id");
        required(aliyun.getAccessKeySecret(), "file.storage.aliyun.access-key-secret");
    }

    private BusinessException unsupported() {
        return new BusinessException(ErrorCode.OPERATION_ERROR, "阿里云 OSS 文件存储暂未实现");
    }
}

package com.yuan.user.config;

import com.yuan.common.ErrorCode;
import com.yuan.exception.BusinessException;
import com.yuan.model.enums.FileStorageType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件存储配置。
 *
 * <p>当前配置采用强类型结构，不再通过 Provider Map 收集各存储实现的配置。
 * 调用方可以通过 activeType 获取当前激活的存储策略，也可以按 storageType
 * 获取指定存储实现的配置。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "file.storage")
public class FileStorageConfig {

    /** 当前激活的文件存储类型。必须通过配置显式指定。 */
    private FileStorageType activeType;

    /** 允许上传的文件扩展名白名单（小写，不含前导点），为空表示不限制。 */
    private List<String> allowedFileTypes = new ArrayList<>();

    /** 是否开启暂存模式。开启后文件先写入暂存路径，确认使用后通过 promote() 转移至久存路径。 */
    private boolean stageMode = false;

    /** 暂存路径前缀，仅在 stage-mode=true 时生效。 */
    private String stagePrefix = "staging";

    /** 阿里云 OSS 文件存储配置。 */
    private Aliyun aliyun = new Aliyun();

    /** MinIO 文件存储配置。 */
    private Minio minio = new Minio();

    /**
     * 根据存储类型获取对应的存储配置。
     *
     * @param storageType 文件存储类型
     * @return 存储配置对象
     */
    public StorageProperties getConfig(FileStorageType storageType) {
        if (storageType == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件存储类型不能为空");
        }
        return switch (storageType) {
            case ALIYUN -> aliyun;
            case MINIO -> minio;
        };
    }

    /**
     * 校验指定存储类型是否已启用。
     *
     * @param storageType 文件存储类型
     */
    public void validateEnabled(FileStorageType storageType) {
        StorageProperties config = getConfig(storageType);
        if (config == null || !config.isEnabled()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "文件存储未启用: file.storage." + storageType.getCode() + ".enabled");
        }
    }

    /**
     * 文件存储公共配置。
     */
    @Data
    public static class StorageProperties {

        /** 是否启用当前存储实现。 */
        private boolean enabled;

        /** 对象存储路径前缀，例如 uploads。 */
        private String pathPrefix = "uploads";

        /** 公开访问地址，可用于拼接公开下载地址。 */
        private String publicUrl;

    }

    /**
     * 阿里云 OSS 文件存储配置。
     */
    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class Aliyun extends StorageProperties {

        /** OSS Endpoint。 */
        private String endpoint;

        /** OSS Bucket 名称。 */
        private String bucketName;

        /** AccessKey ID。 */
        private String accessKeyId;

        /** AccessKey Secret。 */
        private String accessKeySecret;

    }

    /**
     * MinIO 文件存储配置。
     */
    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class Minio extends StorageProperties {

        /** MinIO Endpoint，例如 http://127.0.0.1:9000。 */
        private String endpoint;

        /** MinIO Bucket 名称。 */
        private String bucketName;

        /** MinIO Access Key。 */
        private String accessKey;

        /** MinIO Secret Key。 */
        private String secretKey;

    }
}

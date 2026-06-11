package com.yuan.user.service.storage.model;

import com.yuan.model.enums.FileStorageType;
import lombok.Data;

/**
 * 存储实体的公共基类。
 *
 * <p>包含所有存储结果类型共有的 {@code storagePath} 与 {@code storageType} 字段，
 * 消除各实体类中的重复声明。</p>
 */
@Data
public class StorageEntity {

    /** 存储路径。本地存储为相对路径，MinIO/OSS 为 objectName。 */
    private String storagePath;

    /** 存储类型。 */
    private FileStorageType storageType;
}

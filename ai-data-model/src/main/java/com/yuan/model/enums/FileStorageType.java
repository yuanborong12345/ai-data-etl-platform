package com.yuan.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 文件存储类型枚举
 */
public enum FileStorageType {

    /** 阿里云 OSS 对象存储 */
    ALIYUN("aliyun", "阿里云OSS对象存储"),
    /** MinIO 对象存储 */
    MINIO("minio", "MinIO存储");

    @EnumValue
    private final String code;
    private final String description;

    FileStorageType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据 code 获取枚举
     */
    public static FileStorageType fromCode(String code) {
        for (FileStorageType storageType : values()) {
            if (storageType.code.equals(code)) {
                return storageType;
            }
        }
        throw new IllegalArgumentException("未知文件存储类型: " + code);
    }
}

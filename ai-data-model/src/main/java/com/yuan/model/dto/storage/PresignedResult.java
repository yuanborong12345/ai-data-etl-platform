package com.yuan.model.dto.storage;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * 预签名结果公共基类。
 *
 * <p>包含上传预签名与下载预签名共有的 {@code method} 与 {@code expiresAt} 字段，
 * 继承自 {@link StorageEntity} 以复用存储路径与类型。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PresignedResult extends StorageEntity {

    /** HTTP 方法，例如 PUT / GET。 */
    private String method;

    /** 预签名地址到期时间。 */
    private Instant expiresAt;
}

package com.yuan.user.service.storage.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 预签名下载结果。
 *
 * <p>包含前端可直接使用的下载 URL 及有效期信息。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PresignedDownloadResult extends PresignedResult {

    /** 下载预签名 URL。 */
    private String downloadUrl;
}

package com.yuan.user.service.storage.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.Map;

/**
 * 预签名上传结果。
 *
 * <p>包含前端可直接使用的上传 URL、存储路径及有效期信息。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PresignedUploadResult extends PresignedResult {

    /** 上传预签名 URL。 */
    private String uploadUrl;

    /** 预注册的文件元数据 ID，用于客户端上传完成后回调确认。 */
    private Long fileId;

    /** 额外请求头，前端上传时需要携带。 */
    private Map<String, String> headers = new HashMap<>();
}

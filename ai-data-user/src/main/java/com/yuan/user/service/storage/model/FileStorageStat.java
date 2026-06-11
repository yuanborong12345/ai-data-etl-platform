package com.yuan.user.service.storage.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.ZonedDateTime;

/**
 * 文件存储元信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FileStorageStat extends StorageEntity {

    /** 文件大小，单位：字节。 */
    private Long size;

    /** MIME 类型。 */
    private String contentType;

    /** 对象 ETag。 */
    private String etag;

    /** 最后修改时间。 */
    private ZonedDateTime lastModified;
}

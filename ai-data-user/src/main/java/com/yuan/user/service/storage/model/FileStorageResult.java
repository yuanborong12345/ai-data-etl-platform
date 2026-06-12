package com.yuan.user.service.storage.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件保存结果。
 *
 * <p>用于承载上传完成后的文件元数据，业务层通常会将这些字段写入 file_info 表。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FileStorageResult extends StorageEntity {

    /** 原始文件名。 */
    private String fileName;

    /** 文件大小，单位：字节。 */
    private Long fileSize;

    /** 文件类型，通常为扩展名，例如 xlsx、csv。 */
    private String fileType;

    /** MIME 类型。 */
    private String contentType;

    /** 文件 MD5。 */
    private String fileMd5;
}

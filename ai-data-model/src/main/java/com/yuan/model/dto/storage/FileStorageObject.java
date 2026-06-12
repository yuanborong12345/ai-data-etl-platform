package com.yuan.model.dto.storage;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.InputStream;

/**
 * 文件存储对象。
 *
 * <p>包含文件内容的输入流，调用方使用完成后必须关闭 {@link #getInputStream()}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FileStorageObject extends StorageEntity {

    /** 原始文件名。 */
    private String fileName;

    /** MIME 类型。 */
    private String contentType;

    /** 文件大小，单位：字节。 */
    private Long contentLength;

    /** 文件内容输入流。使用完成后必须关闭。 */
    private InputStream inputStream;
}

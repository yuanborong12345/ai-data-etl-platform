package com.yuan.user.service.storage.strategy;

import com.yuan.model.enums.FileStorageType;
import com.yuan.model.dto.storage.FileStorageObject;
import com.yuan.model.dto.storage.FileStorageResult;
import com.yuan.model.dto.storage.FileStorageStat;
import com.yuan.model.dto.storage.PresignedDownloadResult;
import com.yuan.model.dto.storage.PresignedUploadRequest;
import com.yuan.model.dto.storage.PresignedUploadResult;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/**
 * 文件存储策略接口。
 *
 * <p>不同存储实现需要遵守同一组文件生命周期能力，包括上传、读取、元信息查询、
 * 删除、预签名地址生成、复制和移动。</p>
 */
public interface FileStorageStrategy {

    /**
     * 当前策略支持的存储类型。
     *
     * @return 文件存储类型
     */
    FileStorageType getStorageType();

    /**
     * 服务端上传文件。
     *
     * @param file 上传文件
     * @return 文件保存结果
     */
    FileStorageResult save(MultipartFile file);

    /**
     * 读取文件对象。
     *
     * <p>返回对象中包含 InputStream，调用方使用完成后必须关闭。</p>
     *
     * @param storagePath 存储路径
     * @return 文件对象
     */
    FileStorageObject getObject(String storagePath);

    /**
     * 获取文件元信息。
     *
     * @param storagePath 存储路径
     * @return 文件元信息
     */
    FileStorageStat stat(String storagePath);

    /**
     * 判断文件是否存在。
     *
     * @param storagePath 存储路径
     * @return true 表示存在，false 表示不存在
     */
    boolean exists(String storagePath);

    /**
     * 删除文件。
     *
     * @param storagePath 存储路径
     * @return true 表示删除了文件，false 表示文件原本不存在
     */
    boolean delete(String storagePath);

    /**
     * 创建上传预签名地址。
     *
     * @param request 预签名上传请求
     * @return 预签名上传结果
     */
    PresignedUploadResult createUploadPresigned(PresignedUploadRequest request);

    /**
     * 创建下载预签名地址。
     *
     * @param storagePath 存储路径
     * @param expires     有效期
     * @return 预签名下载结果
     */
    PresignedDownloadResult createDownloadPresigned(String storagePath, Duration expires);

    /**
     * 复制文件。
     *
     * @param sourceStoragePath 源存储路径
     * @param targetStoragePath 目标存储路径
     * @return 目标存储路径
     */
    String copy(String sourceStoragePath, String targetStoragePath);

    /**
     * 移动文件。
     *
     * <p>对象存储通常没有真实移动能力，具体实现一般为复制成功后删除源文件。</p>
     *
     * @param sourceStoragePath 源存储路径
     * @param targetStoragePath 目标存储路径
     * @return 目标存储路径
     */
    String move(String sourceStoragePath, String targetStoragePath);

    /**
     * 将暂存文件提升为久存文件。
     *
     * <p>仅在 stage-mode 开启时使用。方法内部自动生成久存路径（以 pathPrefix 替换 stagePathPrefix），
     * 并通过 {@link #move(String, String)} 完成转移。</p>
     *
     * @param storagePath 暂存路径
     * @return 久存路径
     */
    String promote(String storagePath);
}

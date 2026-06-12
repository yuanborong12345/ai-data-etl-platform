package com.yuan.user.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.yuan.model.entity.FileInfo;
import com.yuan.model.dto.storage.FileStorageObject;
import com.yuan.model.dto.storage.PresignedDownloadResult;
import com.yuan.model.dto.storage.PresignedUploadRequest;
import com.yuan.model.dto.storage.PresignedUploadResult;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/**
 * 文件存储服务接口。
 *
 * <p>继承 MyBatis-Plus IService 提供通用 CRUD，同时封装文件上传、下载、
 * 预签名、删除与暂存提升等完整生命周期操作。</p>
 *
 * <p>方法分两层：</p>
 * <ul>
 *   <li><b>底层纯文件操作</b>（无消息队列职责）：{@link #uploadFile}, {@link #confirmUpload} 等</li>
 *   <li><b>上层任务方法</b>（文件操作 + MQ 提交）：{@link #uploadFileAndSubmitTask}, {@link #confirmUploadAndSubmitTask}</li>
 * </ul>
 */
public interface FileService extends IService<FileInfo> {

    // ======================== 底层纯文件操作 ========================

    /**
     * 服务端直接上传文件到对象存储，仅持久化元数据，不发送消息队列。
     *
     * @param file   上传文件
     * @param userId 上传用户 ID
     * @return 持久化后的文件元数据
     */
    FileInfo uploadFile(MultipartFile file, Long userId);

    /**
     * 获取文件对象（用于下载）。
     *
     * <p>返回对象中包含 InputStream，调用方使用完成后必须关闭。</p>
     *
     * @param fileId 文件 ID
     * @return 文件对象
     */
    FileStorageObject downloadObject(Long fileId);

    /**
     * 获取文件元信息。
     *
     * @param fileId 文件 ID
     * @return 文件元数据
     */
    FileInfo getFileInfo(Long fileId);

    /**
     * 创建上传预签名地址，并预注册文件元数据记录。
     *
     * @param request 预签名上传请求
     * @param userId  上传用户 ID
     * @return 预签名上传结果
     */
    PresignedUploadResult createUploadPresigned(PresignedUploadRequest request, Long userId);

    /**
     * 创建下载预签名地址。
     *
     * @param fileId  文件 ID
     * @param expires 预签名有效期，为 null 时默认 15 分钟
     * @return 预签名下载结果
     */
    PresignedDownloadResult createDownloadPresigned(Long fileId, Duration expires);

    /**
     * 删除文件（逻辑删除 + 存储层删除）。
     *
     * @param fileId 文件 ID
     * @return true 表示删除成功
     */
    boolean deleteFile(Long fileId);

    /**
     * 确认预签名上传完成，回填文件元数据（不发送消息队列）。
     *
     * <p>客户端通过预签名 URL 上传完成后调用此方法，回填文件大小与 contentType。</p>
     *
     * @param fileId 文件 ID
     * @return 更新后的文件元数据
     */
    FileInfo confirmUpload(Long fileId);

    /**
     * 将暂存文件提升为久存文件。
     *
     * <p>仅在 stage-mode 开启时生效，关闭时直接返回原记录。</p>
     *
     * @param fileId 文件 ID
     * @return 更新后的文件元数据
     */
    FileInfo promoteFile(Long fileId);

    // ======================== 上层任务方法（文件操作 + 消息队列提交） ========================

    /**
     * 上传文件并提交处理任务到消息队列。
     *
     * <p>组合调用 {@link #uploadFile} + 发送 {@code FileProcessMessage} 到 RabbitMQ，
     * 触发 ETL 管道。适用于服务端直接上传场景。</p>
     *
     * @param file          上传文件
     * @param userId        上传用户 ID
     * @param promptContent 用户分析需求描述（可选，如"分析各区域季度销售趋势"）
     * @return 持久化后的文件元数据
     */
    FileInfo uploadFileAndSubmitTask(MultipartFile file, Long userId, String promptContent);

    /**
     * 确认预签名上传完成并提交处理任务到消息队列。
     *
     * <p>组合调用 {@link #confirmUpload} + 发送 {@code FileProcessMessage} 到 RabbitMQ，
     * 触发 ETL 管道。适用于客户端预签名直传场景。</p>
     *
     * @param fileId        文件 ID
     * @param promptContent 用户分析需求描述（可选，如"分析各区域季度销售趋势"）
     * @return 更新后的文件元数据
     */
    FileInfo confirmUploadAndSubmitTask(Long fileId, String promptContent);
}

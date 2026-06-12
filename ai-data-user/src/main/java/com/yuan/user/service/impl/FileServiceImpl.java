package com.yuan.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuan.common.ErrorCode;
import com.yuan.exception.BusinessException;
import com.yuan.model.dto.processor.FileProcessMessage;
import com.yuan.model.entity.FileInfo;
import com.yuan.user.mapper.FileInfoMapper;
import com.yuan.user.service.FileService;
import com.yuan.user.service.RabbitMqService;
import com.yuan.user.service.storage.FileStorageStrategyFactory;
import com.yuan.user.service.storage.model.FileStorageObject;
import com.yuan.user.service.storage.model.FileStorageResult;
import com.yuan.user.service.storage.model.FileStorageStat;
import com.yuan.user.service.storage.model.PresignedDownloadResult;
import com.yuan.user.service.storage.model.PresignedUploadRequest;
import com.yuan.user.service.storage.model.PresignedUploadResult;
import com.yuan.user.service.storage.strategy.FileStorageStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * 文件存储服务实现类。
 *
 * <p>协调存储策略调用、元数据持久化和处理器任务提交。存储策略内部
 * 仍持有各提供商特定的 SDK 异常映射。</p>
 */
@Slf4j
@Service
public class FileServiceImpl extends ServiceImpl<FileInfoMapper, FileInfo>
        implements FileService {

    private final FileStorageStrategyFactory strategyFactory;

    private final RabbitMqService rabbitMqService;

    public FileServiceImpl(FileStorageStrategyFactory strategyFactory,
                           RabbitMqService rabbitMqService) {
        this.strategyFactory = strategyFactory;
        this.rabbitMqService = rabbitMqService;
    }

    /**
     * 服务端直接上传文件到对象存储。
     *
     * <p>仅持久化元数据，不发送消息队列。由上层方法
     * {@link #uploadFileAndSubmitTask} 在需要时发送。</p>
     *
     * @param file   上传文件
     * @param userId 上传用户 ID
     * @return 持久化后的文件元数据
     */
    @Override
    public FileInfo uploadFile(MultipartFile file, Long userId) {
        // 1. 校验上传参数
        validateUploadRequest(file, userId);

        // 2. 获取当前激活的存储策略并保存文件
        FileStorageStrategy strategy = strategyFactory.getActiveStrategy();
        FileStorageResult result = strategy.save(file);
        validateStorageResult(result, "upload");

        // 3. 构建文件元数据对象
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileName(result.getFileName());
        fileInfo.setFileSize(result.getFileSize());
        fileInfo.setFileType(result.getFileType());
        fileInfo.setContentType(result.getContentType());
        fileInfo.setStoragePath(result.getStoragePath());
        fileInfo.setStorageType(result.getStorageType());
        fileInfo.setFileMd5(result.getFileMd5());
        fileInfo.setUserId(userId);
        fileInfo.setEditTime(new Date());

        // 4. 持久化文件元数据到数据库。失败时补偿删除已上传对象，避免产生孤立文件。
        try {
            insertMetadata(fileInfo, "upload");
        } catch (RuntimeException e) {
            cleanupUploadedFile(strategy, result, e);
            throw e;
        }

        return fileInfo;
    }

    /**
     * 获取文件对象（用于下载）。
     *
     * <p>返回对象中包含 InputStream，调用方使用完成后必须关闭流。</p>
     *
     * @param fileId 文件 ID
     * @return 文件存储对象（含输入流）
     */
    @Override
    public FileStorageObject downloadObject(Long fileId) {
        // 1. 查询文件元数据并校验存储完整性
        FileInfo fileInfo = getFileInfo(fileId);
        validateStorageMetadata(fileInfo, "download");

        // 2. 从存储层拉取文件对象
        FileStorageStrategy strategy = strategyFactory.getStrategy(fileInfo.getStorageType());
        FileStorageObject object = strategy.getObject(fileInfo.getStoragePath());

        // 3. 校验返回的文件流不为空
        if (object == null || object.getInputStream() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "存储返回的文件对象为空，fileId=" + fileId);
        }
        return object;
    }

    /**
     * 获取文件元信息。
     *
     * @param fileId 文件 ID
     * @return 文件元数据
     * @throws BusinessException 文件不存在时抛出 NOT_FOUND_ERROR
     */
    @Override
    public FileInfo getFileInfo(Long fileId) {
        if (fileId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件 ID 不能为空");
        }
        // 从数据库查询文件元数据
        FileInfo fileInfo;
        try {
            fileInfo = baseMapper.selectById(fileId);
        } catch (RuntimeException e) {
            log.error("查询文件元数据失败，fileId={}", fileId, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "查询文件元数据失败");
        }
        if (fileInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在，fileId=" + fileId);
        }
        return fileInfo;
    }

    /**
     * 创建上传预签名地址，并预注册文件元数据记录。
     *
     * @param request 预签名上传请求
     * @param userId  上传用户 ID
     * @return 预签名上传结果（含上传地址和文件 ID）
     */
    @Override
    public PresignedUploadResult createUploadPresigned(PresignedUploadRequest request, Long userId) {
        // 1. 校验预签名请求参数
        validatePresignedUploadRequest(request, userId);

        // 2. 向存储层申请预签名上传地址
        FileStorageStrategy strategy = strategyFactory.getActiveStrategy();
        PresignedUploadResult result = strategy.createUploadPresigned(request);
        validatePresignedUploadResult(result);

        // 3. 预注册文件元数据记录（文件实际还未上传）
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileName(request.getFileName());
        fileInfo.setContentType(request.getContentType());
        fileInfo.setStoragePath(result.getStoragePath());
        fileInfo.setStorageType(result.getStorageType());
        fileInfo.setUserId(userId);
        fileInfo.setEditTime(new Date());
        insertMetadata(fileInfo, "createUploadPresigned");

        // 4. 将数据库生成的 fileId 回填到预签名结果中
        result.setFileId(fileInfo.getId());
        return result;
    }

    /**
     * 创建下载预签名地址。
     *
     * @param fileId  文件 ID
     * @param expires 预签名有效期，为 null 时默认 15 分钟
     * @return 预签名下载结果（含下载地址）
     */
    @Override
    public PresignedDownloadResult createDownloadPresigned(Long fileId, Duration expires) {
        // 1. 查询文件元数据并校验存储完整性
        FileInfo fileInfo = getFileInfo(fileId);
        validateStorageMetadata(fileInfo, "createDownloadPresigned");

        // 2. 向存储层申请预签名下载地址
        FileStorageStrategy strategy = strategyFactory.getStrategy(fileInfo.getStorageType());
        PresignedDownloadResult result = strategy.createDownloadPresigned(fileInfo.getStoragePath(), expires);

        // 3. 校验返回的下载地址有效
        if (result == null || !StringUtils.hasText(result.getDownloadUrl())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "创建下载预签名地址失败，fileId=" + fileId);
        }
        return result;
    }

    /**
     * 删除文件（逻辑删除 + 存储层删除）。
     *
     * @param fileId 文件 ID
     * @return true 表示删除成功
     */
    @Override
    public boolean deleteFile(Long fileId) {
        // 1. 查询文件元数据并校验存储完整性
        FileInfo fileInfo = getFileInfo(fileId);
        validateStorageMetadata(fileInfo, "delete");

        // 2. 删除数据库中的文件元数据记录
        int deleted;
        try {
            deleted = baseMapper.deleteById(fileId);
        } catch (RuntimeException e) {
            log.error("删除文件元数据失败，fileId={}，storagePath={}",
                    fileId, fileInfo.getStoragePath(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "删除文件元数据失败");
        }
        if (deleted <= 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "删除文件元数据未影响任何行，fileId=" + fileId);
        }

        // 3. 删除存储层中的文件对象
        FileStorageStrategy strategy = strategyFactory.getStrategy(fileInfo.getStorageType());
        strategy.delete(fileInfo.getStoragePath());

        return true;
    }

    /**
     * 确认预签名上传完成，回填文件元数据（不发送消息队列）。
     *
     * <p>客户端通过预签名 URL 上传完成后调用此方法，
     * 回填文件大小与 contentType。</p>
     *
     * @param fileId 文件 ID
     * @return 更新后的文件元数据
     */
    @Override
    public FileInfo confirmUpload(Long fileId) {
        // 1. 查询文件元数据并校验存储完整性
        FileInfo fileInfo = getFileInfo(fileId);
        validateStorageMetadata(fileInfo, "confirmUpload");

        // 2. 从存储层查询文件实际状态（大小、类型）
        FileStorageStrategy strategy = strategyFactory.getStrategy(fileInfo.getStorageType());
        FileStorageStat stat = strategy.stat(fileInfo.getStoragePath());
        validateStorageStat(stat, fileId);

        // 3. 回填文件大小与内容类型
        fileInfo.setFileSize(stat.getSize());
        if (StringUtils.hasText(stat.getContentType())) {
            fileInfo.setContentType(stat.getContentType());
        }
        fileInfo.setEditTime(new Date());

        // 4. 更新数据库中的文件元数据
        updateMetadata(fileInfo, "confirmUpload");

        return fileInfo;
    }

    /**
     * 将暂存文件提升为久存文件。
     *
     * <p>仅在 stage-mode 开启时生效，关闭时直接返回原记录。</p>
     *
     * @param fileId 文件 ID
     * @return 更新后的文件元数据
     */
    @Override
    public FileInfo promoteFile(Long fileId) {
        // 1. 查询文件元数据并校验存储完整性
        FileInfo fileInfo = getFileInfo(fileId);
        validateStorageMetadata(fileInfo, "promote");

        // 2. 调用存储层将暂存路径文件提升为久存路径
        FileStorageStrategy strategy = strategyFactory.getStrategy(fileInfo.getStorageType());
        String newPath = strategy.promote(fileInfo.getStoragePath());
        if (!StringUtils.hasText(newPath)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "存储返回的提升路径为空，fileId=" + fileId);
        }

        // 3. 路径有变化时更新数据库记录的存储路径
        if (!newPath.equals(fileInfo.getStoragePath())) {
            fileInfo.setStoragePath(newPath);
            fileInfo.setEditTime(new Date());
            updateMetadata(fileInfo, "promote");
        }
        return fileInfo;
    }

    /**
     * 上传文件并提交处理任务到消息队列。
     *
     * <p>组合调用 {@link #uploadFile} + 发送 {@code FileProcessMessage}
     * 到 RabbitMQ，触发 ETL 管道。适用于服务端直接上传场景。</p>
     *
     * @param file          上传文件
     * @param userId        上传用户 ID
     * @param promptContent 用户分析需求描述（可选，如"分析各区域季度销售趋势"）
     * @return 持久化后的文件元数据
     */
    @Override
    public FileInfo uploadFileAndSubmitTask(MultipartFile file, Long userId, String promptContent) {
        // 1. 上传文件到存储并持久化元数据
        FileInfo fileInfo = uploadFile(file, userId);
        // 2. 发送 ETL 处理消息到消息队列
        sendFileProcessMessage(fileInfo, promptContent);
        return fileInfo;
    }

    /**
     * 确认预签名上传完成并提交处理任务到消息队列。
     *
     * <p>组合调用 {@link #confirmUpload} + 发送 {@code FileProcessMessage}
     * 到 RabbitMQ，触发 ETL 管道。适用于客户端预签名直传场景。</p>
     *
     * @param fileId        文件 ID
     * @param promptContent 用户分析需求描述（可选，如"分析各区域季度销售趋势"）
     * @return 更新后的文件元数据
     */
    @Override
    public FileInfo confirmUploadAndSubmitTask(Long fileId, String promptContent) {
        // 1. 确认预签名上传并回填元数据
        FileInfo fileInfo = confirmUpload(fileId);
        // 2. 发送 ETL 处理消息到消息队列
        sendFileProcessMessage(fileInfo, promptContent);
        return fileInfo;
    }

    /**
     * 发送文件处理消息到 RabbitMQ。
     *
     * <p>构造 {@link FileProcessMessage} 并通过 {@link RabbitMqService}
     * 发送到消息队列，触发后续 ETL 管道处理。</p>
     *
     * @param fileInfo      文件元数据
     * @param promptContent 用户分析需求描述（可选）
     */
    private void sendFileProcessMessage(FileInfo fileInfo, String promptContent) {
        // 1. 校验文件信息完整性
        validateMessageFileInfo(fileInfo);

        // 2. 构建文件处理消息对象
        FileProcessMessage message = new FileProcessMessage(
                UUID.randomUUID().toString(),
                fileInfo.getId(),
                fileInfo.getStoragePath(),
                fileInfo.getStorageType(),
                fileInfo.getFileName(),
                fileInfo.getUserId(),
                promptContent
        );

        // 3. 发送消息到 RabbitMQ
        try {
            rabbitMqService.sendFileProcessMessage(message);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("提交文件处理消息失败，fileId={}，storagePath={}",
                    fileInfo.getId(), fileInfo.getStoragePath(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "文件已保存，但提交处理任务失败");
        }
    }

    /**
     * 校验上传请求参数。
     *
     * @param file   上传文件，不能为空
     * @param userId 用户 ID，不能为空
     */
    private void validateUploadRequest(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "上传文件不能为空");
        }
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        }
    }

    /**
     * 校验预签名上传请求参数。
     *
     * @param request 预签名上传请求，不能为空且必须包含文件名
     * @param userId  用户 ID，不能为空
     */
    private void validatePresignedUploadRequest(PresignedUploadRequest request, Long userId) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "预签名上传请求不能为空");
        }
        if (!StringUtils.hasText(request.getFileName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件名不能为空");
        }
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        }
    }

    /**
     * 校验存储操作返回结果。
     *
     * @param result    存储操作结果
     * @param operation 操作名称（用于日志）
     */
    private void validateStorageResult(FileStorageResult result, String operation) {
        if (result == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "存储返回结果为空，operation=" + operation);
        }
        if (!StringUtils.hasText(result.getFileName())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "存储结果缺少文件名，operation=" + operation);
        }
        if (!StringUtils.hasText(result.getStoragePath())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "存储结果缺少存储路径，operation=" + operation);
        }
        if (result.getStorageType() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "存储结果缺少存储类型，operation=" + operation);
        }
    }

    /**
     * 校验预签名上传结果。
     *
     * @param result 预签名上传结果，须包含有效的上传地址和存储路径
     */
    private void validatePresignedUploadResult(PresignedUploadResult result) {
        if (result == null || !StringUtils.hasText(result.getUploadUrl())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建上传预签名地址失败");
        }
        if (!StringUtils.hasText(result.getStoragePath())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "预签名上传结果缺少存储路径");
        }
        if (result.getStorageType() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "预签名上传结果缺少存储类型");
        }
    }

    /**
     * 校验文件元数据中存储相关字段的完整性。
     *
     * @param fileInfo  文件元数据
     * @param operation 操作名称（用于日志）
     */
    private void validateStorageMetadata(FileInfo fileInfo, String operation) {
        if (fileInfo == null || fileInfo.getId() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "文件元数据不完整，operation=" + operation);
        }
        if (!StringUtils.hasText(fileInfo.getStoragePath())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "文件存储路径为空，fileId=" + fileInfo.getId());
        }
        if (fileInfo.getStorageType() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "文件存储类型为空，fileId=" + fileInfo.getId());
        }
    }

    /**
     * 校验存储状态信息。
     *
     * @param stat   存储状态
     * @param fileId 文件 ID（用于日志）
     */
    private void validateStorageStat(FileStorageStat stat, Long fileId) {
        if (stat == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "存储状态结果为空，fileId=" + fileId);
        }
        if (stat.getSize() == null || stat.getSize() < 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "存储状态大小无效，fileId=" + fileId);
        }
    }

    /**
     * 校验发送消息所需的文件信息完整性。
     *
     * @param fileInfo 文件元数据，须包含存储路径、类型、文件名和用户 ID
     */
    private void validateMessageFileInfo(FileInfo fileInfo) {
        validateStorageMetadata(fileInfo, "submitTask");
        if (!StringUtils.hasText(fileInfo.getFileName())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "文件名为空，fileId=" + fileInfo.getId());
        }
        if (fileInfo.getUserId() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "文件用户 ID 为空，fileId=" + fileInfo.getId());
        }
    }

    /**
     * 插入文件元数据记录。
     *
     * @param fileInfo  文件元数据
     * @param operation 操作名称（用于日志）
     */
    private void insertMetadata(FileInfo fileInfo, String operation) {
        try {
            int rows = baseMapper.insert(fileInfo);
            if (rows <= 0 || fileInfo.getId() == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "文件元数据插入未成功，operation=" + operation);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("插入文件元数据失败，operation={}，fileName={}，storagePath={}",
                    operation, fileInfo.getFileName(), fileInfo.getStoragePath(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "插入文件元数据失败");
        }
    }

    /**
     * 元数据写入失败时清理已上传对象。
     *
     * <p>清理失败只记录日志，不覆盖原始数据库异常。</p>
     */
    private void cleanupUploadedFile(FileStorageStrategy strategy, FileStorageResult result, RuntimeException cause) {
        if (strategy == null || result == null || !StringUtils.hasText(result.getStoragePath())) {
            return;
        }
        try {
            boolean deleted = strategy.delete(result.getStoragePath());
            if (!deleted) {
                log.warn("文件元数据写入失败后清理存储对象未删除任何文件，storageType={}，storagePath={}",
                        result.getStorageType(), result.getStoragePath(), cause);
            }
        } catch (RuntimeException cleanupException) {
            log.error("文件元数据写入失败后清理存储对象失败，storageType={}，storagePath={}",
                    result.getStorageType(), result.getStoragePath(), cleanupException);
        }
    }

    /**
     * 更新文件元数据记录。
     *
     * @param fileInfo  文件元数据
     * @param operation 操作名称（用于日志）
     */
    private void updateMetadata(FileInfo fileInfo, String operation) {
        try {
            int rows = baseMapper.updateById(fileInfo);
            if (rows <= 0) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "文件元数据更新未影响任何行，operation=" + operation
                                + "，fileId=" + fileInfo.getId());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("更新文件元数据失败，operation={}，fileId={}，storagePath={}",
                    operation, fileInfo.getId(), fileInfo.getStoragePath(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新文件元数据失败");
        }
    }
}

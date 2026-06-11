package com.yuan.user.service.storage.strategy;

import com.yuan.common.ErrorCode;
import com.yuan.exception.BusinessException;
import com.yuan.model.enums.FileStorageType;
import com.yuan.user.config.FileStorageConfig;
import com.yuan.user.service.storage.model.FileStorageObject;
import com.yuan.user.service.storage.model.FileStorageResult;
import com.yuan.user.service.storage.model.FileStorageStat;
import com.yuan.user.service.storage.model.PresignedDownloadResult;
import com.yuan.user.service.storage.model.PresignedUploadRequest;
import com.yuan.user.service.storage.model.PresignedUploadResult;
import com.yuan.user.service.storage.support.FileStorageGuard;
import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SourceObject;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 对象存储策略实现。
 *
 * <p>基于 MinIO Java SDK（{@link MinioClient}）实现文件上传、下载、预签名、
 * 复制与删除等完整生命周期操作。通过 {@code file.storage.minio.enabled=true} 激活。</p>
 */
@Service
@ConditionalOnProperty(prefix = "file.storage.minio", name = "enabled", havingValue = "true")
public class MinioFileStorageStrategy extends AbstractFileStorageStrategy {

    private final MinioClient minioClient;

    private final FileStorageGuard fileStorageGuard;

    /**
     * 构造 MinIO 策略，初始化 MinIO 客户端。
     *
     * @param fileStorageConfig 文件存储配置
     * @param fileStorageGuard  文件存储操作防护门面
     */
    public MinioFileStorageStrategy(FileStorageConfig fileStorageConfig,
                                    FileStorageGuard fileStorageGuard) {
        super(fileStorageConfig);
        this.fileStorageGuard = fileStorageGuard;
        FileStorageConfig.Minio minio = fileStorageConfig.getMinio();
        // 构建 MinIO 客户端（endpoint、accessKey、secretKey 为空时抛出异常）
        this.minioClient = MinioClient.builder()
                .endpoint(required(minio.getEndpoint(), "file.storage.minio.endpoint"))
                .credentials(
                        required(minio.getAccessKey(), "file.storage.minio.access-key"),
                        required(minio.getSecretKey(), "file.storage.minio.secret-key")
                ).build();
    }

    @Override
    public FileStorageType getStorageType() {
        return FileStorageType.MINIO;
    }

    /**
     * 服务端上传文件到 MinIO。
     *
     * @param file 上传文件
     * @return 文件保存结果（含存储路径、MD5 等元数据）
     */
    @Override
    public FileStorageResult save(MultipartFile file) {
        // 1. 校验文件及扩展名
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "上传文件不能为空");
        }
        validateFileExtension(file.getOriginalFilename());

        // 2. 构建对象存储路径
        String originalFilename = cleanFileName(file.getOriginalFilename());
        String fileType = getFileType(originalFilename);
        String savePrefix = resolveSavePathPrefix(fileStorageConfig.getMinio().getPathPrefix());
        String objectName = buildObjectName(savePrefix, originalFilename);

        // 3. 上传文件内容到 MinIO
        return fileStorageGuard.execute("minio.save", () -> {
            // 3a. 确保 bucket 已存在
            ensureBucket();
            // 3b. 计算文件 MD5
            String fileMd5 = computeMd5(file);
            // 3c. 执行上传
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucketName())
                        .object(objectName)
                        .stream(inputStream, file.getSize(), -1L)
                        .contentType(file.getContentType())
                        .build());
            }

            // 4. 构建并返回上传结果
            FileStorageResult result = new FileStorageResult();
            result.setFileName(originalFilename);
            result.setFileSize(file.getSize());
            result.setFileType(fileType);
            result.setContentType(file.getContentType());
            result.setStoragePath(objectName);
            result.setStorageType(getStorageType());
            result.setFileMd5(fileMd5);
            return result;
        });
    }

    /**
     * 从 MinIO 读取文件对象（含输入流）。
     *
     * @param storagePath 存储路径
     * @return 文件对象，包含 InputStream
     */
    @Override
    public FileStorageObject getObject(String storagePath) {
        String objectName = normalizeObjectName(storagePath);
        return fileStorageGuard.execute("minio.getObject", () -> {
            // 1. 确保 bucket 存在并查询文件元信息
            ensureBucket();
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName())
                    .object(objectName)
                    .build());

            // 2. 拉取文件内容流
            InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName())
                    .object(objectName)
                    .build());

            // 3. 封装返回结果
            FileStorageObject object = new FileStorageObject();
            object.setStoragePath(objectName);
            object.setStorageType(getStorageType());
            object.setFileName(Path.of(objectName).getFileName().toString());
            object.setContentType(stat.contentType());
            object.setContentLength(stat.size());
            object.setInputStream(inputStream);
            return object;
        });
    }

    /**
     * 查询 MinIO 中文件的元信息。
     *
     * @param storagePath 存储路径
     * @return 文件元信息（大小、类型、etag、最后修改时间）
     */
    @Override
    public FileStorageStat stat(String storagePath) {
        String objectName = normalizeObjectName(storagePath);
        return fileStorageGuard.execute("minio.stat", () -> {
            // 1. 查询 MinIO 文件状态
            ensureBucket();
            StatObjectResponse response = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName())
                    .object(objectName)
                    .build());

            // 2. 封装状态结果
            FileStorageStat stat = new FileStorageStat();
            stat.setStoragePath(objectName);
            stat.setStorageType(getStorageType());
            stat.setSize(response.size());
            stat.setContentType(response.contentType());
            stat.setEtag(response.etag());
            stat.setLastModified(response.lastModified());
            return stat;
        });
    }

    /**
     * 判断文件在 MinIO 中是否存在。
     *
     * @param storagePath 存储路径
     * @return true 存在，false 不存在
     */
    @Override
    public boolean exists(String storagePath) {
        try {
            // 通过 stat 查询，能查到即存在
            stat(storagePath);
            return true;
        } catch (BusinessException e) {
            // stat 抛出 NOT_FOUND_ERROR 表示文件不存在
            if (e.getCode() == ErrorCode.NOT_FOUND_ERROR.getCode()) {
                return false;
            }
            throw e;
        }
    }

    /**
     * 删除 MinIO 中的文件。
     *
     * @param storagePath 存储路径
     * @return true 表示删除了文件，false 表示文件原本不存在
     */
    @Override
    public boolean delete(String storagePath) {
        String objectName = normalizeObjectName(storagePath);
        return fileStorageGuard.execute("minio.delete", () -> {
            // 1. 文件不存在则直接返回 false
            ensureBucket();
            if (!exists(objectName)) {
                return false;
            }
            // 2. 执行删除
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName())
                    .object(objectName)
                    .build());
            return true;
        });
    }

    /**
     * 创建上传预签名地址（客户端直传）。
     *
     * <p>客户端可以通过该地址直接上传文件到 MinIO，无需经过应用服务器。</p>
     *
     * @param request 预签名上传请求（含文件名、可选前缀和有效期）
     * @return 预签名上传结果（含上传地址、存储路径和过期时间）
     */
    @Override
    public PresignedUploadResult createUploadPresigned(PresignedUploadRequest request) {
        // 1. 校验请求参数及文件扩展名
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "预签名上传请求不能为空");
        }
        validateFileExtension(request.getFileName());

        // 2. 构建对象存储路径
        String basePrefix = resolvePathPrefix(request.getPathPrefix());
        String savePrefix = resolveSavePathPrefix(basePrefix);
        String objectName = buildObjectName(savePrefix, request.getFileName());
        Duration expires = normalizeExpires(request.getExpires());

        // 3. 向 MinIO 申请预签名 PUT 地址
        return fileStorageGuard.execute("minio.presignedUpload", () -> {
            ensureBucket();
            String uploadUrl = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Http.Method.PUT)
                    .bucket(bucketName())
                    .object(objectName)
                    .expiry((int) expires.toSeconds(), TimeUnit.SECONDS)
                    .build());

            // 4. 封装预签名结果
            PresignedUploadResult result = new PresignedUploadResult();
            result.setUploadUrl(uploadUrl);
            result.setStoragePath(objectName);
            result.setStorageType(getStorageType());
            result.setMethod("PUT");
            result.setExpiresAt(Instant.now().plus(expires));
            return result;
        });
    }

    /**
     * 创建下载预签名地址。
     *
     * <p>客户端可以通过该地址直接下载 MinIO 中的文件。</p>
     *
     * @param storagePath 存储路径
     * @param expires     有效期，为 null 时默认 15 分钟
     * @return 预签名下载结果（含下载地址和过期时间）
     */
    @Override
    public PresignedDownloadResult createDownloadPresigned(String storagePath, Duration expires) {
        String objectName = normalizeObjectName(storagePath);
        Duration normalizedExpires = normalizeExpires(expires);

        // 向 MinIO 申请预签名 GET 地址
        return fileStorageGuard.execute("minio.presignedDownload", () -> {
            ensureBucket();
            String downloadUrl = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Http.Method.GET)
                    .bucket(bucketName())
                    .object(objectName)
                    .expiry((int) normalizedExpires.toSeconds(), TimeUnit.SECONDS)
                    .build());

            PresignedDownloadResult result = new PresignedDownloadResult();
            result.setDownloadUrl(downloadUrl);
            result.setStoragePath(objectName);
            result.setStorageType(getStorageType());
            result.setMethod("GET");
            result.setExpiresAt(Instant.now().plus(normalizedExpires));
            return result;
        });
    }

    /**
     * 在 MinIO 中复制文件。
     *
     * @param sourceStoragePath 源存储路径
     * @param targetStoragePath 目标存储路径
     * @return 目标存储路径
     */
    @Override
    public String copy(String sourceStoragePath, String targetStoragePath) {
        String sourceObjectName = normalizeObjectName(sourceStoragePath);
        String targetObjectName = normalizeObjectName(targetStoragePath);

        return fileStorageGuard.execute("minio.copy", () -> {
            // 1. 确保 bucket 存在
            ensureBucket();
            // 2. 执行服务端复制
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(bucketName())
                    .object(targetObjectName)
                    .source(SourceObject.builder()
                            .bucket(bucketName())
                            .object(sourceObjectName)
                            .build())
                    .build());
            return targetObjectName;
        });
    }

    /**
     * 在 MinIO 中移动文件（复制 + 删除源文件）。
     *
     * @param sourceStoragePath 源存储路径
     * @param targetStoragePath 目标存储路径
     * @return 目标存储路径
     */
    @Override
    public String move(String sourceStoragePath, String targetStoragePath) {
        // 先复制到目标路径，再删除源文件
        String targetObjectName = copy(sourceStoragePath, targetStoragePath);
        delete(sourceStoragePath);
        return targetObjectName;
    }

    /**
     * 确保 MinIO bucket 已存在，不存在则自动创建。
     */
    private void ensureBucket() {
        fileStorageGuard.execute("minio.ensureBucket", () -> {
            // 校验存储类型已启用
            fileStorageConfig.validateEnabled(getStorageType());
            // 检查 bucket 是否存在
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucketName())
                    .build());
            // 不存在则创建
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(bucketName())
                        .build());
            }
            return null;
        });
    }

    /**
     * 获取配置的 bucket 名称，为空时直接抛出异常。
     */
    private String bucketName() {
        return required(fileStorageConfig.getMinio().getBucketName(), "file.storage.minio.bucket-name");
    }

    /**
     * 解析路径前缀：优先使用请求中的前缀，未指定时使用配置的默认前缀。
     *
     * @param requestPathPrefix 请求中携带的前缀，可为 null
     * @return 解析后的路径前缀
     */
    private String resolvePathPrefix(String requestPathPrefix) {
        if (requestPathPrefix != null && !requestPathPrefix.isBlank()) {
            return requestPathPrefix;
        }
        return fileStorageConfig.getMinio().getPathPrefix();
    }
}

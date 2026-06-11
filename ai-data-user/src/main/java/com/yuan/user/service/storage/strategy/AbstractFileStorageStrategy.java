package com.yuan.user.service.storage.strategy;

import com.yuan.common.ErrorCode;
import com.yuan.exception.BusinessException;
import com.yuan.model.enums.FileStorageType;
import com.yuan.user.config.FileStorageConfig;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 文件存储策略抽象基类。
 *
 * <p>封装不同存储实现的共性逻辑，包括对象路径生成、路径安全性校验、
 * 文件名处理、预签名有效期规范化等工具方法。</p>
 */
public abstract class AbstractFileStorageStrategy implements FileStorageStrategy {

    /** 日期路径格式，用于生成按日期分层的对象存储路径。 */
    protected static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 预签名地址默认有效期：15 分钟。 */
    protected static final Duration DEFAULT_PRESIGNED_EXPIRES = Duration.ofMinutes(15);

    /** 预签名地址最大有效期：7 天。超过该值的有效期会被截断。 */
    protected static final Duration MAX_PRESIGNED_EXPIRES = Duration.ofDays(7);

    /** 文件存储强类型配置，持有各存储实现的 endpoint、bucket 等连接参数。 */
    protected final FileStorageConfig fileStorageConfig;

    protected AbstractFileStorageStrategy(FileStorageConfig fileStorageConfig) {
        this.fileStorageConfig = fileStorageConfig;
    }

    /**
     * 当前策略支持的存储类型。
     */
    @Override
    public abstract FileStorageType getStorageType();

    /**
     * 生成统一对象路径：{pathPrefix}/{yyyy/MM/dd}/{uuid}.{ext}。
     *
     * @param pathPrefix 路径前缀（如 uploads）
     * @param fileName   原始文件名，用于提取扩展名
     * @return 对象存储路径
     */
    protected String buildObjectName(String pathPrefix, String fileName) {
        String datePath = LocalDate.now().format(DATE_PATH_FORMATTER);
        String fileType = getFileType(cleanFileName(fileName));
        String suffix = fileType == null || fileType.isBlank() ? "" : "." + fileType;
        String prefix = pathPrefix == null || pathPrefix.isBlank() ? "" : trimSlash(pathPrefix) + "/";
        return prefix + datePath + "/" + UUID.randomUUID() + suffix;
    }

    /**
     * 归一化对象路径，禁止 {@code ../} 等路径穿越写法。
     *
     * @param storagePath 原始存储路径
     * @return 归一化后的路径
     */
    protected String normalizeObjectName(String storagePath) {
        String value = required(storagePath, "storagePath").replace("\\", "/");
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (value.contains("../") || value.equals("..") || value.endsWith("/..")) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "非法文件路径: " + storagePath);
        }
        return value;
    }

    /**
     * 清理文件名，只保留文件名部分，去除目录信息。
     */
    protected String cleanFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "unknown";
        }
        return Path.of(originalFilename).getFileName().toString();
    }

    /**
     * 从文件名中提取扩展名（小写）。
     *
     * @param fileName 文件名
     * @return 扩展名，无扩展名时返回 null
     */
    protected String getFileType(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dotIndex + 1).toLowerCase();
    }

    /**
     * 校验文件扩展名是否在白名单范围内。
     *
     * <p>白名单为空时不校验，允许所有类型上传。该配置由 {@code file.storage.allowed-file-types} 控制，</p>
     *
     * @param fileName 原始文件名
     */
    protected void validateFileExtension(String fileName) {
        List<String> allowedTypes = fileStorageConfig.getAllowedFileTypes();
        // 白名单为空表示不限制
        if (allowedTypes == null || allowedTypes.isEmpty()) {
            return;
        }
        if (fileName == null || fileName.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件名不能为空");
        }
        String ext = getFileType(fileName);
        if (ext == null || !allowedTypes.contains(ext)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "不支持的文件类型，允许的类型: " + String.join(", ", allowedTypes));
        }
    }

    /**
     * 根据 stage-mode 配置决定 save 时使用的路径前缀。
     *
     * <p>stage-mode 开启时返回 {@code stagePathPrefix}，写入暂存目录；
     * 关闭时直接返回 {@code permanentPrefix}，写入久存目录。</p>
     *
     * @param permanentPrefix 久存路径前缀（来自具体的存储配置）
     * @return 实际使用的路径前缀
     */
    protected String resolveSavePathPrefix(String permanentPrefix) {
        if (fileStorageConfig.isStageMode()) {
            return fileStorageConfig.getStagePrefix();
        }
        return permanentPrefix;
    }

    /**
     * 将暂存文件提升为久存文件。
     *
     * <p>从暂存路径提取文件名，重新生成久存路径，再通过子类的 {@link #move(String, String)}
     * 完成转移。stage-mode 关闭时直接返回原路径。</p>
     *
     * @param storagePath 暂存路径，如 staging/2026/06/10/abc.xlsx
     * @return 久存路径
     */
    @Override
    public String promote(String storagePath) {
        // stage-mode 关闭时，promote 为无操作，直接返回原路径
        if (!fileStorageConfig.isStageMode()) {
            return storagePath;
        }
        String targetPath = buildPromoteTarget(storagePath);
        return move(storagePath, targetPath);
    }

    /**
     * 提取暂存路径中的文件名，使用久存前缀重新生成新路径。
     */
    private String buildPromoteTarget(String stagePath) {
        // 1. 从暂存路径中提取纯文件名
        String fileName = Path.of(stagePath).getFileName().toString();
        // 2. 使用久存路径前缀（即当前存储配置的 pathPrefix）生成新路径
        String permanentPrefix = fileStorageConfig.getConfig(getStorageType()).getPathPrefix();
        // 3. 利用 buildObjectName 生成带唯一 ID 的久存路径
        return buildObjectName(permanentPrefix, fileName);
    }

    /**
     * 去除首尾斜杠。
     */
    protected String trimSlash(String value) {
        String result = value;
        while (result.startsWith("/") || result.startsWith("\\")) {
            result = result.substring(1);
        }
        while (result.endsWith("/") || result.endsWith("\\")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * 去除尾部斜杠。
     */
    protected String trimRightSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * 对路径进行 URL 编码，保留 {@code /} 分隔符。
     */
    protected String encodePath(String value) {
        if (value == null) {
            return null;
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%2F", "/");
    }

    /**
     * 校验配置值非空，为空时抛出异常。
     *
     * @param value      配置值
     * @param configPath 配置路径，用于错误提示
     * @return 非空值
     */
    protected String required(String value, String configPath) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件存储配置缺少必填项: " + configPath);
        }
        return value;
    }

    /**
     * 规范化预签名有效期。null / 负数 / 零值默认 15 分钟，最长不超过 7 天。
     *
     * @param expires 原始有效期
     * @return 规范化后的有效期
     */
    protected Duration normalizeExpires(Duration expires) {
        Duration value = expires == null || expires.isNegative() || expires.isZero()
                ? DEFAULT_PRESIGNED_EXPIRES
                : expires;
        if (value.compareTo(MAX_PRESIGNED_EXPIRES) > 0) {
            return MAX_PRESIGNED_EXPIRES;
        }
        return value;
    }

    /**
     * 计算上传文件的 MD5 值。
     *
     * @param file 上传文件
     * @return MD5 十六进制字符串
     */
    protected String computeMd5(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return cn.hutool.crypto.digest.DigestUtil.md5Hex(inputStream);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "计算文件 MD5 失败");
        }
    }
}

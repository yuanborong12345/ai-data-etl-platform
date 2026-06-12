package com.yuan.user.service.storage;

import com.yuan.common.ErrorCode;
import com.yuan.exception.BusinessException;
import com.yuan.model.enums.FileStorageType;
import com.yuan.user.config.FileStorageConfig;
import com.yuan.user.service.storage.strategy.FileStorageStrategy;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 文件存储策略工厂。
 *
 * <p>该工厂只收集已通过 @ConditionalOnProperty 创建的策略 Bean。
 * 如果某个存储类型未启用，对应策略不会进入 strategyMap。</p>
 */
@Component
public class FileStorageStrategyFactory {

    private final Map<FileStorageType, FileStorageStrategy> strategyMap = new EnumMap<>(FileStorageType.class);
    private final FileStorageConfig fileStorageConfig;

    /**
     * 注册所有已启用的文件存储策略，并在启动阶段校验当前激活策略可用。
     *
     * @param strategies        Spring 容器中已创建的文件存储策略 Bean
     * @param fileStorageConfig 文件存储配置
     */
    public FileStorageStrategyFactory(List<FileStorageStrategy> strategies, FileStorageConfig fileStorageConfig) {
        this.fileStorageConfig = fileStorageConfig;
        for (FileStorageStrategy strategy : strategies) {
            FileStorageStrategy exists = strategyMap.put(strategy.getStorageType(), strategy);
            if (exists != null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "重复的文件存储策略: " + strategy.getStorageType().getCode());
            }
        }
        validateActiveStrategy();
    }

    /**
     * 根据存储类型获取策略。
     *
     * @param storageType 文件存储类型
     * @return 文件存储策略
     */
    public FileStorageStrategy getStrategy(FileStorageType storageType) {
        if (storageType == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件存储类型不能为空");
        }
        FileStorageStrategy strategy = strategyMap.get(storageType);
        if (strategy == null) {
            fileStorageConfig.validateEnabled(storageType);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "文件存储策略未创建，请检查配置: file.storage." + storageType.getCode() + ".enabled");
        }
        return strategy;
    }

    /**
     * 获取当前激活的存储策略。
     *
     * @return 当前激活的文件存储策略
     */
    public FileStorageStrategy getActiveStrategy() {
        FileStorageType activeType = fileStorageConfig.getActiveType();
        if (activeType == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前激活的文件存储类型不能为空，请检查配置: file.storage.active-type");
        }
        return getStrategy(activeType);
    }

    /**
     * 校验当前激活的存储策略是否已经创建。
     */
    private void validateActiveStrategy() {
        FileStorageType activeType = fileStorageConfig.getActiveType();
        fileStorageConfig.validateEnabled(activeType);
        if (!strategyMap.containsKey(activeType)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "当前激活的文件存储策略未创建，请检查配置: file.storage." + activeType.getCode() + ".enabled");
        }
    }
}

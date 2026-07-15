package com.yuan.processor.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yuan.model.entity.TaskInfo;
import com.yuan.processor.mapper.TaskInfoMapper;
import com.yuan.processor.service.TemplateDataParser.ParseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * 解析结果持久化服务。
 *
 * <p>行数据的批量 insert 已在解析阶段由 {@link ParseBatchWriter} 异步完成，
 * 此处仅更新 TaskInfo 状态和解析时间戳。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParseResultService {

    private final TaskInfoMapper taskInfoMapper;

    /**
     * 更新任务解析完成状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(String taskId, ParseResult parseResult) {
        TaskInfo update = new TaskInfo();
        update.setParseEndTime(new Date());
        taskInfoMapper.update(update,
                new LambdaUpdateWrapper<TaskInfo>()
                        .eq(TaskInfo::getTaskId, taskId));
        log.info("任务解析状态已更新: taskId={}, totalRows={}, validRows={}, errorRows={}",
                taskId, parseResult.totalRows(), parseResult.validRows(), parseResult.errorRows());
    }
}

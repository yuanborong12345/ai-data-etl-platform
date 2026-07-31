package com.yuan.processor.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yuan.model.entity.TaskInfo;
import com.yuan.model.enums.TaskStatus;
import com.yuan.processor.mapper.ParseDataMapper;
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
 * 行数据的批量 insert 已在解析阶段由 {@link ParseBatchWriter} 异步完成，
 * 此处仅更新 TaskInfo 状态和解析时间戳。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParseResultService {

    private final TaskInfoMapper taskInfoMapper;
    private final ParseDataMapper parseDataMapper;

    @Transactional(rollbackFor = Exception.class)
    public void save(String taskId, ParseResult parseResult) {
        TaskInfo update = new TaskInfo();
        update.setStatus(TaskStatus.PARSE_SUCCESS);
        update.setParseEndTime(new Date());
        taskInfoMapper.update(update,
                new LambdaUpdateWrapper<TaskInfo>()
                        .eq(TaskInfo::getTaskId, taskId));
        log.info("任务解析成功: taskId={}, totalRows={}, validRows={}, errorRows={}",
                taskId, parseResult.totalRows(), parseResult.validRows(), parseResult.errorRows());
    }

    @Transactional(rollbackFor = Exception.class)
    public void markFailed(String taskId, String errorMsg) {
        int cleaned = parseDataMapper.deleteByTaskId(taskId);
        log.info("清理部分落库数据: taskId={}, cleanedRows={}", taskId, cleaned);

        TaskInfo update = new TaskInfo();
        update.setStatus(TaskStatus.PARSE_FAILED);
        update.setErrorMsg(errorMsg);
        update.setParseEndTime(new Date());
        taskInfoMapper.update(update,
                new LambdaUpdateWrapper<TaskInfo>()
                        .eq(TaskInfo::getTaskId, taskId));
        log.info("任务解析失败: taskId={}, errorMsg={}", taskId, errorMsg);
    }
}

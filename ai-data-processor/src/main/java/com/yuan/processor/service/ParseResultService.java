package com.yuan.processor.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuan.common.ErrorCode;
import com.yuan.exception.BusinessException;
import com.yuan.model.entity.ParseData;
import com.yuan.model.entity.TaskInfo;
import com.yuan.processor.mapper.ParseDataMapper;
import com.yuan.processor.mapper.TaskInfoMapper;
import com.yuan.processor.service.TemplateDataParser.ParseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 解析结果持久化服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParseResultService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ParseDataMapper parseDataMapper;
    private final TaskInfoMapper taskInfoMapper;

    /**
     * 批量保存解析结果并更新任务状态为"解析完成"。
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(String taskId, ParseResult parseResult) {
        // 1. 批量插入解析数据
        List<String> errors = parseResult.errors();
        List<ParseData> dataList = new ArrayList<>(parseResult.rows().size());

        for (int i = 0; i < parseResult.rows().size(); i++) {
            ParseData pd = new ParseData();
            pd.setTaskId(taskId);
            pd.setRowIndex(i);
            pd.setRowData(toJson(parseResult.rows().get(i)));
            pd.setIsValid(1);
            pd.setCreateTime(new Date());
            dataList.add(pd);
        }
        for (ParseData pd : dataList) {
            parseDataMapper.insert(pd);
        }
        log.info("解析数据已存储: taskId={}, rows={}", taskId, dataList.size());

        // 2. 更新任务状态：标记解析完成
        TaskInfo update = new TaskInfo();
        update.setParseEndTime(new Date());
        taskInfoMapper.update(update,
                new LambdaUpdateWrapper<TaskInfo>()
                        .eq(TaskInfo::getTaskId, taskId));
        log.info("任务解析状态已更新: taskId={}", taskId);
    }

    private String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "JSON 序列化失败");
        }
    }
}

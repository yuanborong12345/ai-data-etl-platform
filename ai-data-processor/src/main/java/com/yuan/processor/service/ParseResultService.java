package com.yuan.processor.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析结果持久化服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParseResultService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final ParseDataMapper parseDataMapper;
    private final TaskInfoMapper taskInfoMapper;

    /**
     * 批量保存解析结果并更新任务状态为"解析完成"。
     *
     * 有错误的行不序列化行数据（残缺 Map 会触发 Jackson 异常），
     * 只记录错误信息到 {@code validationError} 字段。
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(String taskId, ParseResult parseResult) {
        // 0. 将错误列表转成行号 → 错误信息 的映射
        Map<Integer, List<String>> errorMap = buildErrorMap(parseResult.errors());

        // 1. 遍历行，区分有效行和错误行
        List<ParseData> dataList = new ArrayList<>(parseResult.rows().size());
        int insertedValid = 0;
        int insertedError = 0;

        for (int i = 0; i < parseResult.rows().size(); i++) {
            ParseData pd = new ParseData();
            pd.setTaskId(taskId);
            pd.setRowIndex(i);
            pd.setCreateTime(new Date());

            List<String> rowErrors = errorMap.get(i);
            if (rowErrors != null && !rowErrors.isEmpty()) {
                // 错误行：不序列化残缺 Map，只存错误描述
                pd.setRowData("{\"status\":\"error\"}");
                pd.setIsValid(0);
                pd.setValidationError(String.join("; ", rowErrors));
                insertedError++;
            } else {
                // 有效行：正常 JSON 序列化
                pd.setRowData(toJson(parseResult.rows().get(i)));
                pd.setIsValid(1);
                insertedValid++;
            }
            dataList.add(pd);
        }

        // 没有一条有效数据 → 不写库，仅记录日志
        if (insertedValid == 0) {
            log.warn("解析结果全部无效，跳过入库: taskId={}, totalRows={}, errorRows={}",
                    taskId, parseResult.rows().size(), insertedError);
            return;
        }

        for (ParseData pd : dataList) {
            parseDataMapper.insert(pd);
        }
        log.info("解析数据已存储: taskId={}, totalRows={}, valid={}, error={}",
                taskId, dataList.size(), insertedValid, insertedError);

        // 2. 更新任务状态：标记解析完成
        TaskInfo update = new TaskInfo();
        update.setParseEndTime(new Date());
        taskInfoMapper.update(update,
                new LambdaUpdateWrapper<TaskInfo>()
                        .eq(TaskInfo::getTaskId, taskId));
        log.info("任务解析状态已更新: taskId={}", taskId);
    }

    /**
     * 将错误列表 {@code ["第X行: [字段] 为必填字段", ...]} 解析为
     * {@code Map<行号(0-based), List<错误信息>>}。
     */
    private Map<Integer, List<String>> buildErrorMap(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return Collections.emptyMap();
        }
        Pattern pattern = Pattern.compile("^第(\\d+)行");
        Map<Integer, List<String>> map = new LinkedHashMap<>();
        for (String err : errors) {
            Matcher m = pattern.matcher(err);
            if (m.find()) {
                int row = Integer.parseInt(m.group(1)) - 2; // 转 0-based
                map.computeIfAbsent(row, k -> new ArrayList<>()).add(err);
            }
        }
        return map;
    }

    private String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "JSON 序列化失败");
        }
    }
}

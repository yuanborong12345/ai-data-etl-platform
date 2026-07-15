package com.yuan.processor.service;

import cn.hutool.core.io.FileTypeUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yuan.common.ErrorCode;
import com.yuan.exception.BusinessException;
import com.yuan.model.dto.template.TemplateSchemaDTO;
import com.yuan.model.entity.ParseData;
import com.yuan.model.vo.TemplateInfoVO;
import com.yuan.processor.service.ParseBatchWriter.BatchSession;
import com.yuan.serviceclient.feign.TemplateFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模板驱动的文件解析器。
 *
 * 流式读取 + 固定大小缓冲区 + 线程池异步分批落库。
 * 主线程负责 EasyExcel 流式解析，线程池负责 DB 写入，两者并行。
 * CallerRunsPolicy 在队列满时让主线程直接执行 insert，天然背压。
 */
@Slf4j
@Service
public class TemplateDataParser {

    private static final int BATCH_SIZE = 500;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final TemplateFeignClient templateFeignClient;
    private final ParseBatchWriter batchWriter;

    public TemplateDataParser(TemplateFeignClient templateFeignClient,
                              ParseBatchWriter batchWriter) {
        this.templateFeignClient = templateFeignClient;
        this.batchWriter = batchWriter;
    }

    /**
     * 按模板解析文件。
     *
     * @param inputStream 文件输入流
     * @param templateId  模板 ID
     * @param fileName    原始文件名（用于类型检测）
     * @param taskId      任务 ID（写入 ParseData 时关联）
     * @return 解析结果（仅含元数据，不含全量行数据）
     */
    public ParseResult parse(InputStream inputStream, Long templateId, String fileName, String taskId) {
        TemplateInfoVO template = templateFeignClient.getTemplateById(templateId).getData();
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "模板不存在: " + templateId);
        }
        List<TemplateSchemaDTO> schemaList = parseSchema(template.getTemplateSchema());
        schemaList.sort(Comparator.comparing(TemplateSchemaDTO::getColumnIndex));
        log.info("模板 [{}] 加载完成，共 {} 列定义", template.getTemplateName(), schemaList.size());

        byte[] content = getBytesByInputStream(inputStream);
        log.info("文件 {} 大小: {} bytes", fileName, content.length);
        if (content.length > 0 && content[0] == '{') {
            String body = new String(content, StandardCharsets.UTF_8);
            log.error("文件下载返回了 JSON 而非文件内容: {}", body);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件下载失败，服务端返回了错误响应");
        }

        return parseFileByFileType(content, schemaList, fileName, taskId);
    }

    private static byte[] getBytesByInputStream(InputStream inputStream) {
        try {
            return inputStream.readAllBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取文件内容失败: " + e.getMessage());
        }
    }

    private ParseResult parseFileByFileType(byte[] content, List<TemplateSchemaDTO> schemaList,
                                             String fileName, String taskId) {
        String fileType = FileTypeUtil.getType(new ByteArrayInputStream(content), true);
        log.info("文件类型检测结果: {}", fileType);

        if ("xlsx".equals(fileType) || "xls".equals(fileType)
                || ("zip".equals(fileType) && fileName.toLowerCase().endsWith(".xlsx"))) {
            return parseExcel(new ByteArrayInputStream(content), schemaList, taskId);
        }

        if ("zip".equals(fileType)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "请上传标准的 Excel 文件，勿上传压缩包");
        }

        if ("csv".equals(fileType) || "txt".equals(fileType) || fileType == null) {
            return parseCsv(new ByteArrayInputStream(content), schemaList, taskId);
        }

        throw new BusinessException(ErrorCode.PARAMS_ERROR,
                "不支持的文件格式，仅支持 .xlsx / .xls / .csv: " + fileName);
    }

    /* ======================== Excel 解析 ======================== */

    private ParseResult parseExcel(InputStream inputStream, List<TemplateSchemaDTO> schemaList,
                                    String taskId) {
        BatchSession session = batchWriter.newSession();
        AtomicInteger errorCount = new AtomicInteger(0);

        EasyExcel.read(inputStream, new AnalysisEventListener<LinkedHashMap<Integer, String>>() {
            private final List<ParseData> buffer = new ArrayList<>(BATCH_SIZE);

            @Override
            public void invoke(LinkedHashMap<Integer, String> rowData, AnalysisContext context) {
                Integer rowIndex = context.readRowHolder().getRowIndex();
                ParseData pd = buildParseData(rowData, schemaList, rowIndex, taskId, errorCount);
                buffer.add(pd);

                if (buffer.size() >= BATCH_SIZE) {
                    flushBuffer();
                }
            }

            private void flushBuffer() {
                if (buffer.isEmpty()) return;
                List<ParseData> snapshot = new ArrayList<>(buffer);
                buffer.clear();
                session.submitBatch(snapshot);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                flushBuffer();
                session.awaitCompletion();
                log.info("Excel 读取完成，共提交 {} 行", session.getSubmittedCount());
            }
        }).sheet().doRead();

        int submitted = session.getSubmittedCount();
        int err = errorCount.get();
        return new ParseResult(schemaList, submitted - err, err, submitted);
    }

    /* ======================== CSV 解析 ======================== */

    private ParseResult parseCsv(InputStream inputStream, List<TemplateSchemaDTO> schemaList,
                                  String taskId) {
        BatchSession session = batchWriter.newSession();
        AtomicInteger errorCount = new AtomicInteger(0);

        CSVFormat format = CSVFormat.RFC4180
                .withIgnoreSurroundingSpaces(true)
                .withIgnoreEmptyLines(true)
                .withTrim(true);

        List<ParseData> buffer = new ArrayList<>(BATCH_SIZE);

        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {

            int rowIndex = 0;
            for (CSVRecord record : parser) {
                LinkedHashMap<Integer, String> rowData = new LinkedHashMap<>();
                for (int i = 0; i < record.size(); i++) {
                    rowData.put(i, record.get(i));
                }
                ParseData pd = buildParseData(rowData, schemaList, rowIndex, taskId, errorCount);
                buffer.add(pd);
                rowIndex++;

                if (buffer.size() >= BATCH_SIZE) {
                    List<ParseData> snapshot = new ArrayList<>(buffer);
                    buffer.clear();
                    session.submitBatch(snapshot);
                }
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "CSV 解析失败: " + e.getMessage());
        }

        // flush remaining
        if (!buffer.isEmpty()) {
            session.submitBatch(new ArrayList<>(buffer));
            buffer.clear();
        }
        session.awaitCompletion();

        int submitted = session.getSubmittedCount();
        int err = errorCount.get();
        log.info("CSV 读取完成，共提交 {} 行", submitted);
        return new ParseResult(schemaList, submitted - err, err, submitted);
    }

    /* ======================== 单行处理 ======================== */

    /**
     * 将一行原始数据转换为 ParseData，同时完成：
     * - 必填校验 → isError=true 时设 isValid=0 + validationError
     * - 类型转换 → 有效行序列化 rowData JSON
     */
    private ParseData buildParseData(LinkedHashMap<Integer, String> rowData,
                                      List<TemplateSchemaDTO> schemaList,
                                      Integer rowIndex,
                                      String taskId,
                                      AtomicInteger errorCount) {
        ParseData pd = new ParseData();
        pd.setTaskId(taskId);
        pd.setRowIndex(rowIndex);
        pd.setCreateTime(new Date());

        Map<String, Object> row = new LinkedHashMap<>();
        List<String> rowErrors = new ArrayList<>();

        for (TemplateSchemaDTO col : schemaList) {
            String rawValue = rowData.getOrDefault(col.getColumnIndex(), "");

            if (Boolean.TRUE.equals(col.getRequired())
                    && (rawValue == null || rawValue.isBlank())) {
                rowErrors.add(String.format("[%s] 为必填字段", col.getHeaderName()));
                continue;
            }

            Object converted = convertType(rawValue, col.getDataType(), col.getHeaderName(), rowIndex);
            row.put(col.getFieldKey(), converted);
        }

        if (!rowErrors.isEmpty()) {
            pd.setRowData("{\"status\":\"error\"}");
            pd.setIsValid(0);
            pd.setValidationError(String.join("; ", rowErrors));
            errorCount.incrementAndGet();
        } else {
            pd.setRowData(toJson(row));
            pd.setIsValid(1);
        }

        return pd;
    }

    /* ======================== 工具方法 ======================== */

    private List<TemplateSchemaDTO> parseSchema(String templateSchema) {
        try {
            return OBJECT_MAPPER.readValue(templateSchema,
                    new TypeReference<List<TemplateSchemaDTO>>() {});
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "模板 schema 解析失败: " + e.getMessage());
        }
    }

    private Object convertType(String rawValue, String dataType, String headerName, Integer rowIndex) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String type = dataType != null ? dataType.toLowerCase() : "string";
        try {
            return switch (type) {
                case "number", "decimal", "bigdecimal" -> new BigDecimal(rawValue.trim());
                case "integer", "int", "long" -> Long.parseLong(rawValue.trim());
                case "boolean", "bool" -> Boolean.parseBoolean(rawValue.trim());
                case "date" -> LocalDate.parse(rawValue.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
                case "datetime", "timestamp" ->
                        LocalDateTime.parse(rawValue.trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                default -> rawValue.trim();
            };
        } catch (Exception e) {
            log.warn("第{}行: [{}] 类型转换失败, rawValue={}, dataType={}",
                    rowIndex + 1, headerName, rawValue, type);
            return rawValue.trim();
        }
    }

    private static String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "JSON 序列化失败");
        }
    }

    /* ======================== 解析结果 ======================== */

    public record ParseResult(
            List<TemplateSchemaDTO> schema,
            int validRows,
            int errorRows,
            int totalRows
    ) {}
}

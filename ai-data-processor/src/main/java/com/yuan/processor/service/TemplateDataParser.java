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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
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
    private static final int MARK_LIMIT = 16384;
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
     */
    public ParseResult parse(InputStream inputStream, Long templateId, String fileName, String taskId) throws IOException {
        TemplateInfoVO template = templateFeignClient.getTemplateById(templateId).getData();
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "模板不存在: " + templateId);
        }
        List<TemplateSchemaDTO> schemaList = parseSchema(template.getTemplateSchema());
        schemaList.sort(Comparator.comparing(TemplateSchemaDTO::getColumnIndex));
        log.info("模板 [{}] 加载完成，共 {} 列定义", template.getTemplateName(), schemaList.size());

        // 确保流支持 mark/reset，仅读文件头做类型检测，不读取全量内容
        InputStream is = inputStream.markSupported()
                ? inputStream : new BufferedInputStream(inputStream, MARK_LIMIT);
        is.mark(MARK_LIMIT);

        byte[] head = new byte[512];
        int headLen = is.read(head);
        if (headLen <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件内容为空");
        }
        if (head[0] == '{') {
            String body = new String(head, 0, headLen, StandardCharsets.UTF_8);
            log.error("文件下载返回了 JSON 而非文件内容: {}", body);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件下载失败，服务端返回了错误响应");
        }
        is.reset();
        is.mark(MARK_LIMIT); // 重新标记以扩展 readlimit，防止后续 reset 失效

        String fileType = FileTypeUtil.getType(is);
        log.info("文件类型检测结果: {}, fileName={}", fileType, fileName);
        is.reset();

        return parseFileByFileType(is, schemaList, fileName, taskId, fileType);
    }

    private ParseResult parseFileByFileType(InputStream inputStream, List<TemplateSchemaDTO> schemaList,
                                             String fileName, String taskId, String fileType) {
        if ("xlsx".equals(fileType) || "xls".equals(fileType)
                || ("zip".equals(fileType) && fileName.toLowerCase().endsWith(".xlsx"))) {
            return parseExcel(inputStream, schemaList, taskId);
        }
        if ("zip".equals(fileType)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请上传标准的 Excel 文件，勿上传压缩包");
        }
        if ("csv".equals(fileType) || "txt".equals(fileType) || fileType == null) {
            return parseCsv(inputStream, schemaList, taskId);
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR,
                "不支持的文件格式，仅支持 .xlsx / .xls / .csv: " + fileName);
    }

    /* ======================== Excel ======================== */

    private ParseResult parseExcel(InputStream inputStream, List<TemplateSchemaDTO> schemaList,
                                    String taskId) {
        BatchSession session = batchWriter.newSession();
        AtomicInteger errorCount = new AtomicInteger(0);
        try {
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
                }
            }).sheet().doRead();

            session.awaitCompletion();
            log.info("Excel 读取完成，共提交 {} 行", session.getSubmittedCount());
        } finally {
            session.ensureCompleted();
        }

        int submitted = session.getSubmittedCount();
        int err = errorCount.get();
        return new ParseResult(schemaList, submitted - err, err, submitted);
    }

    /* ======================== CSV ======================== */

    private ParseResult parseCsv(InputStream inputStream, List<TemplateSchemaDTO> schemaList,
                                  String taskId) {
        BatchSession session = batchWriter.newSession();
        AtomicInteger errorCount = new AtomicInteger(0);
        try {
            CSVFormat format = CSVFormat.RFC4180
                    .withIgnoreSurroundingSpaces(true)
                    .withIgnoreEmptyLines(true)
                    .withTrim(true);

            List<ParseData> buffer = new ArrayList<>(BATCH_SIZE);

            // 处理 Windows Excel 导出的 UTF-8 BOM
            PushbackInputStream pbIn = new PushbackInputStream(inputStream, 3);
            byte[] bom = new byte[3];
            int bomLen = pbIn.read(bom);
            if (bomLen >= 3 && (bom[0] & 0xFF) == 0xEF && (bom[1] & 0xFF) == 0xBB && (bom[2] & 0xFF) == 0xBF) {
                log.info("CSV 文件包含 UTF-8 BOM，已自动跳过");
            } else if (bomLen > 0) {
                pbIn.unread(bom, 0, bomLen);
            }

            try (Reader reader = new InputStreamReader(pbIn, StandardCharsets.UTF_8);
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
            }

            if (!buffer.isEmpty()) {
                session.submitBatch(new ArrayList<>(buffer));
                buffer.clear();
            }

            session.awaitCompletion();
            log.info("CSV 读取完成，共提交 {} 行", session.getSubmittedCount());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "CSV 解析失败: " + e.getMessage());
        } finally {
            session.ensureCompleted();
        }

        int submitted = session.getSubmittedCount();
        int err = errorCount.get();
        return new ParseResult(schemaList, submitted - err, err, submitted);
    }

    /* ======================== 单行处理 ======================== */

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
            try {
                Object converted = convertType(rawValue, col.getDataType());
                row.put(col.getFieldKey(), converted);
            } catch (Exception e) {
                rowErrors.add(String.format("[%s] 类型转换失败: %s (值=%s)",
                        col.getHeaderName(), e.getMessage(), rawValue));
            }
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

    private Object convertType(String rawValue, String dataType) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String type = dataType != null ? dataType.toLowerCase() : "string";
        return switch (type) {
            case "number", "decimal", "bigdecimal" -> new BigDecimal(rawValue.trim());
            case "integer", "int", "long" -> Long.parseLong(rawValue.trim());
            case "boolean", "bool" -> Boolean.parseBoolean(rawValue.trim());
            case "date" -> LocalDate.parse(rawValue.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            case "datetime", "timestamp" ->
                    LocalDateTime.parse(rawValue.trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            default -> rawValue.trim();
        };
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

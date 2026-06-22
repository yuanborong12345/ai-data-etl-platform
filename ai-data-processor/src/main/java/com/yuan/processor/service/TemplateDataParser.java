package com.yuan.processor.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuan.common.ErrorCode;
import com.yuan.exception.BusinessException;
import com.yuan.model.dto.template.TemplateSchemaDTO;
import com.yuan.model.vo.TemplateInfoVO;
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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模板驱动的文件解析器。
 *
 * <p>根据模板 schema 按列读取 Excel，校验数据并输出结构化结果。</p>
 */
@Slf4j
@Service
public class TemplateDataParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TemplateFeignClient templateFeignClient;

    public TemplateDataParser(TemplateFeignClient templateFeignClient) {
        this.templateFeignClient = templateFeignClient;
    }

    /**
     * 按模板解析文件（根据内容 magic bytes 自动识别 xlsx / csv，失败时回退）。
     */
    public ParseResult parse(InputStream inputStream, Long templateId, String fileName) {
        TemplateInfoVO template = templateFeignClient.getTemplateById(templateId).getData();
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "模板不存在: " + templateId);
        }
        List<TemplateSchemaDTO> schemaList = parseSchema(template.getTemplateSchema());
        schemaList.sort(Comparator.comparing(TemplateSchemaDTO::getColumnIndex));
        log.info("模板 [{}] 加载完成，共 {} 列定义", template.getTemplateName(), schemaList.size());

        // 一次性读入内存，避免流被消费后无法回退
        byte[] content;
        try {
            content = inputStream.readAllBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取文件内容失败: " + e.getMessage());
        }
        log.info("文件 {} 大小: {} bytes, 前4字节(hex): {}", fileName, content.length,
                content.length >= 4 ? String.format("%02X %02X %02X %02X", content[0], content[1], content[2], content[3]) : "不足4字节");

        // 检测是否为 JSON 错误响应（下载失败时服务端返回的 BaseResponse）
        if (content.length > 0 && content[0] == '{') {
            String body = new String(content, StandardCharsets.UTF_8);
            log.error("文件下载返回了 JSON 而非文件内容，请检查 user 模块日志: {}", body);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件下载失败，服务端返回了错误响应");
        }

        boolean isXlsx = content.length >= 4 && content[0] == 0x50 && content[1] == 0x4B;

        if (isXlsx) {
            try {
                return parseExcel(new ByteArrayInputStream(content), schemaList);
            } catch (Exception e) {
                log.warn("EasyExcel 解析失败，回退到宽松 CSV 模式: {}", e.getMessage());
            }
        }
        return parseCsv(new ByteArrayInputStream(content), schemaList);
    }

    /**
     * EasyExcel 读取 xlsx。
     */
    private ParseResult parseExcel(InputStream inputStream, List<TemplateSchemaDTO> schemaList) {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> parseErrors = new ArrayList<>();

        EasyExcel.read(inputStream, new AnalysisEventListener<LinkedHashMap<Integer, String>>() {
            @Override
            public void invoke(LinkedHashMap<Integer, String> rowData, AnalysisContext context) {
                Integer rowIndex = context.readRowHolder().getRowIndex();
                Map<String, Object> row = processRow(rowData, schemaList, rowIndex, parseErrors);
                rows.add(row);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                log.info("Excel 读取完成，共 {} 行", context.readSheetHolder().getApproximateTotalRowNumber());
            }
        }).sheet().doRead();

        return new ParseResult(schemaList, rows, parseErrors, rows.size() - parseErrors.size(), parseErrors.size());
    }

    /**
     * commons-csv 读取 csv，使用宽松模式容忍引号后空格等非标准格式。
     */
    private ParseResult parseCsv(InputStream inputStream, List<TemplateSchemaDTO> schemaList) {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> parseErrors = new ArrayList<>();

        CSVFormat format = CSVFormat.RFC4180
                .withIgnoreSurroundingSpaces(true)
                .withIgnoreEmptyLines(true)
                .withTrim(true);

        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {

            int rowIndex = 0;
            for (CSVRecord record : parser) {
                LinkedHashMap<Integer, String> rowData = new LinkedHashMap<>();
                for (int i = 0; i < record.size(); i++) {
                    rowData.put(i, record.get(i));
                }
                Map<String, Object> row = processRow(rowData, schemaList, rowIndex, parseErrors);
                rows.add(row);
                rowIndex++;
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "CSV 解析失败: " + e.getMessage());
        }

        log.info("CSV 读取完成，共 {} 行", rows.size());
        return new ParseResult(schemaList, rows, parseErrors, rows.size() - parseErrors.size(), parseErrors.size());
    }

    /**
     * 单行数据处理：必填校验 + 类型转换。
     */
    private Map<String, Object> processRow(LinkedHashMap<Integer, String> rowData,
                                           List<TemplateSchemaDTO> schemaList,
                                           Integer rowIndex,
                                           List<String> parseErrors) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (TemplateSchemaDTO col : schemaList) {
            String rawValue = rowData.getOrDefault(col.getColumnIndex(), "");

            if (Boolean.TRUE.equals(col.getRequired())
                    && (rawValue == null || rawValue.isBlank())) {
                parseErrors.add(String.format("第%d行: [%s] 为必填字段", rowIndex + 1, col.getHeaderName()));
                continue;
            }

            Object converted = convertType(rawValue, col.getDataType(), col.getHeaderName(), rowIndex);
            row.put(col.getFieldKey(), converted);
        }
        return row;
    }

    /**
     * 将 JSON schema 解析为 TemplateSchemaDTO 列表。
     */
    private List<TemplateSchemaDTO> parseSchema(String templateSchema) {
        try {
            return OBJECT_MAPPER.readValue(templateSchema, new TypeReference<List<TemplateSchemaDTO>>() {});
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "模板 schema 解析失败: " + e.getMessage());
        }
    }

    /**
     * 类型转换。
     */
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
                case "datetime", "timestamp" -> LocalDateTime.parse(rawValue.trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                default -> rawValue.trim();
            };
        } catch (Exception e) {
            log.warn("第{}行: [{}] 类型转换失败, rawValue={}, dataType={}", rowIndex + 1, headerName, rawValue, type);
            return rawValue.trim();
        }
    }

    /**
     * 解析结果。
     */
    public record ParseResult(
            List<TemplateSchemaDTO> schema,
            List<Map<String, Object>> rows,
            List<String> errors,
            int validRows,
            int errorRows
    ) {}
}

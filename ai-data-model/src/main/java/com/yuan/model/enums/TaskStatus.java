package com.yuan.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 任务状态枚举 — ETL 管道三阶段细分。
 *
 * <pre>
 * 上传: 0=上传中 → 1=上传成功 / 2=上传失败
 * 解析: 3=解析中 → 4=解析成功 / 5=解析失败
 * 分析: 6=分析中 → 7=分析成功 / 8=分析失败
 * </pre>
 */
public enum TaskStatus {

    UPLOADING(0, "上传中"),
    UPLOAD_SUCCESS(1, "上传成功"),
    UPLOAD_FAILED(2, "上传失败"),
    PARSING(3, "解析中"),
    PARSE_SUCCESS(4, "解析成功"),
    PARSE_FAILED(5, "解析失败"),
    ANALYZING(6, "分析中"),
    ANALYZE_SUCCESS(7, "分析成功"),
    ANALYZE_FAILED(8, "分析失败");

    @EnumValue
    private final int code;
    private final String description;

    TaskStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}

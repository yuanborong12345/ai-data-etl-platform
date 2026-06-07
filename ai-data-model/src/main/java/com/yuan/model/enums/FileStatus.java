package com.yuan.model.enums;

/**
 * 文件处理状态枚举
 * 定义从上传到 AI 分析完成的完整状态机流转
 */
public enum FileStatus {

    /** 上传中（客户端正在传输） */
    UPLOADING(0, "上传中"),
    /** 待解析（上传完成，等待 Processor 消费） */
    PENDING(1, "待解析"),
    /** 解析中（Processor 正在执行 ETL） */
    PARSING(2, "解析中"),
    /** AI 分析中（Intelligence 正在调用大模型） */
    ANALYZING(3, "AI分析中"),
    /** 处理完成 */
    COMPLETED(4, "完成"),
    /** 处理失败 */
    FAILED(5, "失败");

    private final int code;
    private final String description;

    FileStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据 code 获取枚举
     */
    public static FileStatus fromCode(int code) {
        for (FileStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知文件状态码: " + code);
    }
}

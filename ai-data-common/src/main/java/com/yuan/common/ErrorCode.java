package com.yuan.common;

/**
 * 自定义错误码
 *
 */
public enum ErrorCode {
    /**
     * 业务码
     */
    /*** 登录 */
    LOGIN_ACCOUNT_NOT_EXIST(40001,"登录账号不存在"),
    LOGIN_ACCOUNT_PASSWORD_ERROR(40002,"登录密码错误"),
    LOGIN_ACCOUNT_STATUS_EXCEPTION(40003,"账号状态异常"),
    /*** 网关*/
    GATEWAY_NO_TOKEN(40020,"未检测到合法的鉴权令牌"),
    GATEWAY_INVALID_CLAIMS(40021,"登录凭证无效或已损坏"),
    GATEWAY_SESSION_EXPIRED(40022,"登录已经过期"),
    GATEWAY_TOKEN_TEMPERED(40023,"签名验证失败"),
    GATEWAY_KICKED_BY_ANOTHER_LOGIN(40024,"您的账号已在别处登录"),
    GATEWAY_ACCOUNT_BANNED(40025,"该账号已被管理员封禁"),
    GATEWAY_NO_SESSION(40026,"会话不存在"),
    /**
     * 通用码
     */
    SUCCESS(0, "ok"),
    PARAMS_ERROR(40000, "请求参数错误"),
    NOT_LOGIN_ERROR(40100, "未登录"),
    NO_AUTH_ERROR(40101, "无权限"),
    NOT_FOUND_ERROR(40400, "请求数据不存在"),
    FORBIDDEN_ERROR(40300, "禁止访问"),
    SYSTEM_ERROR(50000, "系统内部异常"),
    OPERATION_ERROR(50001, "操作失败");

    /**
     * 状态码
     */
    private final int code;

    /**
     * 信息
     */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

}

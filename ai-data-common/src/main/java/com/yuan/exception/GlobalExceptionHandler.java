package com.yuan.exception;

import com.yuan.common.BaseResponse;
import com.yuan.common.ErrorCode;
import com.yuan.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<BaseResponse<?>> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResponseEntity.status(e.getCode() / 100)
                .body(ResultUtils.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<?>> methodArgumentNotValidExceptionHandler(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        log.warn("参数校验失败: {}", msg);
        return ResponseEntity.status(ErrorCode.PARAMS_ERROR.getCode() / 100)
                .body(ResultUtils.error(ErrorCode.PARAMS_ERROR, msg));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<?>> httpMessageNotReadableExceptionHandler(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.PARAMS_ERROR.getCode() / 100)
                .body(ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求体格式错误，请检查JSON格式"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<BaseResponse<?>> missingServletRequestParameterExceptionHandler(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return ResponseEntity.status(ErrorCode.PARAMS_ERROR.getCode() / 100)
                .body(ResultUtils.error(ErrorCode.PARAMS_ERROR, "缺少参数: " + e.getParameterName()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<BaseResponse<?>> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResponseEntity.status(ErrorCode.SYSTEM_ERROR.getCode() / 100)
                .body(ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误"));
    }
}

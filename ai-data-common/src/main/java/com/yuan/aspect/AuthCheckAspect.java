package com.yuan.aspect;

import com.yuan.annotation.AuthCheck;
import com.yuan.common.ErrorCode;
import com.yuan.exception.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import com.yuan.utils.UserContext;
import org.springframework.web.servlet.DispatcherServlet;

@Component
@Aspect
@ConditionalOnClass(DispatcherServlet.class)
public class AuthCheckAspect {
    @Pointcut("@annotation(com.yuan.annotation.AuthCheck)")
    public void authPointcut() {}

    @Around("authPointcut()")
    public Object checkAuth(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        AuthCheck authCheck = signature.getMethod().getAnnotation(AuthCheck.class);
        String mustRole = authCheck.mustRole();

        // 当前登录角色
        String currentRole = UserContext.getRole();

        // 必须是指定角色
        if (!mustRole.equals(currentRole)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 通过校验
        return joinPoint.proceed();
    }
}

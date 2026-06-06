package com.yuan.config;

import com.yuan.filter.UserContextFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;

@Configuration
// 只有普通SpringMvc项目才加载，Gateway(WebFlux)没有DispatcherServlet，自动跳过，避免网关启动报错
@ConditionalOnClass(DispatcherServlet.class)
public class AutoWebConfig {

    @Bean
    public FilterRegistrationBean<UserContextFilter> userContextFilter(){
        FilterRegistrationBean<UserContextFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new UserContextFilter());
        bean.addUrlPatterns("/*"); // 拦截全部接口
        bean.setOrder(1); // 优先级靠前，先解析header再进Controller、切面
        return bean;
    }
}
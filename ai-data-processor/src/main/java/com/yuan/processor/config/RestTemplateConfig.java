package com.yuan.processor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    /**
     * 用于直连 MinIO 预签名 URL 下载，不需要负载均衡。
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

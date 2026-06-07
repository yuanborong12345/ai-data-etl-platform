package com.yuan.intelligence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan("com.yuan")
public class IntelligenceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntelligenceApplication.class, args);
    }
}

package com.example.travelassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 旅行助手应用入口。
 *
 * <p>{@link ConfigurationPropertiesScan} 会扫描 {@code travel.agent.*} 配置并绑定到属性类。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TravelAssistantApplication {

    /** 启动 Spring Boot 应用。 */
    public static void main(String[] args) {
        SpringApplication.run(TravelAssistantApplication.class, args);
    }
}

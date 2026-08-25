package com.bioplatform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 生物信息学云平台 - 启动类
 * 
 * @author luosg
 */
@EnableAsync
@EnableScheduling
@SpringBootApplication
@MapperScan("com.bioplatform.mapper")
public class BioPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(BioPlatformApplication.class, args);
    }

}

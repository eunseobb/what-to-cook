package com.whattocook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// 인증은 JWT로 직접 처리하므로 기본 인메모리 사용자 생성을 끈다.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class WhatToCookApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhatToCookApplication.class, args);
    }
}

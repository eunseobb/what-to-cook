package com.whattocook.community.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param hideThreshold 누적 신고 수가 이 값에 이르면 대상을 숨긴다.
 */
@ConfigurationProperties(prefix = "app.report")
public record ReportProperties(int hideThreshold) {

    public ReportProperties {
        if (hideThreshold < 1) {
            throw new IllegalArgumentException("app.report.hide-threshold는 1 이상이어야 합니다: " + hideThreshold);
        }
    }
}

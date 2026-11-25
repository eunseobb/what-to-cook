package com.whattocook.ai.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** LLM 호출 전용 스레드 풀. 느린 외부 호출이 웹 요청 스레드를 붙잡지 않게 분리한다. */
@Configuration
public class AiRecipeExecutorConfig {

    public static final String EXECUTOR = "aiRecipeExecutor";

    @Bean(EXECUTOR)
    public ThreadPoolTaskExecutor aiRecipeExecutor(AiRecipeProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.poolSize());
        executor.setMaxPoolSize(properties.poolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("ai-recipe-");
        // 종료 시 진행 중인 생성은 끝내고 저장한다. 못 끝낸 작업은 TTL이 지나면 사라지고 클라이언트가 다시 요청한다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}

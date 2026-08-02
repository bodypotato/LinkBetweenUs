package com.body.linkbetweenus.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Dify 集成配置 —— RestClient + 异步线程池
 */
@Slf4j
@Configuration
@EnableAsync
@EnableConfigurationProperties(DifyProperties.class)
public class DifyConfig {

    /**
     * 调用 Dify API 的 RestClient（使用 JDK HttpClient，连接/读取超时可配）
     * <p>
     * 注：Spring Boot 4.1 未提供 RestClient.Builder 的自动配置，
     * 直接使用 RestClient.builder() 静态工厂创建。
     * </p>
     */
    @Bean
    public RestClient difyRestClient(DifyProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.getConnectTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(props.getReadTimeout());

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Dify 异步回调专用线程池
     * <p>
     * core=2, max=4, queue=200。队列满时 AbortPolicy 拒绝，
     * 被拒绝的任务在调用方 catch 后仅记日志，不影响主流程。
     * </p>
     */
    @Bean("difyTaskExecutor")
    public Executor difyTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("dify-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}

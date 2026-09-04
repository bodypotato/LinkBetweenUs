package com.body.linkbetweenus.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * LBU_agent 集成配置 —— RestClient + 异步线程池（与 DifyConfig 模式一致）
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfig {

    /**
     * 调用 LBU_agent 的 RestClient（读超时给足 180s，本地模型生成慢）。
     * <p>
     * 注意：这里刻意用 SimpleClientHttpRequestFactory（HttpURLConnection），
     * 不用 JdkClientHttpRequestFactory——Spring 7.0 的 JDK 工厂对 POST 流式
     * body 有兼容问题（body 丢失，对端报 422 body missing），实测踩坑。
     * </p>
     */
    @Bean
    public RestClient agentRestClient(AgentProperties props) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) props.getConnectTimeout().toMillis());
        requestFactory.setReadTimeout((int) props.getReadTimeout().toMillis());

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * LBU_agent 异步回调专用线程池
     * <p>
     * core=2, max=4, queue=200。队列满时 AbortPolicy 拒绝，
     * 被拒绝的任务在调用方 catch 后仅记日志，不影响主流程。
     * 注意：本地模型推理耗时较长（可达分钟级），单任务占线程时间远高于 Dify。
     * </p>
     */
    @Bean("agentTaskExecutor")
    public Executor agentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("agent-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}

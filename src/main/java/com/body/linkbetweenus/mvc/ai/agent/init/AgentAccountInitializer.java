package com.body.linkbetweenus.mvc.ai.agent.init;

import com.body.linkbetweenus.config.AgentProperties;
import com.body.linkbetweenus.entity.User;
import com.body.linkbetweenus.mvc.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 启动时自动创建 LBU_agent 机器人账号
 * <p>
 * 若 LBU_agent 通道已启用（base-url 已配置）且 bot 账号在 LBU_User 表中不存在，
 * 则自动插入一条用户记录（随机密码，无法用于登录）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentAccountInitializer implements ApplicationRunner {

    private final AgentProperties agentProperties;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (!agentProperties.isEnabled() || !StringUtils.hasText(agentProperties.getBaseUrl())) {
            log.info("LBU_agent 未配置（缺少 base-url），跳过 LBU_agent 机器人账号初始化");
            return;
        }

        String botAccount = agentProperties.getBotAccount();
        String botName = agentProperties.getBotName();

        try {
            User existing = userMapper.selectById(botAccount);
            if (existing != null) {
                log.info("LBU_agent 机器人账号已存在: account={}", botAccount);
                return;
            }

            User bot = User.builder()
                    .account(botAccount)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .name(botName)
                    .createTime(LocalDateTime.now())
                    .build();
            userMapper.insert(bot);
            log.info("LBU_agent 机器人账号已创建: account={}, name={}", botAccount, botName);

        } catch (Exception e) {
            // 可能是并发启动导致的重复键冲突，已在另一个实例中创建
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                log.info("LBU_agent 机器人账号已被另一实例创建: account={}", botAccount);
            } else {
                log.error("创建 LBU_agent 机器人账号失败: account={}", botAccount, e);
            }
        }
    }
}

package com.body.linkbetweenus.mvc.ai.init;

import com.body.linkbetweenus.config.DifyProperties;
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
 * 启动时自动创建 AI 机器人账号
 * <p>
 * 若 Dify 功能已启用且 bot 账号在 LBU_User 表中不存在，
 * 则自动插入一条用户记录（随机密码，无法用于登录）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BotAccountInitializer implements ApplicationRunner {

    private final DifyProperties difyProperties;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (!difyProperties.isEnabled()
                || !StringUtils.hasText(difyProperties.getBaseUrl())
                || !StringUtils.hasText(difyProperties.getApiKey())) {
            log.info("Dify 未配置（缺少 base-url 或 api-key），跳过 AI 机器人账号初始化");
            return;
        }

        String botAccount = difyProperties.getBotAccount();
        String botName = difyProperties.getBotName();

        try {
            User existing = userMapper.selectById(botAccount);
            if (existing != null) {
                log.info("AI 机器人账号已存在: account={}", botAccount);
                return;
            }

            User bot = User.builder()
                    .account(botAccount)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .name(botName)
                    .createTime(LocalDateTime.now())
                    .build();
            userMapper.insert(bot);
            log.info("AI 机器人账号已创建: account={}, name={}", botAccount, botName);

        } catch (Exception e) {
            // 可能是并发启动导致的重复键冲突，已在另一个实例中创建
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                log.info("AI 机器人账号已被另一实例创建: account={}", botAccount);
            } else {
                log.error("创建 AI 机器人账号失败: account={}", botAccount, e);
            }
        }
    }
}

package com.body.linkbetweenus.mvc.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.body.linkbetweenus.dto.ResetPasswordRequest;
import com.body.linkbetweenus.dto.SecurityQuestionItem;
import com.body.linkbetweenus.dto.SecurityQuestionVO;
import com.body.linkbetweenus.entity.SecurityQuestion;
import com.body.linkbetweenus.entity.User;
import com.body.linkbetweenus.mvc.mapper.SecurityQuestionMapper;
import com.body.linkbetweenus.mvc.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SecurityQuestionService {

    private static final String KEY_FAIL  = "pwd:fail:";   // 错误计数
    private static final String KEY_LOCK  = "pwd:lock:";   // 锁定标记
    private static final String KEY_QUERY = "pwd:query:";  // 查询次数

    private static final int    MAX_QUERY_PER_HOUR = 10;   // 每小时最多查10次
    private static final int    MAX_FAIL_COUNT     = 5;    // 连续失败5次锁定
    private static final int    LOCK_MINUTES       = 30;   // 锁定30分钟

    private final SecurityQuestionMapper securityQuestionMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;

    /** 设置密保问题（覆盖旧数据），需验证密码 */
    @Transactional(rollbackFor = Exception.class)
    public void setQuestions(String account, String password, List<SecurityQuestionItem> items) {
        // 验证密码
        User user = userMapper.selectById(account);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        // 删除旧的
        securityQuestionMapper.delete(
                new LambdaQueryWrapper<SecurityQuestion>()
                        .eq(SecurityQuestion::getAccount, account));

        // 插入新的
        for (SecurityQuestionItem item : items) {
            SecurityQuestion sq = SecurityQuestion.builder()
                    .account(account)
                    .question(item.getQuestion())
                    .answer(passwordEncoder.encode(item.getAnswer()))
                    .build();
            securityQuestionMapper.insert(sq);
        }
    }

    /** 查看自己的密保问题（不含答案） */
    public List<SecurityQuestionVO> getMyQuestions(String account) {
        List<SecurityQuestion> list = securityQuestionMapper.selectList(
                new LambdaQueryWrapper<SecurityQuestion>()
                        .eq(SecurityQuestion::getAccount, account));
        return list.stream().map(SecurityQuestionVO::from).collect(Collectors.toList());
    }

    /** 查指定账号的密保问题（用于重置密码），有频率限制和锁定检查 */
    public List<SecurityQuestionVO> getQuestionsByAccount(String account) {
        // 1. 检查是否被锁定
        if (Boolean.TRUE.equals(redisTemplate.hasKey(KEY_LOCK + account))) {
            long ttl = redisTemplate.getExpire(KEY_LOCK + account);
            long minutes = ttl > 0 ? ttl / 60 : LOCK_MINUTES;
            throw new RuntimeException("操作过于频繁，请 " + minutes + " 分钟后再试");
        }

        // 2. 查询频率限制（每小时最多 N 次）
        String queryKey = KEY_QUERY + account;
        Long queryCount = redisTemplate.opsForValue().increment(queryKey);
        if (queryCount != null && queryCount == 1) {
            redisTemplate.expire(queryKey, Duration.ofHours(1));
        }
        if (queryCount != null && queryCount > MAX_QUERY_PER_HOUR) {
            // 超限 → 锁定账号
            redisTemplate.opsForValue().set(KEY_LOCK + account, "1", Duration.ofMinutes(LOCK_MINUTES));
            redisTemplate.delete(queryKey);
            throw new RuntimeException("操作过于频繁，请 " + LOCK_MINUTES + " 分钟后再试");
        }

        // 3. 业务逻辑
        User user = userMapper.selectById(account);
        if (user == null) {
            // 不暴露账号是否存在
            throw new RuntimeException("无法查询密保问题");
        }
        List<SecurityQuestion> list = securityQuestionMapper.selectList(
                new LambdaQueryWrapper<SecurityQuestion>()
                        .eq(SecurityQuestion::getAccount, account));
        if (list.isEmpty()) {
            throw new RuntimeException("无法查询密保问题");
        }
        return list.stream().map(SecurityQuestionVO::from).collect(Collectors.toList());
    }

    /** 验证答案并重置密码，有失败次数限制 */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordRequest request) {
        String account = request.getAccount();

        // 1. 检查锁
        if (Boolean.TRUE.equals(redisTemplate.hasKey(KEY_LOCK + account))) {
            long ttl = redisTemplate.getExpire(KEY_LOCK + account);
            long minutes = ttl > 0 ? ttl / 60 : LOCK_MINUTES;
            throw new RuntimeException("操作过于频繁，请 " + minutes + " 分钟后再试");
        }

        // 2. 查密保
        List<SecurityQuestion> questions = securityQuestionMapper.selectList(
                new LambdaQueryWrapper<SecurityQuestion>()
                        .eq(SecurityQuestion::getAccount, account));
        if (questions.isEmpty() || questions.size() != 3) {
            throw new RuntimeException("无法重置密码");
        }

        // 3. 验证答案
        for (ResetPasswordRequest.AnswerItem ans : request.getAnswers()) {
            SecurityQuestion sq = questions.stream()
                    .filter(q -> q.getId().equals(ans.getQuestionId()))
                    .findFirst()
                    .orElseThrow(() -> {
                        recordFailure(account);
                        return new RuntimeException("密保验证失败");
                    });
            if (!passwordEncoder.matches(ans.getAnswer(), sq.getAnswer())) {
                recordFailure(account);
                throw new RuntimeException("密保验证失败");
            }
        }

        // 4. 成功 → 清除所有计数和锁
        redisTemplate.delete(KEY_FAIL + account);
        redisTemplate.delete(KEY_LOCK + account);
        redisTemplate.delete(KEY_QUERY + account);

        User user = userMapper.selectById(account);
        if (user == null) {
            throw new RuntimeException("账号不存在");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
    }

    /** 记录一次失败，达到上限后锁定 */
    private void recordFailure(String account) {
        String failKey = KEY_FAIL + account;
        Long count = redisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1) {
            redisTemplate.expire(failKey, Duration.ofHours(1));
        }
        if (count != null && count >= MAX_FAIL_COUNT) {
            redisTemplate.opsForValue().set(KEY_LOCK + account, "1", Duration.ofMinutes(LOCK_MINUTES));
            redisTemplate.delete(failKey);
            redisTemplate.delete(KEY_QUERY + account);
        }
    }
}

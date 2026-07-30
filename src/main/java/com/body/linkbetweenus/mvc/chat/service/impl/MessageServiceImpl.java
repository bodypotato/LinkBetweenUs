package com.body.linkbetweenus.mvc.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.body.linkbetweenus.dto.ConversationVO;
import com.body.linkbetweenus.dto.MessageVO;
import com.body.linkbetweenus.dto.ReadReceiptDto;
import com.body.linkbetweenus.dto.SendMessageRequest;
import com.body.linkbetweenus.entity.Message;
import com.body.linkbetweenus.entity.User;
import com.body.linkbetweenus.mvc.chat.service.MessageService;
import com.body.linkbetweenus.mvc.mapper.MessageMapper;
import com.body.linkbetweenus.mvc.mapper.UserMapper;
import com.body.linkbetweenus.mvc.online.service.OnlineStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final OnlineStatusService onlineStatusService;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageVO sendMessage(String fromAccount, SendMessageRequest request) {
        String toAccount = request.getToAccount();

        // 1. 不能发给自己
        if (fromAccount.equals(toAccount)) {
            throw new RuntimeException("不能给自己发消息");
        }

        // 2. 目标用户必须存在
        User targetUser = userMapper.selectById(toAccount);
        if (targetUser == null) {
            throw new RuntimeException("该用户不存在");
        }

        // 3. 持久化消息
        Message message = Message.builder()
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .content(request.getContent())
                .status(Message.STATUS_SENT)
                .createTime(LocalDateTime.now())
                .build();
        messageMapper.insert(message);

        // 4. 查发送方昵称
        User fromUser = userMapper.selectById(fromAccount);
        String fromName = fromUser != null ? fromUser.getName() : fromAccount;

        MessageVO vo = MessageVO.from(message, fromName);

        // 5. 如果接收方在线，推送到 /user/{toAccount}/queue/private，并更新状态为已送达
        if (onlineStatusService.isOnline(toAccount)) {
            messagingTemplate.convertAndSendToUser(toAccount, "/queue/private", vo);
            message.setStatus(Message.STATUS_DELIVERED);
            messageMapper.updateById(message);
            vo.setStatus(Message.STATUS_DELIVERED);
            log.info("消息实时推送: {} -> {}, msgId={}", fromAccount, toAccount, message.getId());
        } else {
            log.info("消息已落库(对方离线): {} -> {}, msgId={}", fromAccount, toAccount, message.getId());
        }

        // 6. 发送 ack 给发送方
        messagingTemplate.convertAndSendToUser(fromAccount, "/queue/chat-ack", vo);

        return vo;
    }

    @Override
    public List<MessageVO> getChatHistory(String account, String otherAccount, int page, int size) {
        Page<Message> pageObj = new Page<>(page + 1, size);
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .and(w -> w.eq(Message::getFromAccount, account).eq(Message::getToAccount, otherAccount))
                .or(w -> w.eq(Message::getFromAccount, otherAccount).eq(Message::getToAccount, account))
                .orderByAsc(Message::getCreateTime);

        Page<Message> result = messageMapper.selectPage(pageObj, wrapper);

        // 批量查用户名
        Set<String> accounts = new HashSet<>();
        for (Message m : result.getRecords()) {
            accounts.add(m.getFromAccount());
        }
        Map<String, User> userMap = batchQueryUsers(accounts);

        return result.getRecords().stream()
                .map(m -> {
                    User u = userMap.get(m.getFromAccount());
                    return MessageVO.from(m, u != null ? u.getName() : m.getFromAccount());
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<ConversationVO> getConversations(String account) {
        List<Map<String, Object>> rows = messageMapper.findConversationRows(account);
        if (rows.isEmpty()) {
            return List.of();
        }

        // 收集所有对方账号
        Set<String> otherAccounts = new HashSet<>();
        for (Map<String, Object> row : rows) {
            otherAccounts.add((String) row.get("other_account"));
        }

        // 批量查用户名
        Map<String, User> userMap = batchQueryUsers(otherAccounts);

        List<ConversationVO> vos = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String otherAccount = (String) row.get("other_account");
            User user = userMap.get(otherAccount);
            String lastName = user != null ? user.getName() : otherAccount;
            String lastContent = (String) row.get("last_content");

            vos.add(ConversationVO.builder()
                    .account(otherAccount)
                    .name(lastName)
                    .lastMessage(lastContent != null && lastContent.length() > 50
                            ? lastContent.substring(0, 50) + "…" : lastContent)
                    .lastTime(toLocalDateTime(row.get("last_time")))
                    .unreadCount(toLong(row.get("unread_count")))
                    .build());
        }
        return vos;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MessageVO> fetchOfflineMessages(String account) {
        // 查找所有 status=SENT（未送达）的发给我的消息
        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getToAccount, account)
                        .eq(Message::getStatus, Message.STATUS_SENT)
                        .orderByAsc(Message::getCreateTime));

        if (messages.isEmpty()) {
            return List.of();
        }

        // 批量查发送方昵称
        Set<String> fromAccounts = messages.stream()
                .map(Message::getFromAccount)
                .collect(Collectors.toSet());
        Map<String, User> userMap = batchQueryUsers(fromAccounts);

        // 标记为已送达
        LocalDateTime now = LocalDateTime.now();
        for (Message m : messages) {
            m.setStatus(Message.STATUS_DELIVERED);
            messageMapper.updateById(m);
        }

        log.info("离线消息拉取: account={}, count={}", account, messages.size());

        return messages.stream()
                .map(m -> {
                    User u = userMap.get(m.getFromAccount());
                    return MessageVO.from(m, u != null ? u.getName() : m.getFromAccount());
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markAsRead(String account, String fromAccount) {
        // 查找所有 fromAccount → account 且 status < READ 的消息
        List<Message> unreadMessages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getFromAccount, fromAccount)
                        .eq(Message::getToAccount, account)
                        .lt(Message::getStatus, Message.STATUS_READ)
                        .orderByAsc(Message::getCreateTime));

        if (unreadMessages.isEmpty()) {
            return 0;
        }

        // 标记为已读
        LocalDateTime now = LocalDateTime.now();
        for (Message m : unreadMessages) {
            m.setStatus(Message.STATUS_READ);
            m.setReadTime(now);
            messageMapper.updateById(m);
        }

        int count = unreadMessages.size();
        log.info("消息已读: {} 阅读了来自 {} 的 {} 条消息", account, fromAccount, count);

        // 推已读回执给发送方
        User reader = userMapper.selectById(account);
        String readerName = reader != null ? reader.getName() : account;

        ReadReceiptDto receipt = ReadReceiptDto.builder()
                .type("READ_RECEIPT")
                .fromAccount(account)
                .fromName(readerName)
                .toAccount(fromAccount)
                .readTime(now)
                .count(count)
                .build();
        messagingTemplate.convertAndSendToUser(fromAccount, "/queue/read-receipt", receipt);

        return count;
    }

    // ===== 私有工具方法 =====

    private Map<String, User> batchQueryUsers(Set<String> accounts) {
        if (accounts.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(accounts).stream()
                .collect(Collectors.toMap(User::getAccount, u -> u));
    }

    private LocalDateTime toLocalDateTime(Object obj) {
        if (obj == null) return null;
        if (obj instanceof LocalDateTime dt) return dt;
        if (obj instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        if (obj instanceof java.util.Date d) return new java.sql.Timestamp(d.getTime()).toLocalDateTime();
        return null;
    }

    private long toLong(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number n) return n.longValue();
        return 0;
    }
}

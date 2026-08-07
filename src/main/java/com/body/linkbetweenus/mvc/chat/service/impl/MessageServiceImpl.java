package com.body.linkbetweenus.mvc.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.body.linkbetweenus.dto.ConversationVO;
import com.body.linkbetweenus.dto.MessageVO;
import com.body.linkbetweenus.dto.ReadReceiptDto;
import com.body.linkbetweenus.dto.SendMessageRequest;
import com.body.linkbetweenus.entity.Friend;
import com.body.linkbetweenus.entity.Message;
import com.body.linkbetweenus.entity.User;
import com.body.linkbetweenus.mvc.chat.service.MessageService;
import com.body.linkbetweenus.mvc.ai.service.DifyService;
import com.body.linkbetweenus.mvc.mapper.FriendMapper;
import com.body.linkbetweenus.mvc.mapper.MessageMapper;
import com.body.linkbetweenus.mvc.mapper.UserMapper;
import com.body.linkbetweenus.mvc.online.service.OnlineStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final FriendMapper friendMapper;
    private final OnlineStatusService onlineStatusService;
    private final SimpMessagingTemplate messagingTemplate;
    private final DifyService difyService;

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

        // 3. 必须先成为好友（AI 机器人除外）
        if (!difyService.isAiBot(toAccount) && !isFriend(fromAccount, toAccount)) {
            throw new RuntimeException("你们还不是好友，无法发送消息");
        }

        // 4. 持久化消息
        Message message = Message.builder()
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .content(request.getContent())
                .status(Message.STATUS_SENT)
                .createTime(LocalDateTime.now())
                .build();
        messageMapper.insert(message);

        // 查发送方昵称
        User fromUser = userMapper.selectById(fromAccount);
        String fromName = fromUser != null ? fromUser.getName() : fromAccount;

        MessageVO vo = MessageVO.from(message, fromName);

        // STOMP 推送移到 afterCommit —— 确保 DB 提交后客户端才收到，
        // 避免客户端立即调 loadConversations/markAsRead 时查到未提交的数据
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (onlineStatusService.isOnline(toAccount)) {
                    message.setStatus(Message.STATUS_DELIVERED);
                    messageMapper.updateById(message);
                    vo.setStatus(Message.STATUS_DELIVERED);
                    messagingTemplate.convertAndSendToUser(toAccount, "/queue/private", vo);
                    log.info("消息实时推送: {} -> {}, msgId={}", fromAccount, toAccount, message.getId());
                } else {
                    log.info("消息已落库(对方离线): {} -> {}, msgId={}", fromAccount, toAccount, message.getId());
                }
                // 发送 ack 给发送方（也在提交后，保证状态一致）
                messagingTemplate.convertAndSendToUser(fromAccount, "/queue/chat-ack", vo);

                // 发给 AI 机器人时异步调用 Dify 获取回复
                if (difyService.isAiBot(toAccount)) {
                    try {
                        difyService.handleBotMessageAsync(fromAccount, request.getContent());
                    } catch (Exception e) {
                        log.error("AI回复调度失败: user={}", fromAccount, e);
                    }
                }
            }
        });

        return vo;
    }

    @Override
    public List<MessageVO> getChatHistory(String account, String otherAccount, int page, int size) {
        Page<Message> pageObj = new Page<>(page + 1, size);
        // 对话记录：排除当前用户已软删除的消息
        //   我发的 → 过滤 sender_deleted=0；我收的 → 过滤 receiver_deleted=0
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .and(w -> w.eq(Message::getFromAccount, account)
                           .eq(Message::getToAccount, otherAccount)
                           .eq(Message::getSenderDeleted, false))
                .or(w -> w.eq(Message::getFromAccount, otherAccount)
                           .eq(Message::getToAccount, account)
                           .eq(Message::getReceiverDeleted, false))
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
        // 查找所有 status=SENT（未送达）的发给我的消息（排除已软删除的）
        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getToAccount, account)
                        .eq(Message::getStatus, Message.STATUS_SENT)
                        .eq(Message::getReceiverDeleted, false)
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
        // 查找所有 fromAccount → account 且 status < READ、未被接收者删除的消息
        List<Message> unreadMessages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getFromAccount, fromAccount)
                        .eq(Message::getToAccount, account)
                        .lt(Message::getStatus, Message.STATUS_READ)
                        .eq(Message::getReceiverDeleted, false)
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDeleteMessage(String account, Long messageId) {
        Message msg = messageMapper.selectById(messageId);
        if (msg == null) {
            throw new RuntimeException("消息不存在");
        }

        boolean isSender = account.equals(msg.getFromAccount());
        boolean isReceiver = account.equals(msg.getToAccount());

        if (!isSender && !isReceiver) {
            throw new RuntimeException("无权操作该消息");
        }

        if (isSender) {
            msg.setSenderDeleted(true);
        }
        if (isReceiver) {
            msg.setReceiverDeleted(true);
        }
        messageMapper.updateById(msg);

        log.debug("消息软删除: id={}, account={}, role={}",
                messageId, account, isSender ? "sender" : "receiver");
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

    private boolean isFriend(String account1, String account2) {
        String a = account1.compareTo(account2) < 0 ? account1 : account2;
        String b = account1.compareTo(account2) < 0 ? account2 : account1;
        return friendMapper.selectOne(
                new LambdaQueryWrapper<Friend>()
                        .eq(Friend::getAccountA, a)
                        .eq(Friend::getAccountB, b)) != null;
    }
}

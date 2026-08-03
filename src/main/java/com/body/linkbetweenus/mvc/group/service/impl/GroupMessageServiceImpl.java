package com.body.linkbetweenus.mvc.group.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.body.linkbetweenus.dto.GroupConversationVO;
import com.body.linkbetweenus.dto.GroupMessageVO;
import com.body.linkbetweenus.dto.SendGroupMessageRequest;
import com.body.linkbetweenus.entity.*;
import com.body.linkbetweenus.mvc.group.service.GroupMessageService;
import com.body.linkbetweenus.mvc.mapper.*;
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
public class GroupMessageServiceImpl implements GroupMessageService {

    private final GroupMessageMapper groupMessageMapper;
    private final GroupMessageDeleteMapper groupMessageDeleteMapper;
    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final UserMapper userMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupMessageVO sendMessage(String fromAccount, SendGroupMessageRequest request) {
        Long groupId = request.getGroupId();

        // 1. 必须是群成员
        GroupMember member = groupMemberMapper.selectOne(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId)
                        .eq(GroupMember::getAccount, fromAccount));
        if (member == null) {
            throw new RuntimeException("你不是该群成员，无法发送消息");
        }

        // 2. 检查是否被禁言
        if (member.getMutedUntil() != null) {
            if (member.getMutedUntil().isAfter(LocalDateTime.now())) {
                throw new RuntimeException("你已被禁言，无法发送消息");
            }
            // 禁言已过期，自动清除（用 LambdaUpdateWrapper 显式 SET NULL）
            groupMemberMapper.update(null,
                    new LambdaUpdateWrapper<GroupMember>()
                            .eq(GroupMember::getId, member.getId())
                            .set(GroupMember::getMutedUntil, null));
        }

        // 3. 持久化
        GroupMessage message = GroupMessage.builder()
                .groupId(groupId)
                .fromAccount(fromAccount)
                .content(request.getContent())
                .createTime(LocalDateTime.now())
                .build();
        groupMessageMapper.insert(message);

        // 3. 查发送方昵称
        User fromUser = userMapper.selectById(fromAccount);
        String fromName = fromUser != null ? fromUser.getName() : fromAccount;

        GroupMessageVO vo = GroupMessageVO.from(message, fromName);

        // 4. 广播到群内所有成员（/topic/group.{id}）
        messagingTemplate.convertAndSend("/topic/group." + groupId, vo);
        log.info("群消息已广播: groupId={}, from={}, msgId={}", groupId, fromAccount, message.getId());

        // 5. 更新发送者本人的 last_read_time，避免计入自己的未读数
        member.setLastReadTime(LocalDateTime.now());
        groupMemberMapper.updateById(member);

        return vo;
    }

    @Override
    public List<GroupMessageVO> getHistory(Long groupId, String account, int page, int size) {
        Page<GroupMessage> pageObj = new Page<>(page + 1, size);
        LambdaQueryWrapper<GroupMessage> wrapper = new LambdaQueryWrapper<GroupMessage>()
                .eq(GroupMessage::getGroupId, groupId)
                .apply("NOT EXISTS (SELECT 1 FROM LBU_Group_Message_Delete d WHERE d.message_id = LBU_Group_Message.id AND d.account = {0})", account)
                .orderByAsc(GroupMessage::getCreateTime);

        Page<GroupMessage> result = groupMessageMapper.selectPage(pageObj, wrapper);

        // 批量查用户名
        Set<String> accounts = result.getRecords().stream()
                .map(GroupMessage::getFromAccount)
                .collect(Collectors.toSet());
        Map<String, User> userMap = batchQueryUsers(accounts);

        return result.getRecords().stream()
                .map(m -> {
                    User u = userMap.get(m.getFromAccount());
                    return GroupMessageVO.from(m, u != null ? u.getName() : m.getFromAccount());
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<GroupConversationVO> getGroupConversations(String account) {
        List<Map<String, Object>> rows = groupMessageMapper.findGroupConversationRows(account);
        if (rows.isEmpty()) {
            return List.of();
        }

        List<GroupConversationVO> vos = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String lastContent = (String) row.get("last_content");
            vos.add(GroupConversationVO.builder()
                    .groupId(toLong(row.get("group_id")))
                    .groupName((String) row.get("group_name"))
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
    public void markAsRead(String account, Long groupId) {
        GroupMember member = groupMemberMapper.selectOne(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId)
                        .eq(GroupMember::getAccount, account));
        if (member == null) {
            throw new RuntimeException("你不是该群成员");
        }

        member.setLastReadTime(LocalDateTime.now());
        groupMemberMapper.updateById(member);

        log.debug("群消息已读: account={}, groupId={}", account, groupId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDeleteMessage(String account, Long messageId) {
        GroupMessage msg = groupMessageMapper.selectById(messageId);
        if (msg == null) {
            throw new RuntimeException("消息不存在");
        }

        // 检查是否为群成员
        GroupMember member = groupMemberMapper.selectOne(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, msg.getGroupId())
                        .eq(GroupMember::getAccount, account));
        if (member == null) {
            throw new RuntimeException("你不是该群成员");
        }

        // 插入删除记录（UNIQUE KEY 保证不会重复）
        GroupMessageDelete del = GroupMessageDelete.builder()
                .messageId(messageId)
                .account(account)
                .createTime(LocalDateTime.now())
                .build();
        groupMessageDeleteMapper.insert(del);

        log.debug("群消息软删除: messageId={}, account={}, groupId={}", messageId, account, msg.getGroupId());
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

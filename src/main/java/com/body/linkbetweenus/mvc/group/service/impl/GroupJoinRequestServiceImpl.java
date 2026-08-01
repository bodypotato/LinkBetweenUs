package com.body.linkbetweenus.mvc.group.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.body.linkbetweenus.dto.GroupJoinRequestVO;
import com.body.linkbetweenus.dto.GroupNotificationDto;
import com.body.linkbetweenus.entity.Group;
import com.body.linkbetweenus.entity.GroupJoinRequest;
import com.body.linkbetweenus.entity.GroupMember;
import com.body.linkbetweenus.entity.User;
import com.body.linkbetweenus.mvc.group.service.GroupJoinRequestService;
import com.body.linkbetweenus.mvc.mapper.GroupJoinRequestMapper;
import com.body.linkbetweenus.mvc.mapper.GroupMapper;
import com.body.linkbetweenus.mvc.mapper.GroupMemberMapper;
import com.body.linkbetweenus.mvc.mapper.UserMapper;
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
public class GroupJoinRequestServiceImpl implements GroupJoinRequestService {

    private final GroupJoinRequestMapper joinRequestMapper;
    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final UserMapper userMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendJoinRequest(String fromAccount, Long groupId, String message) {
        // 1. 群必须存在
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new RuntimeException("群不存在");
        }

        // 2. 检查是否已是成员
        GroupMember existingMember = groupMemberMapper.selectOne(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId)
                        .eq(GroupMember::getAccount, fromAccount));
        if (existingMember != null) {
            throw new RuntimeException("你已经是该群成员");
        }

        // 3. 检查是否已有待处理申请
        GroupJoinRequest existing = joinRequestMapper.selectOne(
                new LambdaQueryWrapper<GroupJoinRequest>()
                        .eq(GroupJoinRequest::getGroupId, groupId)
                        .eq(GroupJoinRequest::getFromAccount, fromAccount));

        Long requestId;
        if (existing != null) {
            if (existing.getStatus() == GroupJoinRequest.STATUS_PENDING) {
                throw new RuntimeException("已向该群发送过入群申请，请等待处理");
            }
            // 已拒绝，允许重新申请
            existing.setStatus(GroupJoinRequest.STATUS_PENDING);
            existing.setMessage(message);
            existing.setCreateTime(LocalDateTime.now());
            existing.setUpdateTime(LocalDateTime.now());
            joinRequestMapper.updateById(existing);
            requestId = existing.getId();
        } else {
            GroupJoinRequest req = GroupJoinRequest.builder()
                    .groupId(groupId)
                    .fromAccount(fromAccount)
                    .status(GroupJoinRequest.STATUS_PENDING)
                    .message(message)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            joinRequestMapper.insert(req);
            requestId = req.getId();
        }

        // 4. 通知群主和管理员
        User fromUser = userMapper.selectById(fromAccount);
        String fromName = fromUser != null ? fromUser.getName() : fromAccount;
        String groupName = group.getName();

        // 查群主和管理员账号列表
        List<GroupMember> admins = groupMemberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId)
                        .in(GroupMember::getRole, GroupMember.ROLE_OWNER, GroupMember.ROLE_ADMIN));

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                GroupNotificationDto notification = GroupNotificationDto.builder()
                        .type("GROUP_JOIN_REQUEST")
                        .groupId(groupId)
                        .groupName(groupName)
                        .requestId(requestId)
                        .fromAccount(fromAccount)
                        .fromName(fromName)
                        .message(message)
                        .build();

                for (GroupMember admin : admins) {
                    messagingTemplate.convertAndSendToUser(admin.getAccount(), "/queue/group-notification", notification);
                }
                log.info("入群申请已发送: account={}, groupId={}, requestId={}", fromAccount, groupId, requestId);
            }
        });
    }

    @Override
    public List<GroupJoinRequestVO> getPendingRequests(Long groupId, String operatorAccount) {
        // 校验操作者是群主或管理员
        GroupMember operator = groupMemberMapper.selectOne(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId)
                        .eq(GroupMember::getAccount, operatorAccount));
        if (operator == null || operator.getRole() == GroupMember.ROLE_MEMBER) {
            throw new RuntimeException("权限不足");
        }

        List<GroupJoinRequest> requests = joinRequestMapper.selectList(
                new LambdaQueryWrapper<GroupJoinRequest>()
                        .eq(GroupJoinRequest::getGroupId, groupId)
                        .eq(GroupJoinRequest::getStatus, GroupJoinRequest.STATUS_PENDING)
                        .orderByDesc(GroupJoinRequest::getCreateTime));

        return toVOList(requests);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveRequest(String operatorAccount, Long groupId, Long requestId) {
        // 校验权限
        GroupMember operator = groupMemberMapper.selectOne(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId)
                        .eq(GroupMember::getAccount, operatorAccount));
        if (operator == null || operator.getRole() == GroupMember.ROLE_MEMBER) {
            throw new RuntimeException("权限不足");
        }

        GroupJoinRequest req = joinRequestMapper.selectById(requestId);
        if (req == null || !req.getGroupId().equals(groupId)) {
            throw new RuntimeException("入群申请不存在");
        }
        if (req.getStatus() != GroupJoinRequest.STATUS_PENDING) {
            throw new RuntimeException("该申请已处理");
        }

        // 再次检查是否已是成员
        GroupMember existingMember = groupMemberMapper.selectOne(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId)
                        .eq(GroupMember::getAccount, req.getFromAccount()));
        if (existingMember != null) {
            throw new RuntimeException("该用户已经是群成员");
        }

        // 更新申请状态
        req.setStatus(GroupJoinRequest.STATUS_APPROVED);
        req.setUpdateTime(LocalDateTime.now());
        joinRequestMapper.updateById(req);

        // 加入群
        GroupMember newMember = GroupMember.builder()
                .groupId(groupId)
                .account(req.getFromAccount())
                .role(GroupMember.ROLE_MEMBER)
                .lastReadTime(LocalDateTime.now())
                .joinTime(LocalDateTime.now())
                .build();
        groupMemberMapper.insert(newMember);

        // 通知申请人 + 所有管理员刷新
        Group group = groupMapper.selectById(groupId);
        String groupName = group != null ? group.getName() : "";
        String fromAccount = req.getFromAccount();
        User fromUser = userMapper.selectById(fromAccount);
        String fromName = fromUser != null ? fromUser.getName() : fromAccount;

        // 查所有群成员（含刚加入的新成员），用于推送通知
        List<GroupMember> allMembers = groupMemberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId));
        List<String> allMemberAccounts = allMembers.stream()
                .map(GroupMember::getAccount)
                .collect(Collectors.toList());

        // 查所有管理员账号
        List<GroupMember> admins = groupMemberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId)
                        .in(GroupMember::getRole, GroupMember.ROLE_OWNER, GroupMember.ROLE_ADMIN));
        List<String> adminAccounts = admins.stream()
                .map(GroupMember::getAccount)
                .collect(Collectors.toList());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 通知申请人
                GroupNotificationDto toApplicant = GroupNotificationDto.builder()
                        .type("GROUP_JOIN_APPROVED")
                        .groupId(groupId)
                        .groupName(groupName)
                        .requestId(requestId)
                        .fromAccount(operatorAccount)
                        .build();
                messagingTemplate.convertAndSendToUser(fromAccount, "/queue/group-notification", toApplicant);

                // 通知所有管理员刷新待处理列表
                GroupNotificationDto toAdmins = GroupNotificationDto.builder()
                        .type("GROUP_REQUEST_HANDLED")
                        .groupId(groupId)
                        .groupName(groupName)
                        .requestId(requestId)
                        .fromAccount(fromAccount)
                        .message("入群申请已通过")
                        .build();
                for (String adminAccount : adminAccounts) {
                    messagingTemplate.convertAndSendToUser(adminAccount, "/queue/group-notification", toAdmins);
                }

                // 通知所有群成员刷新成员列表
                GroupNotificationDto toAll = GroupNotificationDto.builder()
                        .type("GROUP_MEMBER_JOINED")
                        .groupId(groupId)
                        .groupName(groupName)
                        .fromAccount(fromAccount)
                        .fromName(fromName)
                        .message(fromName + " 加入了群聊")
                        .build();
                for (String acc : allMemberAccounts) {
                    messagingTemplate.convertAndSendToUser(acc, "/queue/group-notification", toAll);
                }

                log.info("入群申请已通过: account={}, groupId={}, requestId={}", fromAccount, groupId, requestId);
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectRequest(String operatorAccount, Long groupId, Long requestId) {
        // 校验权限
        GroupMember operator = groupMemberMapper.selectOne(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId)
                        .eq(GroupMember::getAccount, operatorAccount));
        if (operator == null || operator.getRole() == GroupMember.ROLE_MEMBER) {
            throw new RuntimeException("权限不足");
        }

        GroupJoinRequest req = joinRequestMapper.selectById(requestId);
        if (req == null || !req.getGroupId().equals(groupId)) {
            throw new RuntimeException("入群申请不存在");
        }
        if (req.getStatus() != GroupJoinRequest.STATUS_PENDING) {
            throw new RuntimeException("该申请已处理");
        }

        req.setStatus(GroupJoinRequest.STATUS_REJECTED);
        req.setUpdateTime(LocalDateTime.now());
        joinRequestMapper.updateById(req);

        // 通知申请人 + 所有管理员刷新
        Group group = groupMapper.selectById(groupId);
        String groupName = group != null ? group.getName() : "";
        String fromAccount = req.getFromAccount();

        // 查所有管理员账号
        List<GroupMember> admins = groupMemberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId)
                        .in(GroupMember::getRole, GroupMember.ROLE_OWNER, GroupMember.ROLE_ADMIN));
        List<String> adminAccounts = admins.stream()
                .map(GroupMember::getAccount)
                .collect(Collectors.toList());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 通知申请人
                GroupNotificationDto toApplicant = GroupNotificationDto.builder()
                        .type("GROUP_JOIN_REJECTED")
                        .groupId(groupId)
                        .groupName(groupName)
                        .requestId(requestId)
                        .fromAccount(operatorAccount)
                        .build();
                messagingTemplate.convertAndSendToUser(fromAccount, "/queue/group-notification", toApplicant);

                // 通知所有管理员刷新待处理列表
                GroupNotificationDto toAdmins = GroupNotificationDto.builder()
                        .type("GROUP_REQUEST_HANDLED")
                        .groupId(groupId)
                        .groupName(groupName)
                        .requestId(requestId)
                        .fromAccount(fromAccount)
                        .message("入群申请已被拒绝")
                        .build();
                for (String adminAccount : adminAccounts) {
                    messagingTemplate.convertAndSendToUser(adminAccount, "/queue/group-notification", toAdmins);
                }

                log.info("入群申请已拒绝: account={}, groupId={}, requestId={}", fromAccount, groupId, requestId);
            }
        });
    }

    // ===== 私有方法 =====

    private List<GroupJoinRequestVO> toVOList(List<GroupJoinRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }

        Set<String> accounts = requests.stream()
                .map(GroupJoinRequest::getFromAccount)
                .collect(Collectors.toSet());
        Set<Long> groupIds = requests.stream()
                .map(GroupJoinRequest::getGroupId)
                .collect(Collectors.toSet());

        Map<String, User> userMap = userMapper.selectBatchIds(accounts).stream()
                .collect(Collectors.toMap(User::getAccount, u -> u));
        Map<Long, Group> groupMap = groupMapper.selectBatchIds(groupIds).stream()
                .collect(Collectors.toMap(Group::getId, g -> g));

        List<GroupJoinRequestVO> vos = new ArrayList<>();
        for (GroupJoinRequest r : requests) {
            User u = userMap.get(r.getFromAccount());
            Group g = groupMap.get(r.getGroupId());
            vos.add(GroupJoinRequestVO.builder()
                    .id(r.getId())
                    .groupId(r.getGroupId())
                    .groupName(g != null ? g.getName() : "")
                    .fromAccount(r.getFromAccount())
                    .fromName(u != null ? u.getName() : r.getFromAccount())
                    .status(r.getStatus())
                    .message(r.getMessage())
                    .createTime(r.getCreateTime())
                    .build());
        }
        return vos;
    }
}

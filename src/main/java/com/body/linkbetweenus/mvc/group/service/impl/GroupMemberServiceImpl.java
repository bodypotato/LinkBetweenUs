package com.body.linkbetweenus.mvc.group.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.body.linkbetweenus.dto.GroupMemberVO;
import com.body.linkbetweenus.dto.GroupNotificationDto;
import com.body.linkbetweenus.entity.Group;
import com.body.linkbetweenus.entity.GroupMember;
import com.body.linkbetweenus.entity.User;
import com.body.linkbetweenus.mvc.group.service.GroupMemberService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupMemberServiceImpl implements GroupMemberService {

    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final UserMapper userMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public List<GroupMemberVO> getMembers(Long groupId) {
        List<Map<String, Object>> rows = groupMemberMapper.findMemberRows(groupId);
        if (rows.isEmpty()) {
            return List.of();
        }

        Set<String> accounts = rows.stream()
                .map(r -> (String) r.get("account"))
                .collect(Collectors.toSet());
        Map<String, User> userMap = userMapper.selectBatchIds(accounts).stream()
                .collect(Collectors.toMap(User::getAccount, u -> u));

        List<GroupMemberVO> vos = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String account = (String) row.get("account");
            User u = userMap.get(account);
            vos.add(GroupMemberVO.builder()
                    .account(account)
                    .name(u != null ? u.getName() : account)
                    .role(toInt(row.get("role")))
                    .unreadCount(toLong(row.get("unread_count")))
                    .mutedUntil(toLocalDateTime(row.get("muted_until")))
                    .joinTime(toLocalDateTime(row.get("join_time")))
                    .build());
        }
        return vos;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void kickMember(String operatorAccount, Long groupId, String targetAccount) {
        if (operatorAccount.equals(targetAccount)) {
            throw new RuntimeException("你不能踢出自己，请使用退出群功能");
        }

        Group group = requireGroupExists(groupId);
        GroupMember operator = findMemberEntity(groupId, operatorAccount);
        GroupMember target = findMemberEntity(groupId, targetAccount);

        // 权限检查：群主可以踢任何人，管理员只能踢普通成员
        if (operator.getRole() == GroupMember.ROLE_MEMBER) {
            throw new RuntimeException("权限不足");
        }
        if (operator.getRole() == GroupMember.ROLE_ADMIN && target.getRole() != GroupMember.ROLE_MEMBER) {
            throw new RuntimeException("管理员只能踢出普通成员");
        }
        if (target.getRole() == GroupMember.ROLE_OWNER) {
            throw new RuntimeException("不能踢出群主");
        }

        // 删除前收集所有成员账号，用于推送通知
        List<GroupMember> allMembers = groupMemberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId));
        List<String> memberAccounts = allMembers.stream()
                .map(GroupMember::getAccount)
                .collect(Collectors.toList());

        groupMemberMapper.deleteById(target.getId());

        String groupName = group.getName();
        User opUser = userMapper.selectById(operatorAccount);
        String opName = opUser != null ? opUser.getName() : operatorAccount;
        User targetUser = userMapper.selectById(targetAccount);
        String targetName = targetUser != null ? targetUser.getName() : targetAccount;

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 通知被踢者
                GroupNotificationDto toTarget = GroupNotificationDto.builder()
                        .type("GROUP_KICKED")
                        .groupId(groupId)
                        .groupName(groupName)
                        .fromAccount(operatorAccount)
                        .fromName(opName)
                        .build();
                messagingTemplate.convertAndSendToUser(targetAccount, "/queue/group-notification", toTarget);

                // 通知群内其他所有成员：有人被踢出，刷新成员列表
                GroupNotificationDto toOthers = GroupNotificationDto.builder()
                        .type("GROUP_MEMBER_REMOVED")
                        .groupId(groupId)
                        .groupName(groupName)
                        .fromAccount(targetAccount)
                        .fromName(targetName)
                        .message(opName + " 将 " + targetName + " 踢出了群聊")
                        .build();
                for (String acc : memberAccounts) {
                    if (!acc.equals(targetAccount)) {
                        messagingTemplate.convertAndSendToUser(acc, "/queue/group-notification", toOthers);
                    }
                }

                log.info("用户被踢出群: {} -> groupId={}, operator={}", targetAccount, groupId, operatorAccount);
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void promoteMember(String operatorAccount, Long groupId, String targetAccount) {
        GroupMember operator = findMemberEntity(groupId, operatorAccount);
        if (operator.getRole() != GroupMember.ROLE_OWNER) {
            throw new RuntimeException("只有群主才能提升管理员");
        }

        GroupMember target = findMemberEntity(groupId, targetAccount);
        if (target.getRole() == GroupMember.ROLE_OWNER) {
            throw new RuntimeException("群主不能被提升");
        }
        if (target.getRole() == GroupMember.ROLE_ADMIN) {
            throw new RuntimeException("该成员已经是管理员");
        }

        target.setRole(GroupMember.ROLE_ADMIN);
        groupMemberMapper.updateById(target);

        // 收集所有成员账号，用于推送通知
        List<GroupMember> allMembers = groupMemberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId));
        List<String> memberAccounts = allMembers.stream()
                .map(GroupMember::getAccount)
                .collect(Collectors.toList());

        Group group = requireGroupExists(groupId);
        User targetUser = userMapper.selectById(targetAccount);
        String targetName = targetUser != null ? targetUser.getName() : targetAccount;
        String groupName = group.getName();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                GroupNotificationDto notification = GroupNotificationDto.builder()
                        .type("GROUP_MEMBER_PROMOTED")
                        .groupId(groupId)
                        .groupName(groupName)
                        .fromAccount(targetAccount)
                        .fromName(targetName)
                        .message("被提升为管理员")
                        .build();
                for (String acc : memberAccounts) {
                    messagingTemplate.convertAndSendToUser(acc, "/queue/group-notification", notification);
                }
                log.info("成员被提升为管理员: groupId={}, account={}, operator={}", groupId, targetAccount, operatorAccount);
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void demoteMember(String operatorAccount, Long groupId, String targetAccount) {
        GroupMember operator = findMemberEntity(groupId, operatorAccount);
        if (operator.getRole() != GroupMember.ROLE_OWNER) {
            throw new RuntimeException("只有群主才能解除管理员身份");
        }

        GroupMember target = findMemberEntity(groupId, targetAccount);
        if (target.getRole() == GroupMember.ROLE_OWNER) {
            throw new RuntimeException("群主不能被降级");
        }
        if (target.getRole() != GroupMember.ROLE_ADMIN) {
            throw new RuntimeException("该成员不是管理员，无需解除");
        }

        target.setRole(GroupMember.ROLE_MEMBER);
        groupMemberMapper.updateById(target);

        // 收集所有成员账号，用于推送通知
        List<GroupMember> allMembers = groupMemberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId));
        List<String> memberAccounts = allMembers.stream()
                .map(GroupMember::getAccount)
                .collect(Collectors.toList());

        Group group = requireGroupExists(groupId);
        User targetUser = userMapper.selectById(targetAccount);
        String targetName = targetUser != null ? targetUser.getName() : targetAccount;
        String groupName = group.getName();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                GroupNotificationDto notification = GroupNotificationDto.builder()
                        .type("GROUP_MEMBER_DEMOTED")
                        .groupId(groupId)
                        .groupName(groupName)
                        .fromAccount(targetAccount)
                        .fromName(targetName)
                        .message("管理员身份被解除")
                        .build();
                for (String acc : memberAccounts) {
                    messagingTemplate.convertAndSendToUser(acc, "/queue/group-notification", notification);
                }
                log.info("管理员被解除: groupId={}, account={}, operator={}", groupId, targetAccount, operatorAccount);
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void muteMember(String operatorAccount, Long groupId, String targetAccount, int minutes) {
        if (minutes <= 0) {
            throw new RuntimeException("禁言时长必须大于0分钟");
        }

        GroupMember operator = findMemberEntity(groupId, operatorAccount);
        GroupMember target = findMemberEntity(groupId, targetAccount);

        // 权限检查
        if (operator.getRole() == GroupMember.ROLE_MEMBER) {
            throw new RuntimeException("权限不足");
        }
        if (operator.getRole() == GroupMember.ROLE_ADMIN) {
            if (target.getRole() != GroupMember.ROLE_MEMBER) {
                throw new RuntimeException("管理员只能禁言普通成员");
            }
        }
        if (target.getRole() == GroupMember.ROLE_OWNER) {
            throw new RuntimeException("不能禁言群主");
        }

        LocalDateTime mutedUntil = LocalDateTime.now().plusMinutes(minutes);
        target.setMutedUntil(mutedUntil);
        groupMemberMapper.updateById(target);

        // 通知全体成员
        Group group = requireGroupExists(groupId);
        List<GroupMember> allMembers = groupMemberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId));
        List<String> memberAccounts = allMembers.stream()
                .map(GroupMember::getAccount)
                .collect(Collectors.toList());

        String groupName = group.getName();
        User targetUser = userMapper.selectById(targetAccount);
        String targetName = targetUser != null ? targetUser.getName() : targetAccount;

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                GroupNotificationDto notification = GroupNotificationDto.builder()
                        .type("GROUP_MEMBER_MUTED")
                        .groupId(groupId)
                        .groupName(groupName)
                        .fromAccount(targetAccount)
                        .fromName(targetName)
                        .message("被禁言 " + minutes + " 分钟")
                        .build();
                for (String acc : memberAccounts) {
                    messagingTemplate.convertAndSendToUser(acc, "/queue/group-notification", notification);
                }
                log.info("用户被禁言: groupId={}, account={}, mutedUntil={}, operator={}",
                        groupId, targetAccount, mutedUntil, operatorAccount);
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unmuteMember(String operatorAccount, Long groupId, String targetAccount) {
        GroupMember operator = findMemberEntity(groupId, operatorAccount);
        GroupMember target = findMemberEntity(groupId, targetAccount);

        // 权限检查
        if (operator.getRole() == GroupMember.ROLE_MEMBER) {
            throw new RuntimeException("权限不足");
        }
        if (operator.getRole() == GroupMember.ROLE_ADMIN) {
            if (target.getRole() != GroupMember.ROLE_MEMBER) {
                throw new RuntimeException("管理员只能解除普通成员的禁言");
            }
        }

        if (target.getMutedUntil() == null) {
            throw new RuntimeException("该成员未被禁言");
        }

        // 用 LambdaUpdateWrapper 显式 SET NULL，因为 updateById 默认跳过 null 字段
        groupMemberMapper.update(null,
                new LambdaUpdateWrapper<GroupMember>()
                        .eq(GroupMember::getId, target.getId())
                        .set(GroupMember::getMutedUntil, null));

        // 通知全体成员
        Group group = requireGroupExists(groupId);
        List<GroupMember> allMembers = groupMemberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId));
        List<String> memberAccounts = allMembers.stream()
                .map(GroupMember::getAccount)
                .collect(Collectors.toList());

        String groupName = group.getName();
        User targetUser = userMapper.selectById(targetAccount);
        String targetName = targetUser != null ? targetUser.getName() : targetAccount;

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                GroupNotificationDto notification = GroupNotificationDto.builder()
                        .type("GROUP_MEMBER_UNMUTED")
                        .groupId(groupId)
                        .groupName(groupName)
                        .fromAccount(targetAccount)
                        .fromName(targetName)
                        .message("禁言已解除")
                        .build();
                for (String acc : memberAccounts) {
                    messagingTemplate.convertAndSendToUser(acc, "/queue/group-notification", notification);
                }
                log.info("用户解除禁言: groupId={}, account={}, operator={}", groupId, targetAccount, operatorAccount);
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void leaveGroup(String account, Long groupId) {
        Group group = requireGroupExists(groupId);
        GroupMember member = findMemberEntity(groupId, account);

        if (member.getRole() == GroupMember.ROLE_OWNER) {
            throw new RuntimeException("群主不能直接退出，请先转让群主或解散群");
        }

        // 删除前收集所有成员账号，用于推送通知
        List<GroupMember> allMembers = groupMemberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId));
        List<String> memberAccounts = allMembers.stream()
                .map(GroupMember::getAccount)
                .collect(Collectors.toList());

        groupMemberMapper.deleteById(member.getId());

        String groupName = group.getName();
        User leaveUser = userMapper.selectById(account);
        String leaveName = leaveUser != null ? leaveUser.getName() : account;

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                GroupNotificationDto notification = GroupNotificationDto.builder()
                        .type("GROUP_MEMBER_LEFT")
                        .groupId(groupId)
                        .groupName(groupName)
                        .fromAccount(account)
                        .fromName(leaveName)
                        .message(leaveName + " 退出了群聊")
                        .build();
                for (String acc : memberAccounts) {
                    if (!acc.equals(account)) {
                        messagingTemplate.convertAndSendToUser(acc, "/queue/group-notification", notification);
                    }
                }
                log.info("用户退出群: account={}, groupId={}", account, groupId);
            }
        });
    }

    @Override
    public GroupMemberVO requireMember(Long groupId, String account) {
        GroupMember member = findMemberEntity(groupId, account);
        User user = userMapper.selectById(account);
        return GroupMemberVO.builder()
                .account(account)
                .name(user != null ? user.getName() : account)
                .role(member.getRole())
                .joinTime(member.getJoinTime())
                .build();
    }

    // ===== 内部工具方法 =====

    GroupMember findMemberEntity(Long groupId, String account) {
        GroupMember member = groupMemberMapper.selectOne(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId)
                        .eq(GroupMember::getAccount, account));
        if (member == null) {
            throw new RuntimeException("你不是该群成员");
        }
        return member;
    }

    private Group requireGroupExists(Long groupId) {
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new RuntimeException("群不存在");
        }
        return group;
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

    private int toInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number n) return n.intValue();
        return 0;
    }
}

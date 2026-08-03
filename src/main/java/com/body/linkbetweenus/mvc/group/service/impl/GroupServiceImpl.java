package com.body.linkbetweenus.mvc.group.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.body.linkbetweenus.dto.CreateGroupRequest;
import com.body.linkbetweenus.dto.GroupNotificationDto;
import com.body.linkbetweenus.dto.GroupVO;
import com.body.linkbetweenus.entity.Group;
import com.body.linkbetweenus.entity.GroupMember;
import com.body.linkbetweenus.entity.User;
import com.body.linkbetweenus.mvc.group.service.GroupService;
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
public class GroupServiceImpl implements GroupService {

    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final UserMapper userMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupVO createGroup(String ownerAccount, CreateGroupRequest request) {
        // 1. 创建群
        Group group = Group.builder()
                .name(request.getName())
                .owner(ownerAccount)
                .createTime(LocalDateTime.now())
                .build();
        groupMapper.insert(group);

        // 2. 群主加入成员表
        GroupMember ownerMember = GroupMember.builder()
                .groupId(group.getId())
                .account(ownerAccount)
                .role(GroupMember.ROLE_OWNER)
                .lastReadTime(LocalDateTime.now())
                .joinTime(LocalDateTime.now())
                .build();
        groupMemberMapper.insert(ownerMember);

        // 3. 添加初始成员
        List<String> initialMembers = request.getMembers();
        if (initialMembers != null && !initialMembers.isEmpty()) {
            // 去重 + 排除群主自己
            Set<String> uniqueMembers = initialMembers.stream()
                    .filter(m -> !m.equals(ownerAccount))
                    .collect(Collectors.toSet());

            for (String account : uniqueMembers) {
                // 校验用户存在
                if (userMapper.selectById(account) == null) {
                    throw new RuntimeException("用户 " + account + " 不存在");
                }
                GroupMember member = GroupMember.builder()
                        .groupId(group.getId())
                        .account(account)
                        .role(GroupMember.ROLE_MEMBER)
                        .lastReadTime(LocalDateTime.now())
                        .joinTime(LocalDateTime.now())
                        .build();
                groupMemberMapper.insert(member);
            }
        }

        User ownerUser = userMapper.selectById(ownerAccount);
        String ownerName = ownerUser != null ? ownerUser.getName() : ownerAccount;

        long memberCount = groupMemberMapper.selectCount(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, group.getId()));

        log.info("群创建成功: id={}, name={}, owner={}, memberCount={}", group.getId(), group.getName(), ownerAccount, memberCount);

        return GroupVO.builder()
                .id(group.getId())
                .name(group.getName())
                .owner(ownerAccount)
                .ownerName(ownerName)
                .memberCount(memberCount)
                .createTime(group.getCreateTime())
                .build();
    }

    @Override
    public GroupVO getGroupInfo(Long groupId) {
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new RuntimeException("群不存在");
        }

        User ownerUser = userMapper.selectById(group.getOwner());
        String ownerName = ownerUser != null ? ownerUser.getName() : group.getOwner();

        long memberCount = groupMemberMapper.selectCount(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId));

        return GroupVO.builder()
                .id(group.getId())
                .name(group.getName())
                .owner(group.getOwner())
                .ownerName(ownerName)
                .memberCount(memberCount)
                .createTime(group.getCreateTime())
                .build();
    }

    @Override
    public List<GroupVO> getMyGroups(String account) {
        List<GroupMember> memberships = groupMemberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getAccount, account));

        if (memberships.isEmpty()) {
            return List.of();
        }

        List<Long> groupIds = memberships.stream()
                .map(GroupMember::getGroupId)
                .collect(Collectors.toList());

        List<Group> groups = groupMapper.selectBatchIds(groupIds);

        // 批量查群主昵称
        Set<String> ownerAccounts = groups.stream()
                .map(Group::getOwner)
                .collect(Collectors.toSet());
        Map<String, User> userMap = userMapper.selectBatchIds(ownerAccounts).stream()
                .collect(Collectors.toMap(User::getAccount, u -> u));

        // 统计每个群的成员数
        Map<Long, Long> memberCountMap = new HashMap<>();
        for (Long gid : groupIds) {
            long count = groupMemberMapper.selectCount(
                    new LambdaQueryWrapper<GroupMember>()
                            .eq(GroupMember::getGroupId, gid));
            memberCountMap.put(gid, count);
        }

        return groups.stream()
                .map(g -> {
                    User u = userMap.get(g.getOwner());
                    return GroupVO.builder()
                            .id(g.getId())
                            .name(g.getName())
                            .owner(g.getOwner())
                            .ownerName(u != null ? u.getName() : g.getOwner())
                            .memberCount(memberCountMap.getOrDefault(g.getId(), 0L))
                            .createTime(g.getCreateTime())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renameGroup(String account, Long groupId, String newName) {
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new RuntimeException("群不存在");
        }

        // 检查权限：群主或管理员才能改名
        GroupMember member = groupMemberMapper.selectOne(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId)
                        .eq(GroupMember::getAccount, account));
        if (member == null) {
            throw new RuntimeException("你不是该群成员");
        }
        if (member.getRole() != GroupMember.ROLE_OWNER && member.getRole() != GroupMember.ROLE_ADMIN) {
            throw new RuntimeException("只有群主和管理员才能修改群名称");
        }

        String oldName = group.getName();
        group.setName(newName);
        groupMapper.updateById(group);

        log.info("群名称已修改: id={}, {} -> {}, operator={}", groupId, oldName, newName, account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dismissGroup(String account, Long groupId) {
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new RuntimeException("群不存在");
        }
        if (!group.getOwner().equals(account)) {
            throw new RuntimeException("只有群主才能解散群");
        }

        // 提前收集所有成员账号（删除前），用于广播通知
        List<GroupMember> members = groupMemberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId));
        List<String> memberAccounts = members.stream()
                .map(GroupMember::getAccount)
                .collect(Collectors.toList());

        // 删除所有成员
        groupMemberMapper.delete(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId));

        // 删除群
        groupMapper.deleteById(groupId);

        // 通知所有群成员：群已解散
        String groupName = group.getName();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                GroupNotificationDto notification = GroupNotificationDto.builder()
                        .type("GROUP_DISMISSED")
                        .groupId(groupId)
                        .groupName(groupName)
                        .fromAccount(account)
                        .build();
                for (String acc : memberAccounts) {
                    messagingTemplate.convertAndSendToUser(acc, "/queue/group-notification", notification);
                }

                log.info("群已解散: id={}, name={}, owner={}", groupId, groupName, account);
            }
        });
    }
}

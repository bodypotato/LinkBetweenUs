package com.body.linkbetweenus.mvc.friend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.body.linkbetweenus.dto.FriendNotificationDto;
import com.body.linkbetweenus.dto.FriendVO;
import com.body.linkbetweenus.dto.UserCacheVo;
import com.body.linkbetweenus.entity.Friend;
import com.body.linkbetweenus.entity.FriendRequest;
import com.body.linkbetweenus.entity.User;
import com.body.linkbetweenus.mvc.friend.service.FriendService;
import com.body.linkbetweenus.mvc.mapper.FriendMapper;
import com.body.linkbetweenus.mvc.mapper.FriendRequestMapper;
import com.body.linkbetweenus.mvc.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    private final UserMapper userMapper;
    private final FriendMapper friendMapper;
    private final FriendRequestMapper friendRequestMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public List<UserCacheVo> searchUsers(String currentAccount, String keyword) {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .like(User::getAccount, keyword)
                        .or()
                        .like(User::getName, keyword)
                        .last("LIMIT 20"));

        return users.stream()
                .filter(u -> !u.getAccount().equals(currentAccount))
                .map(UserCacheVo::from)
                .collect(Collectors.toList());
    }

    @Override
    public List<FriendVO> getFriendList(String account) {
        List<Friend> friendships = friendMapper.selectList(
                new LambdaQueryWrapper<Friend>()
                        .eq(Friend::getAccountA, account)
                        .or()
                        .eq(Friend::getAccountB, account));

        if (friendships.isEmpty()) {
            return List.of();
        }

        // 提取所有好友账号
        Set<String> friendAccounts = new HashSet<>();
        for (Friend f : friendships) {
            if (account.equals(f.getAccountA())) {
                friendAccounts.add(f.getAccountB());
            } else {
                friendAccounts.add(f.getAccountA());
            }
        }

        // 批量查询用户信息
        Map<String, User> userMap = userMapper.selectBatchIds(friendAccounts).stream()
                .collect(Collectors.toMap(User::getAccount, u -> u));

        // 构建 account → Friend 映射，用于提取备注
        Map<String, Friend> friendMap = friendships.stream()
                .collect(Collectors.toMap(
                        f -> account.equals(f.getAccountA()) ? f.getAccountB() : f.getAccountA(),
                        f -> f));

        return friendAccounts.stream()
                .map(fa -> {
                    User user = userMap.get(fa);
                    Friend f = friendMap.get(fa);
                    String remark = null;
                    if (f != null) {
                        remark = account.equals(f.getAccountA()) ? f.getRemarkByA() : f.getRemarkByB();
                    }
                    return FriendVO.builder()
                            .account(fa)
                            .name(user != null ? user.getName() : fa)
                            .remark(remark)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRemark(String account, String friendAccount, String remark) {
        String a = account.compareTo(friendAccount) < 0 ? account : friendAccount;
        String b = account.compareTo(friendAccount) < 0 ? friendAccount : account;

        Friend friend = friendMapper.selectOne(
                new LambdaQueryWrapper<Friend>()
                        .eq(Friend::getAccountA, a)
                        .eq(Friend::getAccountB, b));

        if (friend == null) {
            throw new RuntimeException("你们还不是好友");
        }

        if (account.equals(friend.getAccountA())) {
            friend.setRemarkByA(remark);
        } else {
            friend.setRemarkByB(remark);
        }
        friendMapper.updateById(friend);

        // 事务提交后通知本人 —— 覆盖 LBU_agent 代替用户改备注的场景
        User friendUser = userMapper.selectById(friendAccount);
        String friendName = friendUser != null ? friendUser.getName() : friendAccount;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                FriendNotificationDto notification = FriendNotificationDto.builder()
                        .type("REMARK_UPDATED")
                        .fromAccount(friendAccount)
                        .fromName(friendName)
                        .message(remark)
                        .build();
                messagingTemplate.convertAndSendToUser(account, "/queue/friend-request", notification);
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFriend(String account, String friendAccount) {
        String a = account.compareTo(friendAccount) < 0 ? account : friendAccount;
        String b = account.compareTo(friendAccount) < 0 ? friendAccount : account;

        Friend friend = friendMapper.selectOne(
                new LambdaQueryWrapper<Friend>()
                        .eq(Friend::getAccountA, a)
                        .eq(Friend::getAccountB, b));

        if (friend == null) {
            throw new RuntimeException("你们还不是好友");
        }

        friendMapper.deleteById(friend.getId());

        // 将两人之间已接受的好友请求标为"已解除"，允许后续重新发送
        FriendRequest fr = friendRequestMapper.selectOne(
                new LambdaQueryWrapper<FriendRequest>()
                        .eq(FriendRequest::getStatus, FriendRequest.STATUS_ACCEPTED)
                        .and(w -> w
                                .eq(FriendRequest::getFromAccount, a).eq(FriendRequest::getToAccount, b)
                                .or()
                                .eq(FriendRequest::getFromAccount, b).eq(FriendRequest::getToAccount, a)));
        if (fr != null) {
            fr.setStatus(FriendRequest.STATUS_DISSOLVED);
            fr.setUpdateTime(LocalDateTime.now());
            friendRequestMapper.updateById(fr);
        }

        // 事务提交后通知双方 —— 覆盖 LBU_agent 代替用户删好友的场景
        User operator = userMapper.selectById(account);
        String operatorName = operator != null ? operator.getName() : account;
        User removed = userMapper.selectById(friendAccount);
        String removedName = removed != null ? removed.getName() : friendAccount;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 操作者本人：好友列表移除对方
                FriendNotificationDto selfNotification = FriendNotificationDto.builder()
                        .type("FRIEND_REMOVED")
                        .fromAccount(friendAccount)
                        .fromName(removedName)
                        .build();
                messagingTemplate.convertAndSendToUser(account, "/queue/friend-request", selfNotification);
                // 被删除方：好友列表移除操作者
                FriendNotificationDto peerNotification = FriendNotificationDto.builder()
                        .type("FRIEND_REMOVED")
                        .fromAccount(account)
                        .fromName(operatorName)
                        .build();
                messagingTemplate.convertAndSendToUser(friendAccount, "/queue/friend-request", peerNotification);
                log.info("好友已删除: {} <-> {}", account, friendAccount);
            }
        });
    }
}

package com.body.linkbetweenus.mvc.friend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    private final UserMapper userMapper;
    private final FriendMapper friendMapper;
    private final FriendRequestMapper friendRequestMapper;

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
    }
}

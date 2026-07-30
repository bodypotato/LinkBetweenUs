package com.body.linkbetweenus.mvc.friend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.body.linkbetweenus.dto.FriendNotificationDto;
import com.body.linkbetweenus.dto.FriendRequestSendRequest;
import com.body.linkbetweenus.dto.FriendRequestVO;
import com.body.linkbetweenus.entity.Friend;
import com.body.linkbetweenus.entity.FriendRequest;
import com.body.linkbetweenus.entity.User;
import com.body.linkbetweenus.mvc.friend.service.FriendRequestService;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FriendRequestServiceImpl implements FriendRequestService {

    private final FriendRequestMapper friendRequestMapper;
    private final FriendMapper friendMapper;
    private final UserMapper userMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendFriendRequest(String fromAccount, FriendRequestSendRequest request) {
        String toAccount = request.getToAccount();

        // 1. 不能加自己
        if (fromAccount.equals(toAccount)) {
            throw new RuntimeException("不能添加自己为好友");
        }

        // 2. 目标用户必须存在
        User targetUser = userMapper.selectById(toAccount);
        if (targetUser == null) {
            throw new RuntimeException("该用户不存在");
        }

        // 3. 检查是否已经是好友
        if (isAlreadyFriend(fromAccount, toAccount)) {
            throw new RuntimeException("你们已经是好友了");
        }

        // 4. 检查是否已有请求记录
        FriendRequest existing = friendRequestMapper.selectOne(
                new LambdaQueryWrapper<FriendRequest>()
                        .eq(FriendRequest::getFromAccount, fromAccount)
                        .eq(FriendRequest::getToAccount, toAccount));

        Long requestId;
        if (existing != null) {
            if (existing.getStatus() == FriendRequest.STATUS_PENDING) {
                throw new RuntimeException("已向该用户发送过好友请求，请等待对方处理");
            }
            if (existing.getStatus() == FriendRequest.STATUS_ACCEPTED) {
                throw new RuntimeException("你们已经是好友了");
            }
            // STATUS_REJECTED / STATUS_DISSOLVED: 允许重新发送，更新已有记录
            existing.setStatus(FriendRequest.STATUS_PENDING);
            existing.setMessage(request.getMessage());
            existing.setCreateTime(LocalDateTime.now());
            existing.setUpdateTime(LocalDateTime.now());
            friendRequestMapper.updateById(existing);
            requestId = existing.getId();
        } else {
            FriendRequest fr = FriendRequest.builder()
                    .fromAccount(fromAccount)
                    .toAccount(toAccount)
                    .status(FriendRequest.STATUS_PENDING)
                    .message(request.getMessage())
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            friendRequestMapper.insert(fr);
            requestId = fr.getId();
        }

        // 5. 查发起方昵称
        User fromUser = userMapper.selectById(fromAccount);
        String fromName = fromUser != null ? fromUser.getName() : fromAccount;
        String message = request.getMessage();

        // 6. 事务提交后再推送 WebSocket 通知，确保接收方刷新时能查到已提交的数据
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                FriendNotificationDto notification = FriendNotificationDto.builder()
                        .type("FRIEND_REQUEST")
                        .requestId(requestId)
                        .fromAccount(fromAccount)
                        .fromName(fromName)
                        .message(message)
                        .build();
                messagingTemplate.convertAndSendToUser(toAccount, "/queue/friend-request", notification);
                log.info("好友请求已发送: {} -> {}, requestId={}", fromAccount, toAccount, requestId);
            }
        });
    }

    @Override
    public List<FriendRequestVO> getIncomingRequests(String account) {
        List<FriendRequest> requests = friendRequestMapper.selectList(
                new LambdaQueryWrapper<FriendRequest>()
                        .eq(FriendRequest::getToAccount, account)
                        .eq(FriendRequest::getStatus, FriendRequest.STATUS_PENDING)
                        .orderByDesc(FriendRequest::getCreateTime));

        return toVOList(requests);
    }

    @Override
    public List<FriendRequestVO> getOutgoingRequests(String account) {
        List<FriendRequest> requests = friendRequestMapper.selectList(
                new LambdaQueryWrapper<FriendRequest>()
                        .eq(FriendRequest::getFromAccount, account)
                        .eq(FriendRequest::getStatus, FriendRequest.STATUS_PENDING)
                        .orderByDesc(FriendRequest::getCreateTime));

        return toVOList(requests);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptFriendRequest(String account, Long requestId) {
        FriendRequest fr = findAndValidateOwnership(requestId, account);

        // 更新请求状态
        fr.setStatus(FriendRequest.STATUS_ACCEPTED);
        fr.setUpdateTime(LocalDateTime.now());
        friendRequestMapper.updateById(fr);

        // 插入好友关系（字典序）
        insertFriendship(fr.getFromAccount(), fr.getToAccount());

        // 通知请求发起方（事务提交后推送，确保对方刷新时能查到已提交数据）
        String fromAccount = fr.getFromAccount();
        String toAccount = fr.getToAccount();
        User acceptor = userMapper.selectById(account);
        String acceptorName = acceptor != null ? acceptor.getName() : account;

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                FriendNotificationDto notification = FriendNotificationDto.builder()
                        .type("FRIEND_ACCEPTED")
                        .requestId(requestId)
                        .fromAccount(account)
                        .fromName(acceptorName)
                        .build();
                messagingTemplate.convertAndSendToUser(fromAccount, "/queue/friend-request", notification);
                log.info("好友请求已接受: {} <-> {}, requestId={}", fromAccount, toAccount, requestId);
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectFriendRequest(String account, Long requestId) {
        FriendRequest fr = findAndValidateOwnership(requestId, account);

        fr.setStatus(FriendRequest.STATUS_REJECTED);
        fr.setUpdateTime(LocalDateTime.now());
        friendRequestMapper.updateById(fr);

        // 通知请求发起方（事务提交后推送，确保对方刷新时能查到已提交数据）
        String fromAccount = fr.getFromAccount();
        String toAccount = fr.getToAccount();
        User rejector = userMapper.selectById(account);
        String rejectorName = rejector != null ? rejector.getName() : account;

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                FriendNotificationDto notification = FriendNotificationDto.builder()
                        .type("FRIEND_REJECTED")
                        .requestId(requestId)
                        .fromAccount(account)
                        .fromName(rejectorName)
                        .build();
                messagingTemplate.convertAndSendToUser(fromAccount, "/queue/friend-request", notification);
                log.info("好友请求已拒绝: {} -> {}, requestId={}", fromAccount, toAccount, requestId);
            }
        });
    }

    // ===== 私有方法 =====

    private boolean isAlreadyFriend(String account1, String account2) {
        String a = account1.compareTo(account2) < 0 ? account1 : account2;
        String b = account1.compareTo(account2) < 0 ? account2 : account1;
        return friendMapper.selectOne(
                new LambdaQueryWrapper<Friend>()
                        .eq(Friend::getAccountA, a)
                        .eq(Friend::getAccountB, b)) != null;
    }

    private void insertFriendship(String account1, String account2) {
        String a = account1.compareTo(account2) < 0 ? account1 : account2;
        String b = account1.compareTo(account2) < 0 ? account2 : account1;
        Friend friend = Friend.builder()
                .accountA(a)
                .accountB(b)
                .createTime(LocalDateTime.now())
                .build();
        friendMapper.insert(friend);
    }

    private FriendRequest findAndValidateOwnership(Long requestId, String account) {
        FriendRequest fr = friendRequestMapper.selectById(requestId);
        if (fr == null) {
            throw new RuntimeException("好友请求不存在");
        }
        if (fr.getStatus() != FriendRequest.STATUS_PENDING) {
            throw new RuntimeException("该好友请求已处理");
        }
        if (!fr.getToAccount().equals(account)) {
            throw new RuntimeException("无权操作该好友请求");
        }
        return fr;
    }

    private List<FriendRequestVO> toVOList(List<FriendRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        // 收集所有关联账号，批量查询用户信息
        Set<String> accounts = requests.stream()
                .flatMap(r -> Set.of(r.getFromAccount(), r.getToAccount()).stream())
                .collect(Collectors.toSet());
        Map<String, User> userMap = userMapper.selectBatchIds(accounts).stream()
                .collect(Collectors.toMap(User::getAccount, u -> u));

        List<FriendRequestVO> vos = new ArrayList<>();
        for (FriendRequest r : requests) {
            User fromUser = userMap.get(r.getFromAccount());
            User toUser = userMap.get(r.getToAccount());
            vos.add(FriendRequestVO.builder()
                    .id(r.getId())
                    .fromAccount(r.getFromAccount())
                    .fromName(fromUser != null ? fromUser.getName() : r.getFromAccount())
                    .toAccount(r.getToAccount())
                    .toName(toUser != null ? toUser.getName() : r.getToAccount())
                    .status(r.getStatus())
                    .message(r.getMessage())
                    .createTime(r.getCreateTime())
                    .build());
        }
        return vos;
    }
}

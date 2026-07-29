package com.body.linkbetweenus.mvc.friend.service;

import com.body.linkbetweenus.dto.FriendVO;
import com.body.linkbetweenus.dto.UserCacheVo;

import java.util.List;

public interface FriendService {

    /**
     * 搜索用户（按账号或昵称模糊匹配）
     */
    List<UserCacheVo> searchUsers(String currentAccount, String keyword);

    /**
     * 获取好友列表
     */
    List<FriendVO> getFriendList(String account);

    /**
     * 删除/移除好友
     */
    void removeFriend(String account, String friendAccount);
}

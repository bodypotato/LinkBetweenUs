package com.body.linkbetweenus.mvc.group.service;

import com.body.linkbetweenus.dto.CreateGroupRequest;
import com.body.linkbetweenus.dto.GroupVO;

import java.util.List;

public interface GroupService {

    /** 创建群（自动成为群主） */
    GroupVO createGroup(String ownerAccount, CreateGroupRequest request);

    /** 获取群信息 */
    GroupVO getGroupInfo(Long groupId);

    /** 我的群列表 */
    List<GroupVO> getMyGroups(String account);

    /** 解散群（仅群主） */
    void dismissGroup(String account, Long groupId);

    /** 修改群名称（群主+管理员） */
    void renameGroup(String account, Long groupId, String newName);
}

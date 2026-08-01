package com.body.linkbetweenus.mvc.group.service;

import com.body.linkbetweenus.dto.GroupMemberVO;

import java.util.List;

public interface GroupMemberService {

    /** 获取群成员列表 */
    List<GroupMemberVO> getMembers(Long groupId);

    /** 踢人（群主可以踢任何人，管理员只能踢普通成员） */
    void kickMember(String operatorAccount, Long groupId, String targetAccount);

    /** 提升为管理员（仅群主） */
    void promoteMember(String operatorAccount, Long groupId, String targetAccount);

    /** 解除管理员身份（仅群主） */
    void demoteMember(String operatorAccount, Long groupId, String targetAccount);

    /** 禁言（群主可禁任何人，管理员只能禁普通成员） */
    void muteMember(String operatorAccount, Long groupId, String targetAccount, int minutes);

    /** 解除禁言 */
    void unmuteMember(String operatorAccount, Long groupId, String targetAccount);

    /** 退出群 */
    void leaveGroup(String account, Long groupId);

    /** 检查是否为群成员，返回成员信息；不是则抛异常 */
    GroupMemberVO requireMember(Long groupId, String account);
}

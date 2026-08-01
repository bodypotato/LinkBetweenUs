package com.body.linkbetweenus.mvc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.body.linkbetweenus.entity.GroupMember;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface GroupMemberMapper extends BaseMapper<GroupMember> {

    /**
     * 查某群的成员列表（含 unread_count）
     */
    @Select("SELECT gm.account, gm.role, gm.last_read_time, gm.muted_until, gm.join_time, " +
            "  (SELECT COUNT(*) FROM LBU_Group_Message " +
            "   WHERE group_id = gm.group_id AND create_time > gm.last_read_time) AS unread_count " +
            "FROM LBU_Group_Member gm " +
            "WHERE gm.group_id = #{groupId} " +
            "ORDER BY gm.role ASC, gm.join_time ASC")
    List<Map<String, Object>> findMemberRows(@Param("groupId") Long groupId);
}

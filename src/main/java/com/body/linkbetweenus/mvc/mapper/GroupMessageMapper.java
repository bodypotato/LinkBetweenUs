package com.body.linkbetweenus.mvc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.body.linkbetweenus.entity.GroupMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface GroupMessageMapper extends BaseMapper<GroupMessage> {

    /**
     * 查询用户的群会话列表（按最后消息时间倒序，含未读数）
     */
    @Select("SELECT g.id AS group_id, g.name AS group_name, " +
            "  MAX(gm2.create_time) AS last_time, " +
            "  (SELECT COUNT(*) FROM LBU_Group_Message " +
            "   WHERE group_id = g.id AND create_time > COALESCE(gm.last_read_time, '1970-01-01')) AS unread_count, " +
            "  (SELECT content FROM LBU_Group_Message " +
            "   WHERE group_id = g.id ORDER BY create_time DESC LIMIT 1) AS last_content " +
            "FROM LBU_Group_Member gm " +
            "JOIN LBU_Group g ON g.id = gm.group_id " +
            "LEFT JOIN LBU_Group_Message gm2 ON gm2.group_id = g.id " +
            "WHERE gm.account = #{account} " +
            "GROUP BY g.id, g.name, gm.last_read_time " +
            "ORDER BY last_time DESC")
    List<Map<String, Object>> findGroupConversationRows(@Param("account") String account);

    /**
     * 查询某群某用户的未读消息数
     */
    @Select("SELECT COUNT(*) FROM LBU_Group_Message " +
            "WHERE group_id = #{groupId} AND create_time > COALESCE( " +
            "  (SELECT last_read_time FROM LBU_Group_Member WHERE group_id = #{groupId} AND account = #{account}), " +
            "  '1970-01-01')")
    long countUnread(@Param("groupId") Long groupId, @Param("account") String account);
}

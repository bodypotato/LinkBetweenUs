package com.body.linkbetweenus.mvc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.body.linkbetweenus.entity.Message;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 查询会话列表 — 按最后消息时间倒序，含未读计数和最后一条消息内容
     */
    @Select("SELECT " +
            "  other_account, last_time, unread_count, " +
            "  (SELECT content FROM LBU_Message " +
            "   WHERE ((from_account = #{account} AND to_account = other_account) " +
            "       OR (from_account = other_account AND to_account = #{account})) " +
            "   ORDER BY create_time DESC LIMIT 1) AS last_content " +
            "FROM ( " +
            "  SELECT " +
            "    CASE WHEN from_account = #{account} THEN to_account ELSE from_account END AS other_account, " +
            "    MAX(create_time) AS last_time, " +
            "    COUNT(CASE WHEN to_account = #{account} AND status < 2 THEN 1 END) AS unread_count " +
            "  FROM LBU_Message " +
            "  WHERE from_account = #{account} OR to_account = #{account} " +
            "  GROUP BY CASE WHEN from_account = #{account} THEN to_account ELSE from_account END " +
            ") t " +
            "ORDER BY last_time DESC")
    List<Map<String, Object>> findConversationRows(@Param("account") String account);

    /**
     * 查询与指定用户之间未读消息数
     */
    @Select("SELECT COUNT(*) FROM LBU_Message " +
            "WHERE from_account = #{fromAccount} AND to_account = #{toAccount} AND status < 2")
    long countUnread(@Param("fromAccount") String fromAccount,
                     @Param("toAccount") String toAccount);
}

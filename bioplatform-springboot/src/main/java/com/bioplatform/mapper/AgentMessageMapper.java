package com.bioplatform.mapper;

import com.bioplatform.entity.AgentMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI Agent消息Mapper接口
 *
 * @author luosg
 */
@Mapper
public interface AgentMessageMapper {

    int insert(AgentMessage message);

    AgentMessage selectById(@Param("id") Long id);

    List<AgentMessage> selectAll(AgentMessage message);

    int updateById(AgentMessage message);

    int deleteById(@Param("id") Long id);

    List<AgentMessage> selectByConversationId(@Param("conversationId") Long conversationId, @Param("role") String role);

    List<AgentMessage> selectRecentByConversationId(@Param("conversationId") Long conversationId, @Param("limit") int limit);
}

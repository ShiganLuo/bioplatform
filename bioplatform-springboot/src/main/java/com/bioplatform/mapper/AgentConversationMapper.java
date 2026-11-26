package com.bioplatform.mapper;

import com.bioplatform.entity.AgentConversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI Agent对话Mapper接口
 *
 * @author luosg
 */
@Mapper
public interface AgentConversationMapper {

    int insert(AgentConversation conversation);

    AgentConversation selectById(@Param("id") Long id);

    List<AgentConversation> selectAll(AgentConversation conversation);

    int updateById(AgentConversation conversation);

    int deleteById(@Param("id") Long id);

    /** 解绑项目(项目删除时保留对话历史,只清 project_id) */
    int unbindProject(@Param("projectId") Long projectId);

    List<AgentConversation> selectByUserId(@Param("userId") Long userId);

    List<AgentConversation> selectByProjectId(@Param("projectId") Long projectId);
}

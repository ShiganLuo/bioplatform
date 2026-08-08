package com.bioplatform.mapper;

import com.bioplatform.entity.AgentTool;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI Agent工具Mapper接口
 *
 * @author luosg
 */
@Mapper
public interface AgentToolMapper {

    int insert(AgentTool tool);

    AgentTool selectById(@Param("id") Long id);

    List<AgentTool> selectAll(AgentTool tool);

    int updateById(AgentTool tool);

    int deleteById(@Param("id") Long id);

    List<AgentTool> selectEnabled();

    List<AgentTool> selectByCategory(@Param("category") String category);
}

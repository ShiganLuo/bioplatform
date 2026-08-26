package com.bioplatform.mapper;

import com.bioplatform.entity.ComputeNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ComputeNodeMapper {
    int insert(ComputeNode node);
    int updateById(ComputeNode node);
    int deleteById(@Param("id") Long id);
    ComputeNode selectById(@Param("id") Long id);
    ComputeNode selectByNodeId(@Param("nodeId") String nodeId);
    List<ComputeNode> selectAll();
    List<ComputeNode> selectEnabled();
    int updateHealth(@Param("nodeId") String nodeId, @Param("healthy") Integer healthy,
                     @Param("cpuCores") Integer cpuCores, @Param("memoryMb") Long memoryMb);
}

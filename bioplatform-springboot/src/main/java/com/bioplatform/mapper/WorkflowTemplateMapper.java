package com.bioplatform.mapper;

import com.bioplatform.entity.WorkflowTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 工作流模板 Mapper
 *
 * @author luosg
 */
@Mapper
public interface WorkflowTemplateMapper {

    int insert(WorkflowTemplate template);

    WorkflowTemplate selectById(@Param("id") Long id);

    List<WorkflowTemplate> selectAll(WorkflowTemplate query);

    int updateById(WorkflowTemplate template);

    int deleteById(@Param("id") Long id);
}

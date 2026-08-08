package com.bioplatform.mapper;

import com.bioplatform.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目Mapper接口
 *
 * @author luosg
 */
@Mapper
public interface ProjectMapper {

    int insert(Project project);

    Project selectById(@Param("id") Long id);

    List<Project> selectAll(Project project);

    int updateById(Project project);

    int deleteById(@Param("id") Long id);

    List<Project> selectByOwnerId(@Param("ownerId") Long ownerId, @Param("status") Integer status);

    List<Project> searchByName(@Param("name") String name, @Param("ownerId") Long ownerId, @Param("status") Integer status);

    int countByOwnerId(@Param("ownerId") Long ownerId);

    List<Project> selectPublic();
}

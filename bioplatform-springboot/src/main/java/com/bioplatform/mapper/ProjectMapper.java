package com.bioplatform.mapper;

import com.bioplatform.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

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

    List<Project> selectByOwnerId(@Param("ownerId") Long ownerId, @Param("status") Integer status,
                                  @Param("name") String name, @Param("organism") String organism);

    List<Project> searchByName(@Param("name") String name, @Param("ownerId") Long ownerId, @Param("status") Integer status);

    int countByOwnerId(@Param("ownerId") Long ownerId);

    List<Project> selectPublic();

    List<String> selectDistinctOrganisms();

    List<String> selectDistinctGenomeVersions();

    /** 管理后台列表：联表查父项目名+创建者用户名 */
    List<Map<String, Object>> selectAdminList(@Param("name") String name, @Param("organism") String organism);

    /** 获取顶级项目列表（用于父项目下拉选择） */
    List<Project> selectParentCandidates();

    /** 解除子项目绑定（父项目删除时） */
    int unbindChildren(@Param("parentId") Long parentId);

    /** 解除项目的父项目绑定（设为NULL） */
    int clearParent(@Param("id") Long id);
}

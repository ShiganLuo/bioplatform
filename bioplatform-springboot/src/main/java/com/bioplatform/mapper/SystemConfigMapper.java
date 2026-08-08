package com.bioplatform.mapper;

import com.bioplatform.entity.SystemConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统配置Mapper接口
 *
 * @author luosg
 */
@Mapper
public interface SystemConfigMapper {

    int insert(SystemConfig config);

    SystemConfig selectById(@Param("id") Long id);

    List<SystemConfig> selectAll(SystemConfig config);

    int updateById(SystemConfig config);

    int deleteById(@Param("id") Long id);

    SystemConfig selectByKey(@Param("configKey") String configKey);

    int upsertByKey(SystemConfig config);
}

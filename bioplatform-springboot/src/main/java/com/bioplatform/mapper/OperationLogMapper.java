package com.bioplatform.mapper;

import com.bioplatform.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 操作日志Mapper接口
 *
 * @author luosg
 */
@Mapper
public interface OperationLogMapper {

    int insert(OperationLog log);

    OperationLog selectById(@Param("id") Long id);

    List<OperationLog> selectAll(OperationLog log);

    int updateById(OperationLog log);

    int deleteById(@Param("id") Long id);

    List<OperationLog> selectWithFilter(OperationLog log);
}

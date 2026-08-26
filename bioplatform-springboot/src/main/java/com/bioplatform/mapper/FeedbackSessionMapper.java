package com.bioplatform.mapper;

import com.bioplatform.entity.FeedbackSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 反馈会话Mapper
 *
 * @author luosg
 */
@Mapper
public interface FeedbackSessionMapper {

    int insert(FeedbackSession session);

    FeedbackSession selectById(@Param("id") Long id);

    FeedbackSession selectOpenByUserId(@Param("userId") Long userId);

    List<FeedbackSession> selectOpenSessions();

    List<FeedbackSession> selectByUserId(@Param("userId") Long userId);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}

package com.bioplatform.mapper;

import com.bioplatform.entity.FeedbackMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 反馈消息Mapper
 *
 * @author luosg
 */
@Mapper
public interface FeedbackMessageMapper {

    int insert(FeedbackMessage message);

    List<FeedbackMessage> selectBySessionId(@Param("sessionId") Long sessionId);

    FeedbackMessage selectLatestBySessionId(@Param("sessionId") Long sessionId);

    int countUnreadBySessionId(@Param("sessionId") Long sessionId, @Param("senderType") String senderType);
}

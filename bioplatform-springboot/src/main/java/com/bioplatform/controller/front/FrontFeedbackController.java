package com.bioplatform.controller.front;

import com.bioplatform.common.util.LoginUserHolder;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.entity.FeedbackMessage;
import com.bioplatform.entity.FeedbackSession;
import com.bioplatform.service.FeedbackService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 前台反馈接口
 *
 * @author luosg
 */
@RestController
@RequestMapping("/api/front/feedback")
public class FrontFeedbackController {

    private final FeedbackService feedbackService;

    public FrontFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /**
     * 获取当前用户的open会话
     */
    @GetMapping("/session")
    public ApiResponse<FeedbackSession> getMySession() {
        Long userId = LoginUserHolder.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error(400, "请先登录");
        }
        FeedbackSession session = feedbackService.getOrCreateSession(userId,
                LoginUserHolder.getCurrentUsername());
        return ApiResponse.success(session);
    }

    /**
     * 获取会话历史消息
     */
    @GetMapping("/messages")
    public ApiResponse<List<FeedbackMessage>> getMessages(@RequestParam Long sessionId) {
        return ApiResponse.success(feedbackService.getMessages(sessionId));
    }
}

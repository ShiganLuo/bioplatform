package com.bioplatform.controller.admin;

import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.entity.FeedbackMessage;
import com.bioplatform.entity.FeedbackSession;
import com.bioplatform.service.FeedbackService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台反馈管理接口
 *
 * @author luosg
 */
@RestController
@RequestMapping("/api/admin/feedback")
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    public AdminFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /**
     * 获取所有open会话
     */
    @GetMapping("/sessions")
    public ApiResponse<List<FeedbackSession>> listOpenSessions() {
        return ApiResponse.success(feedbackService.listOpenSessions());
    }

    /**
     * 获取会话消息列表
     */
    @GetMapping("/sessions/{id}/messages")
    public ApiResponse<List<FeedbackMessage>> getMessages(@PathVariable Long id) {
        return ApiResponse.success(feedbackService.getMessages(id));
    }

    /**
     * 关闭会话
     */
    @PutMapping("/sessions/{id}/close")
    public ApiResponse<Void> closeSession(@PathVariable Long id) {
        feedbackService.closeSession(id);
        return ApiResponse.success();
    }
}

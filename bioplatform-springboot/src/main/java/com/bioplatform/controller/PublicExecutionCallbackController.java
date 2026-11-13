package com.bioplatform.controller;

import com.bioplatform.common.util.ExecCallbackTokenUtil;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.entity.PipelineExecution;
import com.bioplatform.mapper.PipelineExecutionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 执行回调接口(白名单免登录,凭每执行专属 HMAC 令牌):
 * 流程框架(run.py 等)在终止或正常执行完毕后调用,平台据此更新执行状态。
 * <p>
 * 契约:POST /api/public/executions/callback
 * { "executionId": 12, "token": "<BIPLATFORM_CALLBACK_TOKEN>",
 *   "status": "SUCCESS|FAILED|CANCELLED", "message": "可选,失败原因等", "exitCode": 0 }
 * <p>
 * executionId/token 由派发时注入执行环境(BIPLATFORM_EXEC_ID / BIPLATFORM_CALLBACK_TOKEN),
 * 回调 URL 注入 BIPLATFORM_CALLBACK_URL(默认 http://127.0.0.1:8083/api/public/executions/callback)。
 * 幂等:执行已终态时重复回调直接返回成功。
 *
 * @author luosg
 */
@Slf4j
@RestController
@RequestMapping("/api/public/executions")
public class PublicExecutionCallbackController {

    private final PipelineExecutionMapper executionMapper;
    private final ExecCallbackTokenUtil tokenUtil;

    public PublicExecutionCallbackController(PipelineExecutionMapper executionMapper,
                                             ExecCallbackTokenUtil tokenUtil) {
        this.executionMapper = executionMapper;
        this.tokenUtil = tokenUtil;
    }

    public record CallbackRequest(Long executionId, String token, String status,
                                  String message, Integer exitCode) {
    }

    @PostMapping("/callback")
    public ApiResponse<Void> callback(@RequestBody CallbackRequest req) {
        if (req.executionId() == null || req.status() == null) {
            return ApiResponse.error(400, "executionId/status 必填");
        }
        if (!tokenUtil.verify(req.executionId(), req.token())) {
            log.warn("执行回调令牌校验失败: executionId={}", req.executionId());
            return ApiResponse.error(403, "回调令牌无效");
        }
        String status = req.status().toUpperCase();
        if (!List.of("SUCCESS", "FAILED", "CANCELLED").contains(status)) {
            return ApiResponse.error(400, "status 取值无效(应为 SUCCESS/FAILED/CANCELLED)");
        }
        PipelineExecution exec = executionMapper.selectById(req.executionId());
        if (exec == null) {
            return ApiResponse.error(404, "执行记录不存在");
        }
        // 幂等:已终态不覆盖
        if (!"RUNNING".equals(exec.getStatus()) && !"PENDING".equals(exec.getStatus())) {
            log.info("执行回调忽略(已终态): executionId={}, 当前={}, 回报={}", exec.getId(), exec.getStatus(), status);
            return ApiResponse.success(null);
        }
        exec.setStatus(status);
        exec.setFinishedAt(LocalDateTime.now());
        exec.setHeartbeatAt(LocalDateTime.now());
        if (req.message() != null && !req.message().isBlank()) {
            String msg = req.message();
            exec.setErrorLog(msg.length() > 20_000 ? msg.substring(msg.length() - 20_000) : msg);
        }
        executionMapper.updateById(exec);
        log.info("执行回调更新: executionId={}, status={}, exitCode={}", exec.getId(), status, req.exitCode());
        return ApiResponse.success(null);
    }
}

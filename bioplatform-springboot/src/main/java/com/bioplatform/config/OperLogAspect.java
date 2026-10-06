package com.bioplatform.config;

import com.bioplatform.common.annotation.OperLog;
import com.bioplatform.common.util.LoginUserHolder;
import com.bioplatform.common.util.RequestContextUtil;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.entity.OperationLog;
import com.bioplatform.entity.User;
import com.bioplatform.service.OperLogService;
import com.bioplatform.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 操作日志切面
 * 拦截@OperLog注解的方法，自动记录操作日志：
 * 操作者用户名快照、模块、接口路径(POST /api/...)、脱敏后的参数、成功/失败状态与结果摘要。
 *
 * @author luosg
 */
@Aspect
@Component
public class OperLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperLogAspect.class);

    /** 敏感字段名（大小写不敏感），序列化时统一替换为 *** */
    private static final Pattern SENSITIVE_KEY =
            Pattern.compile("(?i).*(password|passwd|pwd|token|secret|api[-_]?key|authorization|credential).*");

    private static final int MAX_PARAMS_LEN = 2000;
    private static final int MAX_RESULT_LEN = 500;

    private final OperLogService operLogService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public OperLogAspect(OperLogService operLogService, UserService userService, ObjectMapper objectMapper) {
        this.operLogService = operLogService;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    /**
     * 切点：匹配所有标注了@OperLog注解的方法
     */
    @Pointcut("@annotation(com.bioplatform.common.annotation.OperLog)")
    public void operLogPointcut() {
    }

    /**
     * 环绕通知：在方法执行前后记录操作日志
     */
    @Around("operLogPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 获取@OperLog注解信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        OperLog operLogAnnotation = method.getAnnotation(OperLog.class);

        OperationLog operationLog = new OperationLog();
        operationLog.setModule(operLogAnnotation.module());
        operationLog.setOperation(operLogAnnotation.operation());

        // 请求上下文：接口路径 + 客户端IP
        String endpoint = RequestContextUtil.getEndpoint();
        operationLog.setMethod(endpoint != null ? endpoint
                : joinPoint.getTarget().getClass().getName() + "." + method.getName());
        operationLog.setIp(RequestContextUtil.getClientIp());

        // 当前登录用户（用户名快照，用户删除后日志仍可读）
        Long currentUserId = LoginUserHolder.getCurrentUserId();
        operationLog.setUserId(currentUserId);
        operationLog.setUsername(resolveUsername(currentUserId, LoginUserHolder.getCurrentUsername()));

        // 请求参数（跳过不可序列化对象 + 敏感字段脱敏）
        operationLog.setParams(serializeParams(joinPoint.getArgs()));

        Object result = null;
        boolean success = true;
        Throwable error = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            success = false;
            error = e;
            throw e;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            applyResultSummary(operationLog, result, success, error);
            operationLog.setCreatedAt(LocalDateTime.now());

            try {
                operLogService.save(operationLog);
            } catch (Exception e) {
                log.error("Failed to save operation log: {}", e.getMessage(), e);
            }
            log.debug("操作日志: {} {} user={} status={} cost={}ms",
                    operationLog.getModule(), operationLog.getOperation(),
                    operationLog.getUsername(), operationLog.getStatus(), executionTime);
        }
    }

    /**
     * 结果摘要与状态判定：
     * - ApiResponse：code==200 → SUCCESS，否则 FAIL，result 存 code+message
     * - 抛异常：FAIL，result 存异常消息
     * - 其他返回类型：SUCCESS / "成功"
     */
    private void applyResultSummary(OperationLog entry, Object result, boolean success, Throwable error) {
        if (error != null) {
            entry.setStatus("FAIL");
            entry.setResult(truncate(error.getMessage() != null
                    ? error.getClass().getSimpleName() + ": " + error.getMessage()
                    : error.getClass().getSimpleName(), MAX_RESULT_LEN));
            return;
        }
        if (result instanceof ApiResponse<?> apiResponse) {
            boolean ok = apiResponse.code() == 200;
            entry.setStatus(ok ? "SUCCESS" : "FAIL");
            entry.setResult(truncate("code=" + apiResponse.code() + " " + apiResponse.message(), MAX_RESULT_LEN));
            return;
        }
        entry.setStatus(success ? "SUCCESS" : "FAIL");
        entry.setResult(success ? "成功" : "失败");
    }

    /**
     * 序列化方法参数：跳过 servlet/文件/校验对象，敏感字段替换为 ***
     */
    private String serializeParams(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        try {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object arg : args) {
                if (arg == null || isNonLoggable(arg)) {
                    continue;
                }
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                JsonNode tree = objectMapper.valueToTree(arg);
                sb.append(maskNode(tree).toString());
            }
            sb.append("]");
            return truncate(sb.toString(), MAX_PARAMS_LEN);
        } catch (Exception e) {
            log.warn("Failed to serialize method params: {}", e.getMessage());
            return "序列化失败";
        }
    }

    /** 这些类型序列化无意义或会失败，直接跳过 */
    private boolean isNonLoggable(Object arg) {
        return arg instanceof MultipartFile
                || arg instanceof ServletRequest
                || arg instanceof ServletResponse
                || arg instanceof BindingResult
                || arg instanceof java.io.InputStream;
    }

    /** 递归遍历 JSON 树，把敏感字段的值替换为 *** */
    private JsonNode maskNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (SENSITIVE_KEY.matcher(field.getKey()).matches()) {
                    obj.put(field.getKey(), "***");
                } else {
                    obj.set(field.getKey(), maskNode(field.getValue()));
                }
            }
            return obj;
        }
        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                arr.set(i, maskNode(arr.get(i)));
            }
            return arr;
        }
        return node;
    }

    private String resolveUsername(Long userId, String usernameFromContext) {
        if (usernameFromContext != null && !usernameFromContext.isBlank()) {
            return usernameFromContext;
        }
        if (userId != null) {
            try {
                User user = userService.getUserById(userId);
                if (user != null) {
                    return user.getUsername();
                }
            } catch (Exception e) {
                log.warn("Failed to resolve username for userId={}: {}", userId, e.getMessage());
            }
        }
        return null;
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLen ? value.substring(0, maxLen) + "..." : value;
    }

}

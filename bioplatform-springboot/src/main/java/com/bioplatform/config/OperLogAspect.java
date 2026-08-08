package com.bioplatform.config;

import com.bioplatform.common.annotation.OperLog;
import com.bioplatform.common.util.LoginUserHolder;
import com.bioplatform.entity.OperationLog;
import com.bioplatform.service.OperLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 操作日志切面
 * 拦截@OperLog注解的方法，自动记录操作日志
 *
 * @author luosg
 */
@Aspect
@Component
public class OperLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperLogAspect.class);

    private final OperLogService operLogService;
    private final ObjectMapper objectMapper;

    public OperLogAspect(OperLogService operLogService, ObjectMapper objectMapper) {
        this.operLogService = operLogService;
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

        // 构建操作日志实体
        OperationLog operationLog = new OperationLog();
        operationLog.setOperation(operLogAnnotation.operation());
        operationLog.setMethod(joinPoint.getTarget().getClass().getName() + "." + method.getName());

        // 获取当前登录用户
        Long currentUserId = LoginUserHolder.getCurrentUserId();
        operationLog.setUserId(currentUserId);

        // 获取请求参数
        try {
            Object[] args = joinPoint.getArgs();
            String params = objectMapper.writeValueAsString(args);
            // 截断过长的参数
            if (params.length() > 2000) {
                params = params.substring(0, 2000) + "...";
            }
            operationLog.setParams(params);
        } catch (Exception e) {
            log.warn("Failed to serialize method params: {}", e.getMessage());
            operationLog.setParams("序列化失败");
        }

        // 获取请求IP
        try {
            ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                operationLog.setIp(getClientIp(request));
            }
        } catch (Exception e) {
            log.warn("Failed to get client IP: {}", e.getMessage());
        }

        Object result = null;
        boolean success = true;
        try {
            // 执行目标方法
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            success = false;
            throw e;
        } finally {
            // 计算执行时间
            long executionTime = System.currentTimeMillis() - startTime;

            // 设置执行结果
            try {
                String resultStr;
                if (result != null) {
                    resultStr = objectMapper.writeValueAsString(result);
                    if (resultStr.length() > 2000) {
                        resultStr = resultStr.substring(0, 2000) + "...";
                    }
                } else {
                    resultStr = success ? "成功" : "失败";
                }
                operationLog.setResult(resultStr);
            } catch (Exception e) {
                operationLog.setResult(success ? "成功" : "失败");
            }

            operationLog.setCreatedAt(LocalDateTime.now());

            // 异步保存操作日志（避免影响业务性能）
            try {
                operLogService.save(operationLog);
            } catch (Exception e) {
                log.error("Failed to save operation log: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 获取客户端真实IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理时取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}

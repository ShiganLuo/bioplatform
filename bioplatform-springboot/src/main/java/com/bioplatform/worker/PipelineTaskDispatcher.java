package com.bioplatform.worker;

import com.bioplatform.entity.PipelineExecution;
import com.bioplatform.mapper.PipelineExecutionMapper;
import com.bioplatform.mapper.PipelineMapper;
import com.bioplatform.entity.Pipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 任务调度器
 * 选择 Worker、提交任务、异步轮询状态
 *
 * @author luosg
 */
@Component
public class PipelineTaskDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PipelineTaskDispatcher.class);

    private final WorkerRegistry workerRegistry;
    private final WorkerClient workerClient;
    private final PipelineExecutionMapper executionMapper;
    private final PipelineMapper pipelineMapper;

    public PipelineTaskDispatcher(WorkerRegistry workerRegistry, WorkerClient workerClient,
                         PipelineExecutionMapper executionMapper, PipelineMapper pipelineMapper) {
        this.workerRegistry = workerRegistry;
        this.workerClient = workerClient;
        this.executionMapper = executionMapper;
        this.pipelineMapper = pipelineMapper;
    }

    /**
     * 调度执行任务到 Worker
     *
     * @param execution 执行记录
     * @return 是否成功提交
     */
    public boolean dispatch(PipelineExecution execution) {
        // 选择负载最低的 Worker
        WorkerRegistry.WorkerInfo worker = selectWorker();
        if (worker == null) {
            log.error("没有可用的 Worker，executionId={}", execution.getId());
            return false;
        }

        // 获取 Pipeline 信息
        Pipeline pipeline = pipelineMapper.selectById(execution.getPipelineId());
        String command = pipeline != null ? pipeline.getDockerImage() : null;

        try {
            // 提交任务到 Worker
            String taskId = workerClient.submitTask(
                    worker.getUrl(),
                    execution.getPipelineId(),
                    execution.getId(),
                    command,
                    execution.getInputParams()
            );

            // 更新执行记录的 Worker 信息
            execution.setWorkerId(worker.getId());
            execution.setWorkerUrl(worker.getUrl());
            execution.setStatus("RUNNING");
            executionMapper.updateById(execution);

            log.info("任务已调度: executionId={}, worker={}, taskId={}", execution.getId(), worker.getId(), taskId);

            // 异步轮询任务状态
            pollTaskStatus(execution.getId(), worker.getUrl(), taskId);

            return true;
        } catch (Exception e) {
            log.error("任务调度失败: executionId={}, worker={}, error={}", execution.getId(), worker.getId(), e.getMessage());
            execution.setStatus("FAILED");
            execution.setErrorLog("调度失败: " + e.getMessage());
            executionMapper.updateById(execution);
            return false;
        }
    }

    /**
     * 选择负载最低的 Worker
     */
    private WorkerRegistry.WorkerInfo selectWorker() {
        List<WorkerRegistry.WorkerInfo> healthyWorkers = workerRegistry.getHealthyWorkers();
        if (healthyWorkers.isEmpty()) {
            return null;
        }

        // 选择空闲内存最多的 Worker
        WorkerRegistry.WorkerInfo best = null;
        for (WorkerRegistry.WorkerInfo w : healthyWorkers) {
            if (best == null || w.getFreeMemoryGB() > best.getFreeMemoryGB()) {
                best = w;
            }
        }
        return best;
    }

    /**
     * 异步轮询任务状态
     */
    @Async
    public void pollTaskStatus(Long executionId, String workerUrl, String taskId) {
        int maxPolls = 3600; // 最多轮询 1 小时（每 10 秒一次）
        int pollInterval = 10000; // 10 秒

        for (int i = 0; i < maxPolls; i++) {
            try {
                Thread.sleep(pollInterval);

                String status = workerClient.queryTaskStatus(workerUrl, taskId);

                if ("COMPLETED".equals(status)) {
                    // 任务完成
                    String output = workerClient.getTaskOutput(workerUrl, taskId);
                    PipelineExecution execution = executionMapper.selectById(executionId);
                    if (execution != null) {
                        execution.setStatus("SUCCESS");
                        execution.setOutputPath(output);
                        execution.setFinishedAt(java.time.LocalDateTime.now());
                        executionMapper.updateById(execution);
                    }
                    log.info("任务完成: executionId={}, taskId={}", executionId, taskId);
                    return;
                } else if ("FAILED".equals(status)) {
                    // 任务失败
                    String output = workerClient.getTaskOutput(workerUrl, taskId);
                    PipelineExecution execution = executionMapper.selectById(executionId);
                    if (execution != null) {
                        execution.setStatus("FAILED");
                        execution.setErrorLog(output);
                        execution.setFinishedAt(java.time.LocalDateTime.now());
                        executionMapper.updateById(execution);
                    }
                    log.warn("任务失败: executionId={}, taskId={}", executionId, taskId);
                    return;
                } else if ("CANCELLED".equals(status)) {
                    PipelineExecution execution = executionMapper.selectById(executionId);
                    if (execution != null) {
                        execution.setStatus("CANCELLED");
                        execution.setFinishedAt(java.time.LocalDateTime.now());
                        executionMapper.updateById(execution);
                    }
                    log.info("任务已取消: executionId={}, taskId={}", executionId, taskId);
                    return;
                }
                // RUNNING 或 PENDING，继续轮询
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("轮询被中断: executionId={}", executionId);
                return;
            } catch (Exception e) {
                log.warn("轮询异常: executionId={}, error={}", executionId, e.getMessage());
                // 继续重试
            }
        }

        // 超时
        PipelineExecution execution = executionMapper.selectById(executionId);
        if (execution != null && "RUNNING".equals(execution.getStatus())) {
            execution.setStatus("FAILED");
            execution.setErrorLog("任务执行超时（超过 1 小时）");
            execution.setFinishedAt(java.time.LocalDateTime.now());
            executionMapper.updateById(execution);
        }
    }

    /**
     * 取消任务
     */
    public boolean cancelExecution(Long executionId) {
        PipelineExecution execution = executionMapper.selectById(executionId);
        if (execution == null || !"RUNNING".equals(execution.getStatus())) {
            return false;
        }

        if (execution.getWorkerUrl() != null) {
            // 查找对应的 taskId（通过轮询日志或扩展字段）
            // 简化处理：直接更新状态
            workerClient.cancelTask(execution.getWorkerUrl(), String.valueOf(executionId));
        }

        execution.setStatus("CANCELLED");
        execution.setFinishedAt(java.time.LocalDateTime.now());
        executionMapper.updateById(execution);
        return true;
    }
}

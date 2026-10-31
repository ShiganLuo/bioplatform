package com.bioplatform.engine;

import com.bioplatform.worker.WorkerClient;
import org.springframework.stereotype.Component;

/**
 * 本地 Worker 执行引擎
 * 通过 HTTP 调用计算节点(bio_worker 容器)的 REST API 执行任务。
 *
 * @author luosg
 */
@Component
public class LocalWorkerEngine implements ExecutionEngine {

    private final WorkerClient workerClient;

    public LocalWorkerEngine(WorkerClient workerClient) {
        this.workerClient = workerClient;
    }

    @Override
    public String submit(String workerUrl,
                         com.bioplatform.entity.PipelineExecution execution,
                         String command,
                         int timeoutSeconds) {
        return workerClient.submitTask(workerUrl, execution.getPipelineId(),
                execution.getId(), command, execution.getInputParams(), timeoutSeconds);
    }

    @Override
    public String status(String workerUrl, String taskId) {
        return workerClient.queryTaskStatus(workerUrl, taskId);
    }

    @Override
    public String output(String workerUrl, String taskId) {
        return workerClient.getTaskOutput(workerUrl, taskId);
    }

    @Override
    public boolean cancel(String workerUrl, String taskId) {
        return workerClient.cancelTask(workerUrl, taskId);
    }
}

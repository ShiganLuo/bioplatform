package com.bioplatform.engine;

/**
 * 执行引擎抽象
 * 负责把渲染好的命令提交到执行端并跟踪状态。
 * Phase 1 只有 LocalWorkerEngine(bio_worker 容器),RemoteWorkerEngine 留给多节点扩展。
 *
 * @author luosg
 */
public interface ExecutionEngine {

    /**
     * 提交任务
     *
     * @param workerUrl     执行端地址
     * @param execution     执行记录(取 executionId 作为业务关联)
     * @param command       渲染完成的最终命令
     * @param timeoutSeconds 超时秒数(执行端负责超时强杀)
     * @return 执行端任务 ID
     */
    String submit(String workerUrl,
                  com.bioplatform.entity.PipelineExecution execution,
                  String command,
                  int timeoutSeconds);

    /**
     * 查询任务状态
     *
     * @return RUNNING / COMPLETED / FAILED / CANCELLED / NOT_FOUND
     * @throws RuntimeException 网络级失败(区别于 NOT_FOUND 的确定性丢失)
     */
    String status(String workerUrl, String taskId);

    /**
     * 获取任务输出(stdout 或错误详情)
     */
    String output(String workerUrl, String taskId);

    /**
     * 取消任务(必须真杀执行端进程)
     */
    boolean cancel(String workerUrl, String taskId);
}

package com.bioplatform.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.bioplatform.dto.common.ExecutionVO;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.Pipeline;
import com.bioplatform.entity.PipelineExecution;
import com.bioplatform.entity.Project;
import com.bioplatform.mapper.PipelineExecutionMapper;
import com.bioplatform.mapper.PipelineMapper;
import com.bioplatform.mapper.ProjectMapper;
import com.bioplatform.service.PipelineExecutionService;
import com.bioplatform.worker.PipelineTaskDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流水线执行记录服务实现类
 *
 * @author luosg
 */
@Service
public class PipelineExecutionServiceImpl implements PipelineExecutionService {

    private static final Logger log = LoggerFactory.getLogger(PipelineExecutionServiceImpl.class);

    private final PipelineExecutionMapper pipelineExecutionMapper;
    private final PipelineTaskDispatcher taskDispatcher;
    private final PipelineMapper pipelineMapper;
    private final ProjectMapper projectMapper;

    /** 上传卷挂载根(task_output.log 落盘位置的读取侧) */
    @org.springframework.beans.factory.annotation.Value("${bioplatform.storage.shared-path:/data/shared/bioplatform}")
    private String sharedPath;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public PipelineExecutionServiceImpl(PipelineExecutionMapper pipelineExecutionMapper,
                                        PipelineTaskDispatcher taskDispatcher,
                                        PipelineMapper pipelineMapper,
                                        ProjectMapper projectMapper) {
        this.pipelineExecutionMapper = pipelineExecutionMapper;
        this.taskDispatcher = taskDispatcher;
        this.pipelineMapper = pipelineMapper;
        this.projectMapper = projectMapper;
    }

    @Override
    public PipelineExecution getExecutionById(Long id) {
        return pipelineExecutionMapper.selectById(id);
    }

    @Override
    public PageResult listByProjectId(Long projectId, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<PipelineExecution> executions = pipelineExecutionMapper.selectByProjectId(projectId, null);
        PageInfo<PipelineExecution> pageInfo = new PageInfo<>(executions);

        return PageResult.of(pageInfo.getTotal(), pageNum, pageSize, toVOList(executions, false));
    }

    @Override
    public PageResult listByUserId(Long userId, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<PipelineExecution> executions = pipelineExecutionMapper.selectByUserId(userId, null);
        PageInfo<PipelineExecution> pageInfo = new PageInfo<>(executions);

        return PageResult.of(pageInfo.getTotal(), pageNum, pageSize, toVOList(executions, false));
    }

    @Override
    public ExecutionVO getExecutionVO(Long id) {
        PipelineExecution e = pipelineExecutionMapper.selectById(id);
        return e == null ? null : toVO(e, true);
    }

    // ==================== VO 组装(字段对齐前端) ====================

    private List<ExecutionVO> toVOList(List<PipelineExecution> list, boolean withResult) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        java.util.Map<Long, String> pipelineNames = new java.util.HashMap<>();
        java.util.Map<Long, String> projectNames = new java.util.HashMap<>();
        for (PipelineExecution e : list) {
            if (e.getPipelineId() != null && !pipelineNames.containsKey(e.getPipelineId())) {
                Pipeline p = pipelineMapper.selectById(e.getPipelineId());
                pipelineNames.put(e.getPipelineId(), p == null ? null : p.getName());
            }
            if (e.getProjectId() != null && !projectNames.containsKey(e.getProjectId())) {
                Project p = projectMapper.selectById(e.getProjectId());
                projectNames.put(e.getProjectId(), p == null ? null : p.getName());
            }
        }
        return list.stream()
                .map(e -> toVO(e, withResult, pipelineNames.get(e.getPipelineId()), projectNames.get(e.getProjectId())))
                .collect(Collectors.toList());
    }

    private ExecutionVO toVO(PipelineExecution e, boolean withResult) {
        return toVO(e, withResult, null, null);
    }

    private ExecutionVO toVO(PipelineExecution e, boolean withResult, String pipelineName, String projectName) {
        if (pipelineName == null && e.getPipelineId() != null) {
            Pipeline p = pipelineMapper.selectById(e.getPipelineId());
            pipelineName = p == null ? null : p.getName();
        }
        if (projectName == null && e.getProjectId() != null) {
            Project p = projectMapper.selectById(e.getProjectId());
            projectName = p == null ? null : p.getName();
        }
        String result = withResult ? resolveLogText(e) : null;
        return new ExecutionVO(
                e.getId(), e.getPipelineId(), pipelineName, e.getProjectId(), projectName, e.getUserId(),
                e.getStatus(),
                e.getInputParams(),
                result,
                e.getErrorLog(),
                parseProgress(result, e.getStatus()),
                e.getExecutor(),
                e.getOutputPath(),
                e.getTaskId(),
                fmt(e.getStartedAt()),
                fmt(e.getFinishedAt()),
                fmt(e.getCreatedAt()));
    }

    private String fmt(LocalDateTime t) {
        return t == null ? null : t.format(FMT);
    }

    /** 进度:SUCCESS=100;否则从输出尾部 "N of M" 样式尽力解析,解析不出为 null(前端显示 0) */
    private Integer parseProgress(String result, String status) {
        if ("SUCCESS".equals(status)) {
            return 100;
        }
        if (result == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+)\\s+of\\s+(\\d+)").matcher(result);
        int pct = -1;
        while (m.find()) {
            int a = Integer.parseInt(m.group(1));
            int b = Integer.parseInt(m.group(2));
            if (b > 0 && a >= 0 && a <= b) {
                pct = (int) Math.min(99, Math.round(100.0 * a / b));
            }
        }
        return pct < 0 ? null : pct;
    }

    /** 执行输出:task_output.log 落盘 → worker 实时输出 → errorLog(兜底) */
    private String resolveLogText(PipelineExecution e) {
        if (e.getOutputPath() != null && !e.getOutputPath().isBlank()) {
            try {
                java.nio.file.Path f = java.nio.file.Paths.get(sharedPath, e.getOutputPath(), "task_output.log");
                if (java.nio.file.Files.exists(f)) {
                    String text = java.nio.file.Files.readString(f);
                    return text.length() > 200_000 ? text.substring(text.length() - 200_000) : text;
                }
            } catch (Exception ignored) {
                // 落盘读不到时继续兜底
            }
        }
        String live = taskDispatcher.fetchTaskOutput(e);
        if (live != null && !live.isBlank()) {
            return live;
        }
        return e.getErrorLog() == null || e.getErrorLog().isBlank()
                ? "执行状态: " + e.getStatus() + "\n暂无详细日志"
                : e.getErrorLog();
    }

    @Override
    public void cancelExecution(Long id) {
        PipelineExecution execution = pipelineExecutionMapper.selectById(id);
        if (execution == null) {
            throw new IllegalArgumentException("执行记录不存在");
        }

        // 委托 dispatcher:RUNNING 任务会真杀执行端进程(旧版只改 DB 是假取消)
        boolean cancelled = taskDispatcher.cancelExecution(id);
        if (!cancelled) {
            throw new IllegalArgumentException("当前状态不允许取消执行");
        }

        log.info("取消执行成功: executionId={}", id);
    }

    @Override
    public String getExecutionLogs(Long id) {
        PipelineExecution execution = pipelineExecutionMapper.selectById(id);
        if (execution == null) {
            throw new IllegalArgumentException("执行记录不存在");
        }
        // 真实执行输出(task_output.log → worker 实时 → errorLog)
        return resolveLogText(execution);
    }
}

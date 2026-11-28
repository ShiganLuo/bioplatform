package com.bioplatform.service.impl;

import com.bioplatform.common.util.HostPathUtil;
import com.bioplatform.common.util.OmicsVersionUtil;
import com.bioplatform.dto.admin.AdminBenchmarkDTO.*;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.Pipeline;
import com.bioplatform.entity.PipelineBenchmark;
import com.bioplatform.entity.PipelineExecution;
import com.bioplatform.entity.WorkflowTemplate;
import com.bioplatform.mapper.PipelineBenchmarkMapper;
import com.bioplatform.mapper.PipelineExecutionMapper;
import com.bioplatform.mapper.PipelineMapper;
import com.bioplatform.mapper.WorkflowTemplateMapper;
import com.bioplatform.service.BenchmarkService;
import com.bioplatform.worker.BenchmarkHarvester;
import com.bioplatform.worker.WorkerClient;
import com.bioplatform.worker.WorkerRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * benchmark 性能分析与资源校准(2026-11-26 方案一/二/三A)。
 * <p>
 * 校准链路:DB 历史(按当前 workflow_version 过滤、仅 version_source=git)导出
 * resource_history.&lt;版本&gt;.tsv → worker 跑 Omics 侧 calibrate_resources.py
 * --no-append(版本纯净,不混入根目录扫描行)→ resources_suggest.tsv → 可选 --apply
 * 护栏写回 config。护栏口径(n≥3/±30%/取整/删失下界)全在脚本,平台不重复实现。
 *
 * @author luosg
 */
@Service
public class BenchmarkServiceImpl implements BenchmarkService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double SAFETY = 1.3;
    private static final int MIN_POINTS = 3;

    @Value("${bioplatform.omics.root:/data/16T/luosg/workflow/Omics}")
    private String omicsRoot;

    @Value("${bioplatform.storage.shared-path:/data/shared/bioplatform}")
    private String sharedPath;

    @Value("${bioplatform.storage.dataRoot:}")
    private String dataRoot;

    @Autowired
    private PipelineBenchmarkMapper benchmarkMapper;
    @Autowired
    private PipelineExecutionMapper executionMapper;
    @Autowired
    private PipelineMapper pipelineMapper;
    @Autowired
    private WorkflowTemplateMapper workflowTemplateMapper;
    @Autowired
    private BenchmarkHarvester harvester;
    @Autowired
    private WorkerClient workerClient;
    @Autowired
    private WorkerRegistry workerRegistry;

    // ==================== 方案二:分析 ====================

    @Override
    public List<SummaryRow> summary(Long pipelineId, String workflow, String version) {
        String resolved = resolveVersionFilter(version);
        List<PipelineBenchmark> rows = benchmarkMapper.selectList(
                pipelineId, blankToNull(workflow), null, null,
                "ALL".equals(resolved) ? null : resolved);
        Map<String, Map<String, Object>> declared = declaredBlocks(pipelineId);
        // 按 (规则 × 版本) 聚合
        Map<String, List<PipelineBenchmark>> groups = new LinkedHashMap<>();
        for (PipelineBenchmark b : rows) {
            String key = b.getRuleName() + "\u0000" + (b.getWorkflowVersion() == null ? "unknown" : b.getWorkflowVersion());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
        }
        List<SummaryRow> out = new ArrayList<>();
        for (List<PipelineBenchmark> g : groups.values()) {
            PipelineBenchmark first = g.get(0);
            List<Double> obs = new ArrayList<>(), cen = new ArrayList<>(), secs = new ArrayList<>();
            double max = Double.NEGATIVE_INFINITY;
            for (PipelineBenchmark b : g) {
                if (b.getMaxRssMb() != null) {
                    if (Boolean.TRUE.equals(b.getCensored())) {
                        cen.add(b.getMaxRssMb());
                    } else {
                        obs.add(b.getMaxRssMb());
                    }
                    max = Math.max(max, b.getMaxRssMb());
                }
                if (b.getSeconds() != null && !Boolean.TRUE.equals(b.getCensored())) {
                    secs.add(b.getSeconds());
                }
            }
            obs.sort(Double::compareTo);
            cen.sort(Double::compareTo);
            secs.sort(Double::compareTo);
            int n = obs.size(), nCen = cen.size();
            Double p90 = null, suggest = null;
            String note = "";
            if (!obs.isEmpty()) {
                p90 = percentile(obs, 0.90);
                suggest = p90 * SAFETY;
            } else if (!cen.isEmpty()) {
                p90 = cen.get(cen.size() - 1);
                suggest = p90 * SAFETY;
                note = "censored-only(下界建议)";
            }
            if (!cen.isEmpty() && suggest != null) {
                double floor = cen.get(cen.size() - 1) * SAFETY;
                if (floor > suggest) {
                    suggest = floor;
                    note = "censored floor applied";
                }
            }
            if (n + nCen < MIN_POINTS) {
                suggest = null;
                note = "数据点不足(n<3),不出建议";
            }
            Map<String, Object> declBlock = declared.get(first.getRuleName());
            Double declaredMb = null;
            if (declBlock != null && declBlock.get("mem_mb") instanceof Number nb) {
                declaredMb = nb.doubleValue();
            }
            // 与 calibrate_resources.py 同口径:单次调整 ±30% 限幅(damped),
            // delta 按限幅后值算(标准2:与 resources_suggest.tsv 一致)
            if (suggest != null && declaredMb != null && declaredMb > 0) {
                double bounded = Math.min(Math.max(suggest, declaredMb * 0.7), declaredMb * 1.3);
                if (Math.abs(bounded - suggest) > 1) {
                    note = note.isEmpty() ? "damped ±30%" : note + "; damped ±30%";
                    suggest = bounded;
                }
            }
            Double delta = (suggest != null && declaredMb != null && declaredMb > 0)
                    ? 100.0 * (suggest - declaredMb) / declaredMb : null;
            out.add(new SummaryRow(first.getRuleName(), first.getWorkflowVersion(),
                    n, nCen,
                    obs.isEmpty() ? null : percentile(obs, 0.50),
                    p90,
                    max == Double.NEGATIVE_INFINITY ? null : max,
                    secs.isEmpty() ? null : percentile(secs, 0.50),
                    secs.isEmpty() ? null : percentile(secs, 0.90),
                    suggest, declaredMb, delta, note, declBlock));
        }
        out.sort((a, b) -> {
            double av = a.rssP90Mb() == null ? 0 : a.rssP90Mb();
            double bv = b.rssP90Mb() == null ? 0 : b.rssP90Mb();
            return Double.compare(bv, av);
        });
        return out;
    }

    @Override
    public List<VersionInfo> versions(Long pipelineId) {
        Map<String, int[]> merged = new LinkedHashMap<>();
        for (PipelineExecution e : executionMapper.selectByPipelineId(pipelineId, null)) {
            String v = e.getWorkflowVersion() == null ? "unknown" : e.getWorkflowVersion();
            merged.computeIfAbsent(v, k -> new int[2])[0]++;
        }
        for (Map<String, Object> m : benchmarkMapper.countPointsByVersion(pipelineId)) {
            String v = m.get("version") == null ? "unknown" : String.valueOf(m.get("version"));
            int[] c = merged.computeIfAbsent(v, k -> new int[2]);
            c[1] = ((Number) m.get("points")).intValue();
        }
        List<VersionInfo> out = new ArrayList<>();
        merged.forEach((v, c) -> out.add(new VersionInfo(v, c[0], c[1])));
        return out;
    }

    @Override
    public PageResult<PipelineBenchmark> list(Long pipelineId, String rule, Long executionId,
                                              String version, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<PipelineBenchmark> rows = benchmarkMapper.selectList(
                pipelineId, null, blankToNull(rule), executionId,
                "ALL".equals(version) ? null : blankToNull(version));
        PageInfo<PipelineBenchmark> pageInfo = new PageInfo<>(rows);
        return PageResult.of(pageInfo.getTotal(), pageNum, pageSize, rows);
    }

    @Override
    public List<PipelineBenchmark> trend(Long pipelineId, String rule) {
        return benchmarkMapper.selectTrend(pipelineId, blankToNull(rule));
    }

    // ==================== 方案一:历史回填 ====================

    @Override
    public BackfillResult backfill(Long pipelineId) {
        List<PipelineExecution> execs = executionMapper.selectTerminalByPipelineId(pipelineId);
        if (execs.isEmpty()) {
            throw new IllegalArgumentException("该流水线没有终态执行记录");
        }
        int inferred = 0, unknown = 0;
        // 版本回填:历史执行未记版本的,git log --before=<执行开始时间> -1 推断
        for (PipelineExecution e : execs) {
            if (e.getWorkflowVersion() != null) {
                continue;
            }
            String sha = gitHeadBefore(e.getStartedAt());
            e.setWorkflowVersion(sha);
            if (sha != null) {
                e.setVersionSource("inferred");
                inferred++;
            } else {
                e.setVersionSource("unknown");
                unknown++;
            }
            executionMapper.updateById(e);
        }
        int rows = harvester.harvestBackfill(execs);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("pipelineId", pipelineId);
        detail.put("executions", execs.size());
        return new BackfillResult(execs.size(), inferred, unknown, rows, detail);
    }

    // ==================== 方案三A:资源校准两段式 ====================

    @Override
    public CalibrateResult calibrate(Long pipelineId, boolean apply, boolean force) {
        Pipeline pipeline = pipelineMapper.selectById(pipelineId);
        if (pipeline == null) {
            throw new IllegalArgumentException("流水线不存在");
        }
        WorkflowTemplate template = pipeline.getTemplateId() == null ? null
                : workflowTemplateMapper.selectById(pipeline.getTemplateId());
        boolean omicsBridge = template != null
                && (template.getCommandTemplate() == null || template.getCommandTemplate().isBlank())
                && template.getSnakemakePath() != null && !template.getSnakemakePath().isBlank();
        if (!omicsBridge) {
            throw new IllegalArgumentException("仅支持 Omics 工作流模板的流程(snakemake_path 型)");
        }
        String version = OmicsVersionUtil.resolveHead(omicsRoot);
        if (version == null) {
            throw new IllegalArgumentException("无法确定 Omics 仓库当前版本(.git 不可读)");
        }

        // 版本纯净历史:只导出 version_source=git 且版本=当前 HEAD 的行
        // (unknown/inferred 只进明细展示,不进校准聚合)
        List<PipelineBenchmark> rows = benchmarkMapper.selectCalibrationRows(pipelineId, null, version);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("当前版本(" + shortSha(version) + ")无可用 benchmark 历史,请先回填或执行");
        }

        String outRoot = harvester.resolveOutputRoot();
        String absOut = outRoot + "/" + pipelineId;
        String historyRel = "resource_history." + version + ".tsv";
        String stageRel = pipeline.getProjectId() + "/stage_calib_" + System.currentTimeMillis();
        String historyTsv = buildHistoryTsv(rows);

        try {
            // 历史文件经上传卷暂存中转(backend 对 Omics 根是 /host:ro,写不进)
            Files.createDirectories(Paths.get(sharedPath, stageRel));
            Files.writeString(Paths.get(sharedPath, stageRel, "resource_history.tsv"), historyTsv);
            String stageHost = dataRoot + "/" + stageRel;
            String configRel = resolveConfigRel(template);
            String configHost = omicsRoot + "/" + configRel;
            String historyHost = omicsRoot + "/" + historyRel;

            StringBuilder cmd = new StringBuilder();
            cmd.append("cp \"").append(esc(stageHost)).append("/resource_history.tsv\" \"")
                    .append(esc(historyHost)).append("\" && cd \"").append(esc(omicsRoot))
                    .append("\" && python3 src/common/util/calibrate_resources.py")
                    .append(" --root \"").append(esc(absOut)).append("\"")
                    .append(" --history \"").append(esc(historyHost)).append("\"")
                    .append(" --config \"").append(esc(configHost)).append("\"")
                    .append(" --out \"").append(esc(absOut)).append("/resources_suggest.tsv\"")
                    .append(" --no-append");
            if (apply) {
                cmd.append(" --apply");
            }
            cmd.append(" > \"").append(esc(absOut)).append("/calibrate.log\" 2>&1");
            // 退出码保留在 rc:脚本非零不代表失败(输出才是交付物),收尾照做
            cmd.append("; rc=$?; chown --reference=\"").append(esc(omicsRoot)).append("\" \"")
                    .append(esc(historyHost)).append("\" \"").append(esc(configHost))
                    .append("\" 2>/dev/null; rm -rf \"").append(esc(stageHost)).append("\"; exit $rc");

            // 写回护栏:干净工作树(平台自己的历史文件不算脏);force=用户确认带未提交改动
            String diff = "";
            if (apply) {
                String status = gitStatus();
                if (!status.isBlank() && !force) {
                    throw new IllegalArgumentException(
                            "Omics 工作区存在未提交改动,写回已拒绝(确认混入可加 force): " + status);
                }
                diff = gitConfigDiff(configRel); // 留痕:写回前 config 现状
            }

            Map<String, Object> r = executeOnWorker(cmd.toString());
            String outLog = readOutputFile(absOut + "/calibrate.log");
            if (outLog == null || outLog.isBlank()) {
                outLog = r.get("output") == null ? "" : String.valueOf(r.get("output"));
            }

            List<SuggestRow> suggestRows = parseSuggest(absOut, declaredBlocks(pipelineId));
            if (apply) {
                String post = gitConfigDiff(configRel);
                diff = "===== 写回前 =====\n" + diff + "\n===== 写回后 =====\n" + post;
                log.info("资源校准写回: pipelineId={}, version={}, diff 长度={}", pipelineId, shortSha(version), post.length());
            }
            return new CalibrateResult(version, apply, rows.size(), suggestRows, diff, outLog);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("资源校准失败: pipelineId={}, error={}", pipelineId, e.getMessage());
            throw new IllegalArgumentException("资源校准失败: " + e.getMessage());
        }
    }

    // ==================== 内部 ====================

    private String resolveVersionFilter(String version) {
        if (version == null || version.isBlank()) {
            String head = OmicsVersionUtil.resolveHead(omicsRoot);
            return head != null ? head : "ALL";
        }
        return version.trim();
    }

    /** 模板 config 的当前资源声明 resources.<rule> 整块(threads/mem_mb/scale...) */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> declaredBlocks(Long pipelineId) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        Pipeline pipeline = pipelineMapper.selectById(pipelineId);
        if (pipeline == null || pipeline.getTemplateId() == null) {
            return out;
        }
        WorkflowTemplate template = workflowTemplateMapper.selectById(pipeline.getTemplateId());
        if (template == null || template.getConfigTemplate() == null) {
            return out;
        }
        try {
            JsonNode res = MAPPER.readTree(template.getConfigTemplate()).path("resources");
            res.fields().forEachRemaining(e ->
                    out.put(e.getKey(), MAPPER.convertValue(e.getValue(), Map.class)));
        } catch (Exception ignored) {
            // 配置解析失败不出声明值列
        }
        return out;
    }

    /** 近似 p 分位(最近秩),入参须已升序 */
    private Double percentile(List<Double> sorted, double q) {
        if (sorted.isEmpty()) {
            return null;
        }
        int idx = (int) Math.ceil(q * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }

    /** 导出 calibrate_resources.py 的 resource_history.tsv 格式(版本纯净由 SQL 保证) */
    private String buildHistoryTsv(List<PipelineBenchmark> rows) {
        StringBuilder sb = new StringBuilder("run_id\tts\trule\tattempt\tseconds\tmax_rss_mb\tcensored\tsource\n");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        for (PipelineBenchmark b : rows) {
            sb.append("exec_").append(b.getExecutionId()).append('\t')
                    .append(b.getCreatedAt() != null ? b.getCreatedAt().format(fmt) : "").append('\t')
                    .append(b.getRuleName()).append('\t')
                    .append(b.getAttempt() == null ? 1 : b.getAttempt()).append('\t')
                    .append(b.getSeconds() == null ? "" : String.format(Locale.ROOT, "%.1f", b.getSeconds())).append('\t')
                    .append(b.getMaxRssMb() == null ? "" : String.format(Locale.ROOT, "%.0f", b.getMaxRssMb())).append('\t')
                    .append(Boolean.TRUE.equals(b.getCensored()) ? "1" : "0").append('\t')
                    .append(b.getBenchmarkPath() == null ? "" : b.getBenchmarkPath()).append('\n');
        }
        return sb.toString();
    }

    /** 模板 config 文件的相对路径(写回目标):pipeline= config/<名>.json;task= snakemakePath 同目录同名 .json */
    private String resolveConfigRel(WorkflowTemplate template) {
        String smk = template.getSnakemakePath();
        if ("pipeline".equals(template.getType())) {
            return "config/" + template.getName() + ".json";
        }
        if (smk != null && smk.endsWith(".smk")) {
            return smk.substring(0, smk.length() - ".smk".length()) + ".json";
        }
        throw new IllegalArgumentException("无法从 snakemakePath 推断 config 文件: " + smk);
    }

    private List<SuggestRow> parseSuggest(String absOut, Map<String, Map<String, Object>> declared) {
        List<SuggestRow> out = new ArrayList<>();
        try {
            Path f = HostPathUtil.resolveExisting(absOut + "/resources_suggest.tsv");
            if (f == null || !Files.isRegularFile(f)) {
                return out;
            }
            List<String> lines = Files.readAllLines(f);
            for (int i = 1; i < lines.size(); i++) {
                String[] c = lines.get(i).split("\t", -1);
                if (c.length < 8) {
                    continue;
                }
                out.add(new SuggestRow(c[0], parseInt(c[1]), parseInt(c[2]),
                        c[3], c[4],
                        c[5].isBlank() ? null : (c[5].contains(".") ? Double.parseDouble(c[5]) : Integer.parseInt(c[5])),
                        c[6], c[7], declared.get(c[0])));
            }
        } catch (Exception e) {
            log.warn("resources_suggest.tsv 解析失败: {}", e.getMessage());
        }
        return out;
    }

    private Integer parseInt(String s) {
        try {
            return s == null || s.isBlank() ? 0 : (int) Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Map<String, Object> executeOnWorker(String cmd) throws Exception {
        WorkerRegistry.WorkerInfo worker = firstHealthyWorker();
        if (worker == null) {
            throw new IllegalStateException("当前无可用计算节点");
        }
        return workerClient.executeShell(worker.getUrl(), cmd, 300, null);
    }

    /** git log --before=<执行开始时间> -1:版本推断(标注 inferred) */
    private String gitHeadBefore(LocalDateTime before) {
        if (before == null) {
            return null;
        }
        try {
            String ts = before.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            // -c safe.directory:worker 以 root 跑 git 而仓库属主是用户,不加会 fatal 128
            // "detected dubious ownership"(2026-11-26 e2e 实踩)
            Map<String, Object> r = executeOnWorker(
                    "git -c safe.directory=\"" + esc(omicsRoot) + "\" -C \"" + esc(omicsRoot)
                            + "\" log --before=\"" + esc(ts) + "\" --format=%H -1 2>/dev/null");
            String out = r.get("output") == null ? "" : String.valueOf(r.get("output")).trim();
            for (String line : out.split("\\R")) {
                line = line.trim();
                if (line.matches("[0-9a-f]{7,40}")) {
                    return line;
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("版本推断失败(记 unknown): before={}, error={}", before, e.getMessage());
            return null;
        }
    }

    /** git status --porcelain(平台导出的历史文件不算脏) */
    private String gitStatus() {
        try {
            Map<String, Object> r = executeOnWorker(
                    "git -c safe.directory=\"" + esc(omicsRoot) + "\" -C \"" + esc(omicsRoot) + "\" status --porcelain 2>/dev/null");
            String out = r.get("output") == null ? "" : String.valueOf(r.get("output"));
            StringBuilder sb = new StringBuilder();
            for (String line : out.split("\\R")) {
                // 只认 git porcelain 形态行(过滤 bash/locale 警告等噪声,2026-11-26 e2e 实踩)
                if (line.contains("resource_history.") || !line.matches("^.{1,2} .+")) {
                    continue;
                }
                sb.append(line).append('\n');
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "(工作树状态不可查: " + e.getMessage() + ")";
        }
    }

    /** config 现状 diff(留痕) */
    private String gitConfigDiff(String configRel) {
        try {
            Map<String, Object> r = executeOnWorker(
                    "git -c safe.directory=\"" + esc(omicsRoot) + "\" -C \"" + esc(omicsRoot)
                            + "\" diff -- \"" + esc(configRel) + "\" 2>/dev/null");
            String out = r.get("output") == null ? "" : String.valueOf(r.get("output"));
            return out.length() > 20_000 ? out.substring(out.length() - 20_000) : out;
        } catch (Exception e) {
            return "(diff 不可得: " + e.getMessage() + ")";
        }
    }

    private String readOutputFile(String absPath) {
        try {
            Path f = HostPathUtil.resolveExisting(absPath);
            if (f == null) {
                return null;
            }
            String text = Files.readString(f);
            return text.length() > 50_000 ? "…(输出过长已截断)\n" + text.substring(text.length() - 50_000) : text;
        } catch (Exception e) {
            return null;
        }
    }

    private WorkerRegistry.WorkerInfo firstHealthyWorker() {
        List<WorkerRegistry.WorkerInfo> healthy = workerRegistry.getHealthyWorkers();
        return healthy.isEmpty() ? null : healthy.get(0);
    }

    private String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("`", "\\`");
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String shortSha(String v) {
        return v == null ? "unknown" : v.substring(0, Math.min(8, v.length()));
    }
}

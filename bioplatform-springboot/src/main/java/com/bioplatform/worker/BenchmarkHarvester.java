package com.bioplatform.worker;

import com.bioplatform.common.util.HostPathUtil;
import com.bioplatform.common.util.OmicsRuleNames;
import com.bioplatform.entity.PipelineBenchmark;
import com.bioplatform.entity.PipelineExecution;
import com.bioplatform.mapper.PipelineBenchmarkMapper;
import com.bioplatform.mapper.PipelineExecutionMapper;
import com.bioplatform.service.SystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * snakemake benchmark 收割器(方案一,2026-11-26):
 * 扫执行输出目录下 **&#47;*.benchmark.tsv(.txt) / **&#47;benchmarks/*.tsv,逐行解析入库。
 * <p>
 * 归属语义(共享输出目录多执行复用):benchmark 文件按 mtime 落在执行时间窗
 * [startedAt-2min, finishedAt+10min] 内归该执行;回填时对窗外孤儿文件按最近
 * startedAt 归属(每物理行只入库一次)。幂等键 uk_pb_exec_path_attempt,
 * 重复收割 INSERT IGNORE 不重复入库。失败跑也采(删失数据);.OOM 标记或
 * attempt>1 行按删失(与 calibrate_resources.py 同口径)。
 *
 * @author luosg
 */
@Component
public class BenchmarkHarvester {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkHarvester.class);

    @org.springframework.beans.factory.annotation.Value("${bioplatform.storage.shared-path:/data/shared/bioplatform}")
    private String sharedPath;

    @org.springframework.beans.factory.annotation.Value("${bioplatform.storage.dataRoot:}")
    private String dataRoot;

    @Autowired
    private SystemService systemService;

    @Autowired
    private PipelineBenchmarkMapper benchmarkMapper;

    @Autowired
    private PipelineExecutionMapper executionMapper;

    /** 执行输出根目录(system_configs execution.outputRoot,空=上传卷) */
    public String resolveOutputRoot() {
        String v = systemService.getConfigValue("execution.outputRoot");
        return (v == null || v.isBlank()) ? dataRoot : v.trim();
    }

    /** 执行输出目录(多根回退:输出根/上传卷/共享挂载) */
    public Path resolveOutputDir(PipelineExecution exec) {
        if (exec.getOutputPath() == null || exec.getOutputPath().isBlank()) {
            return null;
        }
        for (String root : new String[]{resolveOutputRoot(), dataRoot, sharedPath}) {
            if (root == null || root.isBlank()) {
                continue;
            }
            try {
                Path dir = HostPathUtil.resolveExisting(root + "/" + exec.getOutputPath());
                if (dir != null && Files.isDirectory(dir)) {
                    return dir;
                }
            } catch (Exception ignored) {
                // 该根下不可读,试下一个
            }
        }
        return null;
    }

    /**
     * 终态收割:benchmark 文件归属由 {@link #attributeOwner} 统一判定(与回填同口径),
     * 只入库归属本执行的文件——共享输出目录多执行复用,时间窗重叠时按最近 startedAt 收口,
     * 保证同一物理行无论哪条收割路径先跑都归同一执行(幂等键不产生跨执行重复)。
     *
     * @return 新入库行数
     */
    public int harvestWindow(PipelineExecution exec) {
        try {
            Path dir = resolveOutputDir(exec);
            if (dir == null) {
                return 0;
            }
            List<PipelineExecution> siblings = executionMapper.selectByPipelineId(exec.getPipelineId(), null);
            Map<String, String> layout = OmicsRuleNames.loadSampleLayout(dir);
            int inserted = 0;
            for (FileRow fr : collectFiles(dir)) {
                long mtime = Files.getLastModifiedTime(fr.path).toMillis();
                PipelineExecution owner = attributeOwner(mtime, siblings);
                if (owner == null || !owner.getId().equals(exec.getId())) {
                    continue;
                }
                inserted += parseAndInsert(fr, exec, layout, dir);
            }
            if (inserted > 0) {
                log.info("benchmark 收割: executionId={}, 新入库 {} 行", exec.getId(), inserted);
            }
            return inserted;
        } catch (Exception e) {
            log.warn("benchmark 收割失败(不影响执行结果): executionId={}, error={}", exec.getId(), e.getMessage());
            return 0;
        }
    }

    /**
     * 历史回填:整目录文件经 {@link #attributeOwner} 归属到给定执行集合。
     * 执行集合须为同一输出目录(同一分析)。
     *
     * @return 新入库行数
     */
    public int harvestBackfill(List<PipelineExecution> execs) {
        if (execs.isEmpty()) {
            return 0;
        }
        PipelineExecution first = execs.get(0);
        Path dir = resolveOutputDir(first);
        if (dir == null) {
            return 0;
        }
        Map<String, String> layout = OmicsRuleNames.loadSampleLayout(dir);
        int inserted = 0;
        for (FileRow fr : collectFiles(dir)) {
            long mtime;
            try {
                mtime = Files.getLastModifiedTime(fr.path).toMillis();
            } catch (Exception e) {
                continue;
            }
            PipelineExecution owner = attributeOwner(mtime, execs);
            if (owner == null) {
                continue;
            }
            inserted += parseAndInsert(fr, owner, layout, dir);
        }
        if (inserted > 0) {
            log.info("benchmark 回填: pipelineId={}, 新入库 {} 行", first.getPipelineId(), inserted);
        }
        return inserted;
    }

    /**
     * 文件归属判定(确定性,收割/回填共用):mtime 落在执行时间窗
     * [startedAt-2min, finishedAt+10min] 的执行里取 startedAt 最近(最晚)的
     * ——时间窗可因失败检测滞后而重叠,以最近运行为准;全都不在窗内则取
     * startedAt 距 mtime 最近的执行。
     */
    private PipelineExecution attributeOwner(long mtimeMs, List<PipelineExecution> execs) {
        PipelineExecution best = null;
        long bestStart = Long.MIN_VALUE;
        long bestDist = Long.MAX_VALUE;
        PipelineExecution nearest = null;
        for (PipelineExecution e : execs) {
            LocalDateTime s = e.getStartedAt() != null ? e.getStartedAt().minusMinutes(2)
                    : LocalDateTime.now().minusHours(24);
            LocalDateTime en = e.getFinishedAt() != null ? e.getFinishedAt().plusMinutes(10)
                    : LocalDateTime.now().plusMinutes(10);
            long startMs = toMs(e.getStartedAt() != null ? e.getStartedAt() : s);
            if (mtimeMs >= toMs(s) && mtimeMs <= toMs(en)) {
                if (startMs > bestStart) {
                    bestStart = startMs;
                    best = e;
                }
            }
            long d = Math.abs(mtimeMs - startMs);
            if (d < bestDist) {
                bestDist = d;
                nearest = e;
            }
        }
        return best != null ? best : nearest;
    }

    /** 补偿扫描(兜底):近 24h 终态执行补收割(幂等),防重启/异常路径漏采 */
    @Scheduled(fixedDelay = 3600_000, initialDelay = 120_000)
    public void compensate() {
        try {
            List<PipelineExecution> recent =
                    executionMapper.selectTerminalSince(LocalDateTime.now().minusHours(24));
            for (PipelineExecution e : recent) {
                harvestWindow(e);
            }
        } catch (Exception e) {
            log.warn("benchmark 补偿扫描失败(下轮重试): {}", e.getMessage());
        }
    }

    // ==================== 内部 ====================

    private record FileRow(Path path, String relPath, String base) {
    }

    /** 收集输出目录下全部 benchmark 文件(与 calibrate_resources.py 三种布局一致) */
    private List<FileRow> collectFiles(Path dir) {
        List<FileRow> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir, 12)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                String name = p.getFileName().toString();
                boolean match = (name.endsWith(".benchmark.tsv") || name.endsWith(".benchmark.txt"))
                        || (name.endsWith(".tsv") && p.getParent() != null
                            && "benchmarks".equals(p.getParent().getFileName().toString()));
                if (!match) {
                    return;
                }
                String base = name;
                if (base.endsWith(".benchmark.tsv")) {
                    base = base.substring(0, base.length() - ".benchmark.tsv".length());
                } else if (base.endsWith(".benchmark.txt")) {
                    base = base.substring(0, base.length() - ".benchmark.txt".length());
                } else {
                    base = base.substring(0, base.length() - ".tsv".length());
                }
                String rel = dir.relativize(p).toString().replace('\\', '/');
                out.add(new FileRow(p, rel, base));
            });
        } catch (Exception e) {
            log.warn("benchmark 文件收集失败: dir={}, error={}", dir, e.getMessage());
        }
        out.sort(Comparator.comparing(FileRow::relPath));
        return out;
    }

    /** 解析单文件并入库(INSERT IGNORE 幂等);返回新入库行数 */
    private int parseAndInsert(FileRow fr, PipelineExecution exec, Map<String, String> layout, Path dir) {
        int inserted = 0;
        try {
            String ruleName = OmicsRuleNames.resolve(fr.base, fr.path.toString(), layout);
            Path oomMarker = fr.path.getParent().resolve(fr.base + ".OOM");
            boolean censoredAll = Files.isRegularFile(oomMarker);
            String[] pathSegs = fr.relPath.split("/");
            String workflow = pathSegs.length > 1 ? pathSegs[0] : null;
            String sample = null, genome = null;
            for (int i = 0; i < pathSegs.length - 1; i++) {
                if ("sample".equals(pathSegs[i]) || "samples".equals(pathSegs[i])) {
                    if (i + 1 < pathSegs.length - 1) {
                        sample = pathSegs[i + 1];
                    }
                    if (i + 2 < pathSegs.length - 1) {
                        genome = pathSegs[i + 2];
                    }
                    break;
                }
            }
            List<String> lines = Files.readAllLines(fr.path);
            int attempt = 0;
            for (String line : lines) {
                if (line.isBlank() || line.startsWith("s\t") || line.startsWith("s,")) {
                    continue; // 表头
                }
                attempt++;
                String[] cells = line.split("\t", -1);
                if (cells.length < 3) {
                    continue;
                }
                PipelineBenchmark b = new PipelineBenchmark();
                b.setExecutionId(exec.getId());
                b.setPipelineId(exec.getPipelineId());
                b.setWorkflow(workflow);
                b.setRuleName(ruleName);
                b.setSample(sample);
                b.setGenome(genome);
                b.setAttempt(attempt);
                // 删失口径与 calibrate_resources.py 一致:仅 .OOM 标记(重试行号不作删失)
                b.setCensored(censoredAll);
                b.setSeconds(parseSeconds(cells[0], cells.length > 1 ? cells[1] : null));
                b.setMaxRssMb(parseDouble(cells.length > 2 ? cells[2] : null));
                b.setMaxVmsMb(parseDouble(cells.length > 3 ? cells[3] : null));
                b.setMaxUssMb(parseDouble(cells.length > 4 ? cells[4] : null));
                b.setMaxPssMb(parseDouble(cells.length > 5 ? cells[5] : null));
                b.setIoIn(parseDouble(cells.length > 6 ? cells[6] : null));
                b.setIoOut(parseDouble(cells.length > 7 ? cells[7] : null));
                b.setMeanLoad(parseDouble(cells.length > 8 ? cells[8] : null));
                b.setCpuTime(parseDouble(cells.length > 9 ? cells[9] : null));
                b.setBenchmarkPath(fr.relPath);
                b.setWorkflowVersion(exec.getWorkflowVersion());
                inserted += benchmarkMapper.insertIgnore(b);
            }
        } catch (Exception e) {
            log.warn("benchmark 文件解析失败: path={}, error={}", fr.relPath, e.getMessage());
        }
        return inserted;
    }

    /** s 列优先,h:m:s 兜底 */
    private Double parseSeconds(String sCol, String hmsCol) {
        Double s = parseDouble(sCol);
        if (s != null) {
            return s;
        }
        if (hmsCol == null || hmsCol.isBlank()) {
            return null;
        }
        try {
            double total = 0;
            for (String p : hmsCol.trim().split(":")) {
                total = total * 60 + Double.parseDouble(p.trim());
            }
            return total;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseDouble(String v) {
        if (v == null || v.isBlank() || "nan".equalsIgnoreCase(v.trim()) || "None".equals(v.trim())) {
            return null;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long toMs(LocalDateTime t) {
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}

package com.bioplatform.service.impl;

import com.bioplatform.dto.datafile.SampleMetaFileRef;
import com.bioplatform.dto.datafile.SampleMetaFilesResult;
import com.bioplatform.entity.DataFile;
import com.bioplatform.entity.SampleMeta;
import com.bioplatform.mapper.DataFileMapper;
import com.bioplatform.mapper.SampleMetaMapper;
import com.bioplatform.service.SampleMetaFileService;
import com.bioplatform.storage.StorageStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 样本 meta 文件引用解析实现
 *
 * 解析规则（两层，短路）：
 * 1. 列名白名单：fastq_1/fastq_2/bam/pbi/ms_file/fastq_dir 整列判定为文件引用；
 * 2. 内容启发式兜底（白名单外的列）：值含 '/' 或匹配已知生物数据后缀。
 *
 * 与 data_files 的匹配（三级，按序短路）：
 * 1. data_files.path 精确等于 rawPath（import-local 的绝对路径场景）；
 * 2. data_files.path 以 rawPath 结尾（rawPath 是相对/部分路径）；
 * 3. rawPath 的 basename 等于 data_files.name（多命中优先同项目，再取最新）。
 *
 * 不落物理关联表：meta 随时可编辑，查询时解析天然与最新内容一致。
 *
 * @author luosg
 */
@Service
public class SampleMetaFileServiceImpl implements SampleMetaFileService {

    private static final Logger log = LoggerFactory.getLogger(SampleMetaFileServiceImpl.class);

    /** 已知文件引用列名（按 metaMode 的默认列），key 小写 */
    private static final Set<String> FILE_COLUMN_WHITELIST = new HashSet<>(Arrays.asList(
            "fastq_1", "fastq_2", "bam", "pbi", "ms_file", "fastq_dir"));

    /** 已知生物数据后缀（大小写不敏感） */
    private static final Pattern FILE_SUFFIX = Pattern.compile(
            "\\.(fastq|fq|gz|bam|sam|vcf|bed|gff|gtf|fa|fasta|fna|tsv|csv|txt|pbi|ms)$",
            Pattern.CASE_INSENSITIVE);

    private final SampleMetaMapper sampleMetaMapper;
    private final DataFileMapper dataFileMapper;

    public SampleMetaFileServiceImpl(SampleMetaMapper sampleMetaMapper, DataFileMapper dataFileMapper) {
        this.sampleMetaMapper = sampleMetaMapper;
        this.dataFileMapper = dataFileMapper;
    }

    @Override
    public SampleMetaFilesResult resolveMetaFiles(Long metaId) {
        SampleMeta meta = sampleMetaMapper.selectById(metaId);
        if (meta == null) {
            throw new IllegalArgumentException("Meta不存在: " + metaId);
        }
        List<SampleMetaFileRef> refs = parseRefs(meta);
        long matched = refs.stream().filter(SampleMetaFileRef::matched).count();
        return new SampleMetaFilesResult(metaId, refs, refs.size(), (int) matched);
    }

    @Override
    public int registerMissingFiles(Long metaId, Long userId) {
        SampleMeta meta = sampleMetaMapper.selectById(metaId);
        if (meta == null) {
            throw new IllegalArgumentException("Meta不存在: " + metaId);
        }
        int registered = 0;
        for (SampleMetaFileRef ref : parseRefs(meta)) {
            if (ref.matched() || !ref.onDisk()) continue;
            Path p = Paths.get(ref.rawPath());
            if (!p.isAbsolute()) continue;
            DataFile df = new DataFile();
            df.setName(p.getFileName().toString());
            df.setPath(ref.rawPath());  // import-local 同契约：绝对路径原样存储，resolve 时原样返回
            df.setFileType(suffixOf(ref.rawPath()));
            try {
                df.setFileSize(Files.size(p));
            } catch (Exception e) {
                df.setFileSize(0L);
            }
            df.setUploadedBy(userId);
            dataFileMapper.insert(df);
            if (meta.getProjectId() != null) {
                dataFileMapper.insertFileProject(df.getId(), List.of(meta.getProjectId()));
            }
            registered++;
            log.info("样本meta文件登记入库: metaId={}, fileId={}, path={}", metaId, df.getId(), df.getPath());
        }
        return registered;
    }

    @Override
    public boolean isFileCell(String columnName, String value) {
        if (value == null || value.isBlank()) return false;
        String v = value.trim();
        if (columnName != null && FILE_COLUMN_WHITELIST.contains(columnName.trim().toLowerCase(Locale.ROOT))) {
            return true;
        }
        return v.contains("/") || FILE_SUFFIX.matcher(v).find();
    }

    // ==================== 解析与匹配 ====================

    private List<SampleMetaFileRef> parseRefs(SampleMeta meta) {
        List<SampleMetaFileRef> refs = new ArrayList<>();
        String content = meta.getMetaContent();
        if (content == null || content.isBlank()) return refs;

        String[] lines = content.trim().split("\n");
        if (lines.length == 0) return refs;

        String[] header = splitLine(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            String[] cells = splitLine(lines[i]);
            for (int c = 0; c < header.length && c < cells.length; c++) {
                String col = header[c] == null ? "" : header[c].trim();
                String val = cells[c] == null ? "" : cells[c].trim();
                if (!isFileCell(col, val)) continue;
                refs.add(matchOne(val, meta));
            }
        }
        return refs;
    }

    /** 行内分隔符探测：tab / 多空格 / 逗号 / 分号 / 竖线（与前端 tsvToTable 语义对齐） */
    private String[] splitLine(String line) {
        if (line.contains("\t")) return line.split("\t", -1);
        if (line.matches(".*\\s{2,}.*")) return line.trim().split("\\s{2,}", -1);
        if (line.contains(",")) return line.split(",", -1);
        if (line.contains(";")) return line.split(";", -1);
        if (line.contains("|")) return line.split("\\|", -1);
        return new String[]{line};
    }

    private SampleMetaFileRef matchOne(String rawPath, SampleMeta meta) {
        String raw = rawPath.trim();
        // 单元格常出现带引号的路径
        if (raw.length() >= 2 && ((raw.startsWith("\"") && raw.endsWith("\""))
                || (raw.startsWith("'") && raw.endsWith("'")))) {
            raw = raw.substring(1, raw.length() - 1).trim();
        }

        DataFile hit = null;

        // 1. path 精确匹配
        List<DataFile> candidates = dataFileMapper.searchByPathExact(raw);
        if (!candidates.isEmpty()) {
            hit = pickBest(candidates, meta);
        }

        // 2. path 后缀匹配
        if (hit == null) {
            candidates = dataFileMapper.searchByPathSuffix(raw);
            if (!candidates.isEmpty()) {
                hit = pickBest(candidates, meta);
            }
        }

        // 3. basename = name
        if (hit == null) {
            String base = baseName(raw);
            if (base != null && !base.isBlank()) {
                candidates = dataFileMapper.searchByName(base, meta.getProjectId(), null);
                List<DataFile> exactName = new ArrayList<>();
                for (DataFile c : candidates) {
                    if (baseName(c.getName()) != null && baseName(c.getName()).equalsIgnoreCase(base)) {
                        exactName.add(c);
                    }
                }
                if (!exactName.isEmpty()) {
                    hit = pickBest(exactName, meta);
                }
            }
        }

        boolean onDisk = false;
        if (hit == null) {
            Path p = Paths.get(raw);
            if (p.isAbsolute()) {
                try {
                    onDisk = Files.exists(p);
                } catch (Exception e) {
                    onDisk = false;
                }
            }
        }
        return new SampleMetaFileRef(rawPath, hit != null,
                hit != null ? hit.getId() : null,
                hit != null ? hit.getName() : null,
                hit != null ? hit.getFileStatus() : null,
                onDisk);
    }

    /** 歧义消解：优先与 meta 同项目的行，再取最新 created_at */
    private DataFile pickBest(List<DataFile> candidates, SampleMeta meta) {
        DataFile best = null;
        int bestScore = -1;
        for (DataFile c : candidates) {
            int score = 0;
            if (meta.getProjectId() != null && c.getProjectIds() != null
                    && Arrays.asList(c.getProjectIds().split(",")).contains(String.valueOf(meta.getProjectId()))) {
                score = 2;
            }
            if (score > bestScore || (score == bestScore && best != null
                    && c.getCreatedAt() != null && best.getCreatedAt() != null
                    && c.getCreatedAt().isAfter(best.getCreatedAt()))) {
                best = c;
                bestScore = score;
            }
        }
        return best != null ? best : (candidates.isEmpty() ? null : candidates.get(0));
    }

    private String baseName(String path) {
        if (path == null) return null;
        String p = path.replace('\\', '/');
        int idx = p.lastIndexOf('/');
        return idx >= 0 ? p.substring(idx + 1) : p;
    }

    private String suffixOf(String path) {
        String base = baseName(path);
        if (base != null && base.contains(".")) {
            return base.substring(base.lastIndexOf(".") + 1);
        }
        return "";
    }
}

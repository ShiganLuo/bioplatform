package com.bioplatform.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * benchmark 文件名 → config 资源键的规则名解析。
 * <p>
 * 与 Omics 仓库 {@code src/common/util/calibrate_resources.py} 的 resolve_rule 保持同构
 * (RULE_ALIASES / SUFFIX_ALIASES / LAYOUT_ALIASES + raw.json 样本布局)——
 * 改脚本侧映射表时本类要同步。归属不了的 LAYOUT 行返回原名(校准时因
 * 对不上 config 键自动不合入,不猜归属)。
 *
 * @author luosg
 */
public final class OmicsRuleNames {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** benchmark 文件名(去 .benchmark.*)→ config resources 键的固定映射 */
    private static final Map<String, String> RULE_ALIASES = Map.ofEntries(
            Map.entry("hisat2_build", "hisat2_index"),
            Map.entry("RNAseq_report", "generate_report"),
            Map.entry("gsva", "function_gsva"),
            Map.entry("cutadapt_paired", "trimming_Paired"),
            Map.entry("cutadapt_single", "trimming_Single"));

    /** 文件名带 contrast 前缀(<contrast>.<suffix>)的按后缀映射 */
    private static final Map<String, String> SUFFIX_ALIASES = Map.ofEntries(
            Map.entry(".go_kegg", "function_go_kegg"),
            Map.entry(".gsea", "function_gsea"),
            Map.entry(".manhattan", "DESeq2_TElocal_manhattan"),
            Map.entry(".locus_box", "DESeq2_TElocal_locus_boxplot"),
            Map.entry(".subfamily_bar", "DESeq2_TElocal_subfamily_bar"));

    /** PE/SE 两条规则共用同一 benchmark 文件名:(paired, single) */
    private static final Map<String, String[]> LAYOUT_ALIASES = Map.of(
            "trimming", new String[]{"trimming_Paired", "trimming_Single"},
            "bowtie2_align", new String[]{"bowtie2_align_paired", "bowtie2_align_single"});

    private OmicsRuleNames() {
    }

    /**
     * @param base      benchmark 文件名去掉 .benchmark.tsv/.benchmark.txt/.tsv
     * @param fullPath  benchmark 文件完整路径(样本归属用)
     * @param layout    raw.json 样本布局 {sampleId: "paired"/"single"},可空
     */
    public static String resolve(String base, String fullPath, Map<String, String> layout) {
        String direct = RULE_ALIASES.get(base);
        if (direct != null) {
            return direct;
        }
        String[] pair = LAYOUT_ALIASES.get(base);
        if (pair != null) {
            String sid = sampleFromPath(fullPath, layout);
            if (sid == null) {
                return base; // 归属不了:保留原名(校准不合入,不猜)
            }
            return "paired".equals(layout.get(sid)) ? pair[0] : pair[1];
        }
        for (Map.Entry<String, String> e : SUFFIX_ALIASES.entrySet()) {
            if (base.endsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return base;
    }

    /** raw.json → {sampleId: "paired"/"single"};读不到返回空表 */
    public static Map<String, String> loadSampleLayout(Path outputDir) {
        Map<String, String> layout = new HashMap<>();
        for (String cand : new String[]{"raw.json", "common/raw.json"}) {
            Path p = outputDir.resolve(cand);
            if (!Files.isRegularFile(p)) {
                continue;
            }
            try {
                JsonNode wf = MAPPER.readTree(Files.readString(p)).path("Params").path("workflow");
                for (JsonNode arr : wf.path("genome_paired_samples")) {
                    for (JsonNode sid : arr) {
                        layout.put(sid.asText(), "paired");
                    }
                }
                for (JsonNode arr : wf.path("genome_single_samples")) {
                    for (JsonNode sid : arr) {
                        layout.put(sid.asText(), "single");
                    }
                }
                return layout;
            } catch (Exception e) {
                return new HashMap<>();
            }
        }
        return layout;
    }

    /** benchmark 路径分量里与 raw.json 样本名精确匹配的样本 ID */
    private static String sampleFromPath(String fullPath, Map<String, String> layout) {
        if (layout == null || layout.isEmpty()) {
            return null;
        }
        for (String part : fullPath.replace('\\', '/').split("/")) {
            if (layout.containsKey(part)) {
                return part;
            }
        }
        return null;
    }
}

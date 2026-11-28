package com.bioplatform.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 实验设计 design JSON 校验(2026-11-26 换代,run.py --design 唯一入口)。
 * <p>
 * 形状同 sample_params.design:{scope: {groups: {组名: [样本]}, comparisons:
 * {设计名: {groups: [有序组名], ref?, coding?}}, tests?}};下划线开头键为注释位跳过。
 * 规则与 Omics {@code MetaUtil.build_design_model_pairs} 逐条同严:
 * 组样本非空无重复可解析、组内同 data_type、组内同 tissue 且 tissue==scope 键、
 * 对比内组间样本互斥、ref∈groups、coding∈{consecutive,vs_ref,all_pairs,helmert}、
 * 派生对比名(参照组名_vs_检验组名,helmert 参照侧 prev_mean)跨条目唯一。
 * <p>
 * 样本存在性/tissue/data_type 校验依赖 meta TSV({@code metaTsv} 提供时全量校验,
 * 空则仅结构校验)。样本信息(sample_meta)与流水线(pipelines)两处写入共用此口径。
 *
 * @author luosg
 */
public final class DesignJsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern SAMPLE_KEY = Pattern.compile(".+\\|.+");
    private static final Set<String> CODINGS =
            Set.of("consecutive", "vs_ref", "all_pairs", "helmert");
    /** tests 枚举(sample_params.design 定义):pairwise 已消费,omnibus/trend 保留声明 */
    private static final Set<String> TESTS = Set.of("pairwise", "omnibus", "trend");

    private DesignJsonUtil() {
    }

    /**
     * @param json    design JSON 文本;空/空白=未填,放行
     * @param metaTsv 样本 meta TSV(可空;提供时校验样本存在性/组织/数据类型)
     */
    public static void validate(String json, String metaTsv) {
        if (json == null || json.isBlank()) {
            return;
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("实验设计 JSON 解析失败: " + e.getOriginalMessage());
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("实验设计须为 JSON 对象(scope → groups/comparisons)");
        }

        Map<String, List<MetaRow>> byName = parseMeta(metaTsv);
        // 派生对比名跨条目唯一:名字 -> (scope, 设计名)
        Map<String, String[]> derivedNames = new LinkedHashMap<>();

        Iterator<Map.Entry<String, JsonNode>> scopes = root.fields();
        while (scopes.hasNext()) {
            Map.Entry<String, JsonNode> se = scopes.next();
            String scope = se.getKey();
            if (scope.startsWith("_")) {
                continue;
            }
            JsonNode block = se.getValue();
            String where0 = "实验设计." + scope;
            if (!block.isObject()) {
                throw new IllegalArgumentException(where0 + ": 条目必须是对象");
            }
            JsonNode registry = block.get("groups");
            JsonNode comparisons = block.get("comparisons");
            if (registry == null || !registry.isObject() || registry.isEmpty()) {
                throw new IllegalArgumentException(where0 + ".groups 为空——至少注册 2 个组");
            }
            if (comparisons == null || !comparisons.isObject() || countNonComment(comparisons) == 0) {
                throw new IllegalArgumentException(where0 + ".comparisons 为空——没有可执行的对比");
            }
            JsonNode tests = block.get("tests");
            if (tests != null && !tests.isNull()) {
                if (!tests.isArray()) {
                    throw new IllegalArgumentException(where0 + ".tests 须为数组(pairwise/omnibus/trend)");
                }
                for (JsonNode t : tests) {
                    String tv = t.isTextual() ? t.asText().trim() : "";
                    if (!TESTS.contains(tv)) {
                        throw new IllegalArgumentException(where0 + ".tests 含未知检验 '" + tv
                                + "'——合法值 " + TESTS + "(pairwise 已消费,omnibus/trend 保留声明)");
                    }
                }
            }

            // 组注册表:组名 -> 解析后的样本键
            Map<String, List<String>> resolved = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> gs = registry.fields();
            while (gs.hasNext()) {
                Map.Entry<String, JsonNode> ge = gs.next();
                String gname = ge.getKey();
                if (gname.startsWith("_")) {
                    continue;
                }
                String where = where0 + ".groups." + gname;
                JsonNode samples = ge.getValue();
                if (!samples.isArray() || samples.isEmpty()) {
                    throw new IllegalArgumentException(where + ": 需要非空样本列表");
                }
                List<String> keys = new ArrayList<>();
                Set<String> dts = new HashSet<>();
                Set<String> tissues = new HashSet<>();
                for (JsonNode s : samples) {
                    if (!s.isTextual() || s.asText().isBlank()) {
                        throw new IllegalArgumentException(where + ": 每个样本必须是非空字符串");
                    }
                    String key = resolveOne(s.asText(), where, byName);
                    if (keys.contains(key)) {
                        throw new IllegalArgumentException(where + ": 组内样本重复: " + samples);
                    }
                    keys.add(key);
                    MetaRow row = rowOf(key, byName);
                    String dt = row.dataType.toUpperCase();
                    if (!dt.isEmpty()) {
                        dts.add(dt);
                    }
                    tissues.add(row.tissue);
                }
                if (dts.size() > 1) {
                    throw new IllegalArgumentException(
                            where + ": 组内样本跨多个 data_type " + dts + ";一个组只属于一种 data_type");
                }
                if (tissues.size() > 1) {
                    throw new IllegalArgumentException(where + ": 组内样本跨多个组织 " + tissues + ";条目内样本必须同组织");
                }
                String tissue = tissues.iterator().next();
                if (!tissue.equals(scope)) {
                    throw new IllegalArgumentException(where + ": 样本组织为 '" + tissue + "',不是 scope 键 '"
                            + scope + "'(scope = 样本 tissue 属性,缺省 UNKNOWN)");
                }
                resolved.put(gname, keys);
            }

            Iterator<Map.Entry<String, JsonNode>> cs = comparisons.fields();
            while (cs.hasNext()) {
                Map.Entry<String, JsonNode> ce = cs.next();
                String cname = ce.getKey();
                if (cname.startsWith("_")) {
                    continue;
                }
                validateEntry(where0, cname, ce.getValue(), resolved, derivedNames);
            }
        }
    }

    /** 单条对比:结构 + 互斥 + 派生名唯一 */
    private static void validateEntry(String where0, String cname, JsonNode entry,
                                      Map<String, List<String>> resolved,
                                      Map<String, String[]> derivedNames) {
        String where = where0 + ".comparisons." + cname;
        if (!entry.isObject()) {
            throw new IllegalArgumentException(where + ": 条目必须是对象");
        }
        JsonNode gnode = entry.get("groups");
        if (gnode == null || !gnode.isArray() || gnode.size() < 2) {
            throw new IllegalArgumentException(where + ": 'groups' 需要有序的 ≥2 个组名列表");
        }
        List<String> names = new ArrayList<>();
        for (JsonNode n : gnode) {
            if (!n.isTextual() || n.asText().isBlank()) {
                throw new IllegalArgumentException(where + ": 每个组名必须是非空字符串");
            }
            names.add(n.asText());
        }
        if (new HashSet<>(names).size() != names.size()) {
            throw new IllegalArgumentException(where + ": 组名重复: " + names);
        }
        List<String> unknown = new ArrayList<>();
        for (String n : names) {
            if (!resolved.containsKey(n)) {
                unknown.add(n);
            }
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(where + ": 组未注册在 " + where0 + ".groups: " + unknown);
        }
        String ref = entry.hasNonNull("ref") ? entry.get("ref").asText().trim() : null;
        if (ref != null && !ref.isEmpty() && !names.contains(ref)) {
            throw new IllegalArgumentException(where + ": ref '" + ref + "' 不在 " + names + " 中");
        }
        String coding = entry.hasNonNull("coding") ? entry.get("coding").asText().trim() : "consecutive";
        if (coding.isEmpty()) {
            coding = "consecutive";
        }
        if (!CODINGS.contains(coding)) {
            throw new IllegalArgumentException(where + ": 未知 coding '" + coding + "'——合法值 " + CODINGS);
        }

        // 对比内组间样本互斥
        Map<String, String> seen = new HashMap<>();
        for (String n : names) {
            for (String s : resolved.get(n)) {
                if (seen.containsKey(s)) {
                    throw new IllegalArgumentException(where + ": 样本 '" + s + "' 同时属于 '"
                            + seen.get(s) + "' 和 '" + n + "'——同一对比引用的组之间样本必须互斥");
                }
                seen.put(s, n);
            }
        }

        // 派生对比名(参照组名_vs_检验组名;helmert 参照侧 prev_mean)跨条目唯一
        for (String[] pair : derivePairs(names, coding, ref == null || ref.isEmpty() ? null : ref)) {
            String dname = pair[0] + "_vs_" + pair[1];
            String[] prev = derivedNames.get(dname);
            if (prev != null && !(prev[0].equals(where0) && prev[1].equals(cname))) {
                throw new IllegalArgumentException(where + ": 派生对比 '" + dname + "' 与 实验设计."
                        + prev[0] + ".comparisons." + prev[1] + " 撞名——重名派生对比请改组名或组序列");
            }
            derivedNames.put(dname, new String[]{where0, cname});
        }
    }

    /**
     * 派生两两对(参照名, 检验名)——与 MetaUtil.build_design_model_pairs /
     * common.smk comparison_design() 的 pairwise 展开逐条一致。
     */
    public static List<String[]> derivePairs(List<String> names, String coding, String ref) {
        List<String[]> pairs = new ArrayList<>();
        switch (coding) {
            case "vs_ref" -> {
                String r = ref != null && !ref.isEmpty() ? ref : names.get(0);
                for (String n : names) {
                    if (!n.equals(r)) {
                        pairs.add(new String[]{r, n});
                    }
                }
            }
            case "all_pairs" -> {
                for (int i = 0; i < names.size(); i++) {
                    for (int j = i + 1; j < names.size(); j++) {
                        pairs.add(new String[]{names.get(i), names.get(j)});
                    }
                }
            }
            case "helmert" -> {
                for (int k = 1; k < names.size(); k++) {
                    pairs.add(new String[]{"prev_mean", names.get(k)});
                }
            }
            default -> { // consecutive(缺省):相邻两两,前者参照
                for (int i = 0; i < names.size() - 1; i++) {
                    pairs.add(new String[]{names.get(i), names.get(i + 1)});
                }
            }
        }
        return pairs;
    }

    // ==================== meta TSV 解析(样本存在性/组织/数据类型) ====================

    private record MetaRow(String sampleId, String dataType, String tissue) {
    }

    /** meta TSV → 名称索引(sample_id 与 sample_id|data_type 双键);metaTsv 空返回空表 */
    private static Map<String, List<MetaRow>> parseMeta(String metaTsv) {
        Map<String, List<MetaRow>> byName = new HashMap<>();
        if (metaTsv == null || metaTsv.isBlank()) {
            return byName;
        }
        String[] lines = metaTsv.split("\\R");
        if (lines.length == 0) {
            return byName;
        }
        String[] header = lines[0].split("\t", -1);
        int iSid = -1, iDt = -1, iTissue = -1;
        for (int i = 0; i < header.length; i++) {
            String h = header[i].trim();
            if (h.equals("sample_id")) iSid = i;
            else if (h.equals("data_type")) iDt = i;
            else if (h.equals("tissue")) iTissue = i;
        }
        if (iSid < 0) {
            return byName;
        }
        for (int li = 1; li < lines.length; li++) {
            if (lines[li].isBlank()) {
                continue;
            }
            String[] cells = lines[li].split("\t", -1);
            String sid = iSid < cells.length ? cells[iSid].trim() : "";
            if (sid.isEmpty()) {
                continue;
            }
            String dt = iDt >= 0 && iDt < cells.length ? cells[iDt].trim() : "";
            String tissue = iTissue >= 0 && iTissue < cells.length ? cells[iTissue].trim() : "";
            MetaRow row = new MetaRow(sid, dt, tissue.isEmpty() ? "UNKNOWN" : tissue);
            byName.computeIfAbsent(sid, k -> new ArrayList<>()).add(row);
            if (!dt.isEmpty()) {
                byName.computeIfAbsent(sid + "|" + dt, k -> new ArrayList<>()).add(row);
            }
        }
        return byName;
    }

    /** 样本名解析:sample_id 或 sample_id|data_type;不歧义唯一命中(镜像 _resolve_one) */
    private static String resolveOne(String name, String where, Map<String, List<MetaRow>> byName) {
        if (byName.isEmpty()) {
            return name; // 无 meta:静态模式,只查结构
        }
        List<MetaRow> matched = byName.get(name);
        if (matched == null || matched.isEmpty()) {
            throw new IllegalArgumentException(where + ": 样本 '" + name + "' 不在 meta 中");
        }
        if (matched.size() > 1) {
            throw new IllegalArgumentException(where + ": 样本 '" + name + "' 跨多个 data_type,"
                    + "请用唯一键 '<sample_id>|<data_type>'");
        }
        return name;
    }

    private static MetaRow rowOf(String name, Map<String, List<MetaRow>> byName) {
        List<MetaRow> matched = byName.get(name);
        return matched == null || matched.isEmpty()
                ? new MetaRow(name, "", "UNKNOWN") : matched.get(0);
    }

    private static int countNonComment(JsonNode obj) {
        int n = 0;
        Iterator<String> it = obj.fieldNames();
        while (it.hasNext()) {
            if (!it.next().startsWith("_")) {
                n++;
            }
        }
        return n;
    }
}

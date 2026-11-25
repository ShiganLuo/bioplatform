package com.bioplatform.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 实验设计关系 JSON 校验(2026-11-25):Omics run.py --comparison 的列表同构形态。
 * 每项:comparison_id 非空且组内唯一;control_samples/experiment_samples
 * 各≥1 且为字符串数组;组名可选。空/空白=未填,放行。
 * 样本信息(sample_meta)与流水线(pipelines)两处写入共用此口径。
 */
public final class ComparisonJsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ComparisonJsonUtil() {
    }

    public static void validate(String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            JsonNode arr = MAPPER.readTree(json);
            if (!arr.isArray()) {
                throw new IllegalArgumentException("实验设计关系须为 JSON 数组");
            }
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (JsonNode item : arr) {
                String cid = item.hasNonNull("comparison_id") ? item.get("comparison_id").asText().trim() : "";
                if (cid.isEmpty()) {
                    throw new IllegalArgumentException("实验设计关系:对比名称(comparison_id)不能为空");
                }
                if (!ids.add(cid)) {
                    throw new IllegalArgumentException("实验设计关系:对比名称重复: " + cid);
                }
                checkSamples(item, "control_samples", cid);
                checkSamples(item, "experiment_samples", cid);
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("实验设计关系 JSON 解析失败: " + e.getOriginalMessage());
        }
    }

    private static void checkSamples(JsonNode item, String field, String cid) {
        JsonNode arr = item.get(field);
        if (arr == null || !arr.isArray() || arr.size() == 0) {
            throw new IllegalArgumentException("实验设计关系[" + cid + "]: " + field + " 至少需要 1 个样本");
        }
        for (JsonNode s : arr) {
            if (!s.isTextual() || s.asText().isBlank()) {
                throw new IllegalArgumentException("实验设计关系[" + cid + "]: " + field + " 含非法样本名");
            }
        }
    }
}

package com.bioplatform.service.impl;

import com.bioplatform.entity.SampleMeta;
import com.bioplatform.mapper.SampleMetaMapper;
import com.bioplatform.service.SampleMetaService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SampleMetaServiceImpl implements SampleMetaService {

    private final SampleMetaMapper sampleMetaMapper;

    public SampleMetaServiceImpl(SampleMetaMapper sampleMetaMapper) {
        this.sampleMetaMapper = sampleMetaMapper;
    }

    @Override
    public SampleMeta create(SampleMeta sampleMeta) {
        validateComparisonJson(sampleMeta.getComparisonJson());
        sampleMetaMapper.insert(sampleMeta);
        return sampleMeta;
    }

    @Override
    public SampleMeta getById(Long id) {
        return sampleMetaMapper.selectById(id);
    }

    @Override
    public List<SampleMeta> listByProject(Long projectId) {
        return sampleMetaMapper.selectByProjectId(projectId);
    }

    @Override
    public void update(SampleMeta sampleMeta) {
        validateComparisonJson(sampleMeta.getComparisonJson());
        sampleMetaMapper.updateById(sampleMeta);
    }

    @Override
    public void delete(Long id) {
        sampleMetaMapper.deleteById(id);
    }

    /**
     * 实验设计关系 JSON 校验(2026-11-25):Omics --comparison 列表形态。
     * 每项:comparison_id 非空且组内唯一;control_samples/experiment_samples
     * 各≥1 且为字符串数组;组名可选。空/空白=未填,放行。
     */
    private void validateComparisonJson(String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode arr =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            if (!arr.isArray()) {
                throw new IllegalArgumentException("实验设计关系须为 JSON 数组");
            }
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (com.fasterxml.jackson.databind.JsonNode item : arr) {
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
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("实验设计关系 JSON 解析失败: " + e.getOriginalMessage());
        }
    }

    private void checkSamples(com.fasterxml.jackson.databind.JsonNode item, String field, String cid) {
        com.fasterxml.jackson.databind.JsonNode arr = item.get(field);
        if (arr == null || !arr.isArray() || arr.size() == 0) {
            throw new IllegalArgumentException("实验设计关系[" + cid + "]: " + field + " 至少需要 1 个样本");
        }
        for (com.fasterxml.jackson.databind.JsonNode s : arr) {
            if (!s.isTextual() || s.asText().isBlank()) {
                throw new IllegalArgumentException("实验设计关系[" + cid + "]: " + field + " 含非法样本名");
            }
        }
    }
}

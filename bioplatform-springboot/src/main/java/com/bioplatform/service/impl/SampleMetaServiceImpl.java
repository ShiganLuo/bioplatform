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
        validateDesignJson(sampleMeta.getDesignJson(), sampleMeta.getMetaContent());
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
        validateDesignJson(sampleMeta.getDesignJson(), sampleMeta.getMetaContent());
        sampleMetaMapper.updateById(sampleMeta);
    }

    @Override
    public void delete(Long id) {
        sampleMetaMapper.deleteById(id);
    }

    /** 实验设计 design JSON 校验(meta TSV 提供时全量,统一口径见 DesignJsonUtil) */
    private void validateDesignJson(String json, String metaTsv) {
        com.bioplatform.common.util.DesignJsonUtil.validate(json, metaTsv);
    }
}

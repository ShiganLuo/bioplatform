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
        sampleMetaMapper.updateById(sampleMeta);
    }

    @Override
    public void delete(Long id) {
        sampleMetaMapper.deleteById(id);
    }
}

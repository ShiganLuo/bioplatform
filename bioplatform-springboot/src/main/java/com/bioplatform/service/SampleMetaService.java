package com.bioplatform.service;

import com.bioplatform.entity.SampleMeta;
import java.util.List;

public interface SampleMetaService {

    SampleMeta create(SampleMeta sampleMeta);

    SampleMeta getById(Long id);

    List<SampleMeta> listByProject(Long projectId);

    void update(SampleMeta sampleMeta);

    void delete(Long id);
}

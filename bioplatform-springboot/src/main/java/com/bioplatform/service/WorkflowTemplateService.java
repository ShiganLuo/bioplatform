package com.bioplatform.service;

import com.bioplatform.dto.admin.AdminWorkflowTemplateDTO.CreateRequest;
import com.bioplatform.dto.admin.AdminWorkflowTemplateDTO.UpdateRequest;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.WorkflowTemplate;

/**
 * 工作流模板服务接口
 *
 * @author luosg
 */
public interface WorkflowTemplateService {

    WorkflowTemplate createTemplate(CreateRequest request);

    void updateTemplate(UpdateRequest request);

    void deleteTemplate(Long id);

    WorkflowTemplate getTemplateById(Long id);

    PageResult listTemplates(String type, String category, int page, int size);

    /**
     * 从 Omics 仓库目录批量导入模板
     * @param omicsDir Omics 仓库根目录
     * @return 导入数量
     */
    int importFromOmics(String omicsDir);
}

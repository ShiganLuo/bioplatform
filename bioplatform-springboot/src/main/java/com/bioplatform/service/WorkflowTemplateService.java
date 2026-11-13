package com.bioplatform.service;

import com.bioplatform.dto.admin.AdminWorkflowTemplateDTO.CreateRequest;
import com.bioplatform.dto.admin.AdminWorkflowTemplateDTO.UpdateRequest;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.WorkflowTemplate;

import java.util.List;

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

    /** 工作流 meta 规范(Omics/assests/test/meta_<工作流名>.tsv):列固定,示例行供参考 */
    record MetaTemplate(boolean available, List<String> columns, List<String> examples, String filled) {}

    MetaTemplate getMetaTemplate(Long id);

    PageResult listTemplates(String type, String category, int page, int size);

    /**
     * 从 Omics 仓库目录批量导入模板
     * @param omicsDir Omics 仓库根目录
     * @return 导入数量
     */
    int importFromOmics(String omicsDir);
}

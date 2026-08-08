package com.bioplatform.controller.front;

import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.Pipeline;
import com.bioplatform.service.PipelineService;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * Front-end pipeline controller for public viewing.
 *
 * @author luosg
 */
@RestController
@RequestMapping("/api/front/pipelines")
public class FrontPipelineController {

    private final PipelineService pipelineService;

    public FrontPipelineController(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    /**
     * List public pipelines.
     */
    @GetMapping("/list")
    public ApiResponse<PageResult> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String category) {
        PageResult result = pipelineService.listPipelines(category, pageNum, pageSize);
        return ApiResponse.success(result);
    }

    /**
     * Get pipeline detail by id.
     */
    @GetMapping("/{id}")
    public ApiResponse<Pipeline> getById(@PathVariable Long id) {
        Pipeline pipeline = pipelineService.getPipelineById(id);
        if (pipeline == null) {
            return ApiResponse.error(404, "流水线不存在");
        }
        return ApiResponse.success(pipeline);
    }

    /**
     * List pipeline categories.
     */
    @GetMapping("/categories")
    public ApiResponse<List<String>> categories() {
        // TODO: In production, this should query distinct categories from the database
        List<String> categories = Arrays.asList(
                "Sequence Analysis",
                "Variant Calling",
                "Expression Analysis",
                "Metagenomics",
                "Structural Biology",
                "Other"
        );
        return ApiResponse.success(categories);
    }
}

import http from '@/utils/http/axios'

export interface Pipeline {
  id: number
  name: string
  type: string
  templateId: number
  projectId: number
  metaContent: string
  /** 实验设计关系 JSON(Omics --comparison 列表形态),随样本信息"填入"带入 */
  designJson?: string
  metaType: string
  extraParams: string
  description: string
  category: string
  configJson: string
  dockerImage: string
  timeout: number
  ownerId: number
  createdAt: string
  updatedAt: string
  /** 最近一次执行的流程版本(Omics commit;非表字段,列表批量回填) */
  latestWorkflowVersion?: string | null
  latestVersionSource?: string | null
  /** 模板导入时的 Omics 版本 */
  templateWorkflowVersion?: string | null
}

export interface PipelineQuery {
  page?: number
  size?: number
  name?: string
  category?: string
  projectId?: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export function listPipelines(params: PipelineQuery) {
  return http.get<PageResult<Pipeline>>('/api/admin/pipelines/list', { params })
}

export function getPipeline(id: number) {
  return http.get<Pipeline>(`/api/admin/pipelines/${id}`)
}

export function createPipeline(data: Partial<Pipeline>) {
  return http.post<Pipeline>('/api/admin/pipelines/create', data)
}

export function updatePipeline(id: number, data: Partial<Pipeline>) {
  return http.put<Pipeline>(`/api/admin/pipelines/update`, { id, ...data })
}

export function deletePipeline(id: number) {
  return http.delete(`/api/admin/pipelines/${id}`)
}

export function executePipeline(id: number, params?: Record<string, any>) {
  return http.post(`/api/admin/pipelines/${id}/execute`, params)
}

/** 分析维护(2026-11-24):dry-run/unlock/delete-temp-output/touch/forcerun/cleanup-metadata;extra=规则名或文件路径 */
export function maintainPipeline(id: number, mode: string, extra?: string) {
  return http.post<string>(`/api/admin/pipelines/${id}/maintain`, { mode, extra: extra || '' })
}

/** 资源校准两段式(2026-11-26):apply=false 预览建议表,true 护栏写回 config;force=确认混入未提交改动 */
export function calibratePipeline(id: number, apply = false, force = false) {
  return http.post<any>(`/api/admin/pipelines/${id}/calibrate`, { apply, force })
}

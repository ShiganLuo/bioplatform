import http from '@/utils/http/axios'

export interface Project {
  id: number
  name: string
  description: string
  organism: string
  genomeVersion: string
  ownerId: number
  status: number
  isPrivate: boolean
  createdAt: string
  updatedAt: string
}

export interface ProjectQuery {
  page?: number
  size?: number
  name?: string
  organism?: string
}

export interface CreateAnalysisRequest {
  workflowTemplateName: string
  name?: string
  metaContent: string
  metaType: string
  extraParams?: string
  description?: string
}

export interface Pipeline {
  id: number
  name: string
  type: string
  templateId: number
  projectId: number
  metaContent: string
  metaType: string
  extraParams: string
  description: string
  category: string
  configJson: string
  timeout: number
  ownerId: number
  createdAt: string
  updatedAt: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export function listProjects(params: ProjectQuery) {
  return http.get<PageResult<Project>>('/api/admin/projects/list', { params })
}

export function getProject(id: number) {
  return http.get<Project>(`/api/admin/projects/${id}`)
}

export function createProject(data: Partial<Project>) {
  return http.post<Project>('/api/admin/projects/create', data)
}

export function updateProject(id: number, data: Partial<Project>) {
  return http.put<Project>('/api/admin/projects/update', { id, ...data })
}

export function deleteProject(id: number) {
  return http.delete(`/api/admin/projects/${id}`)
}

export function createAnalysis(projectId: number, data: CreateAnalysisRequest) {
  return http.post<Pipeline>(`/api/admin/projects/${projectId}/analyses`, data)
}

export function listAnalyses(projectId: number, params?: { page?: number; size?: number }) {
  return http.get<PageResult<Pipeline>>(`/api/admin/projects/${projectId}/analyses`, { params })
}

// ========== 导出 API ==========

export interface FileTreeNode {
  name: string
  path: string
  filePath: string
  directory: boolean
  size: number | null
  fileType: string | null
  modifiedAt: string | null
  executionId: number | null
  pipelineName: string | null
  children?: FileTreeNode[]
}

/** 下载项目 Excel 报表 */
export function exportExcel(projectId: number) {
  return http.get(`/api/admin/projects/${projectId}/export/excel`, { responseType: 'blob' })
}

/** 下载项目 PPT 报告 */
export function exportPpt(projectId: number) {
  return http.get(`/api/admin/projects/${projectId}/export/ppt`, { responseType: 'blob' })
}

/** 获取项目文件树 */
export function getFileTree(projectId: number) {
  return http.get<FileTreeNode[]>(`/api/admin/projects/${projectId}/files/tree`)
}

/** 批量打包下载文件（按路径） */
export function batchDownload(projectId: number, filePaths: string[]) {
  return http.post(`/api/admin/projects/${projectId}/files/batch-download`, filePaths, { responseType: 'blob' })
}

/** 全部下载（zip） */
export function downloadAll(projectId: number) {
  return http.get(`/api/admin/projects/${projectId}/export/download-all`, { responseType: 'blob' })
}

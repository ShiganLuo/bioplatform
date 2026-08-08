import http from '@/utils/http/axios'

export interface DataFile {
  id: number
  projectId: number
  projectName: string
  fileName: string
  filePath: string
  fileSize: number
  fileType: string
  mimeType: string
  uploader: string
  createTime: string
}

export interface DataFileQuery {
  page?: number
  size?: number
  projectId?: number
  fileName?: string
  fileType?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export function listFiles(params: DataFileQuery) {
  return http.get<PageResult<DataFile>>('/api/data-files', { params })
}

export function uploadFile(file: File, projectId: number) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('projectId', projectId.toString())
  return http.post<DataFile>('/api/admin/datafiles/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function deleteFile(id: number) {
  return http.delete(`/api/admin/datafiles/${id}`)
}

export function downloadFile(id: number) {
  return http.get(`/api/admin/datafiles/${id}/download`, { responseType: 'blob' })
}

export function getFileInfo(id: number) {
  return http.get<DataFile>(`/api/admin/datafiles/${id}`)
}

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

export interface StorageInfo {
  diskTotal: number
  diskUsed: number
  diskFree: number
  userQuota: number
  userUsed: number
  userRemaining: number
  pendingSize: number
  canUpload: boolean
  reason: string | null
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export function listFiles(params: DataFileQuery) {
  return http.get<PageResult<DataFile>>('/api/admin/datafiles/list', { params })
}

export function storageCheck(pendingSize: number = 0) {
  return http.get<StorageInfo>('/api/admin/datafiles/storage-check', {
    params: { pendingSize }
  })
}

export function uploadFile(file: File, projectId: number) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('projectId', projectId.toString())
  return http.post<DataFile>('/api/admin/datafiles/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function batchUploadFiles(files: File[], relativePaths: string[], projectId: number) {
  const formData = new FormData()
  files.forEach(file => formData.append('files', file))
  relativePaths.forEach(p => formData.append('relativePaths', p))
  formData.append('projectId', projectId.toString())
  return http.post<DataFile[]>('/api/admin/datafiles/batch-upload', formData, {
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

export interface RsyncInfo {
  host: string
  port: string
  uploadPath: string
  example: string
}

export function getRsyncInfo() {
  return http.get<RsyncInfo>('/api/admin/datafiles/rsync-info')
}

export function importLocalFiles(dirPath: string, projectId: number) {
  return http.post<{ count: number; dirPath: string }>('/api/admin/datafiles/import-local', null, {
    params: { dirPath, projectId }
  })
}

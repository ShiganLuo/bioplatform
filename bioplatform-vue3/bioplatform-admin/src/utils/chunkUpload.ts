import http from '@/utils/http/axios'
import type { DataFile } from '@/api/dataFileApi'

/**
 * 分片上传配置
 */
const CHUNK_SIZE = 10 * 1024 * 1024 // 10MB per chunk
const MAX_CONCURRENT = 3 // 最大并发数
const MAX_RETRIES = 3 // 每片最大重试次数

export interface ChunkUploadOptions {
  file: File
  projectId: number
  onProgress?: (progress: ChunkUploadProgress) => void
  onSuccess?: (file: DataFile) => void
  onError?: (error: Error) => void
}

export interface ChunkUploadProgress {
  /** 已上传字节数 */
  loaded: number
  /** 文件总大小 */
  total: number
  /** 百分比 0-100 */
  percent: number
  /** 当前状态 */
  status: 'uploading' | 'merging' | 'done' | 'error'
  /** 已完成分片数 */
  completedChunks: number
  /** 总分片数 */
  totalChunks: number
}

/**
 * 生成 uploadId（基于文件名+大小+最后修改时间，用于断点续传）
 */
function generateUploadId(file: File): string {
  const raw = `${file.name}_${file.size}_${file.lastModified}`
  // 简单 hash
  let hash = 0
  for (let i = 0; i < raw.length; i++) {
    const char = raw.charCodeAt(i)
    hash = ((hash << 5) - hash) + char
    hash |= 0
  }
  return `chunk_${Math.abs(hash).toString(16)}_${file.size}`
}

/**
 * 分片上传单个文件
 * - 自动切片
 * - 并行上传（最多 MAX_CONCURRENT 片同时传）
 * - 断点续传（跳过已上传的分片）
 * - 失败自动重试
 */
export async function chunkUpload(options: ChunkUploadOptions): Promise<DataFile> {
  const { file, projectId, onProgress, onSuccess, onError } = options

  const uploadId = generateUploadId(file)
  const totalChunks = Math.ceil(file.size / CHUNK_SIZE)

  // 1. 查询已上传的分片（断点续传）
  let uploadedSet = new Set<number>()
  try {
    const statusRes = await http.get<{ uploadedChunks: number[] }>(
      '/api/admin/datafiles/upload-status',
      { params: { uploadId }, silent: true } as any
    )
    const data = statusRes as any
    uploadedSet = new Set(data.uploadedChunks || [])
  } catch {
    // 首次上传，没有已上传分片
  }

  // 2. 计算进度
  const completedFromResume = uploadedSet.size
  let loadedBytes = completedFromResume * CHUNK_SIZE
  // 修正最后一片可能不满 CHUNK_SIZE
  if (completedFromResume > 0 && completedFromResume === totalChunks) {
    loadedBytes = file.size
  }

  const reportProgress = (completed: number, status: ChunkUploadProgress['status']) => {
    const percent = Math.round((loadedBytes / file.size) * 100)
    onProgress?.({
      loaded: Math.min(loadedBytes, file.size),
      total: file.size,
      percent: Math.min(percent, 100),
      status,
      completedChunks: completed,
      totalChunks
    })
  }

  reportProgress(completedFromResume, 'uploading')

  // 3. 生成待上传分片列表
  const pendingChunks: number[] = []
  for (let i = 0; i < totalChunks; i++) {
    if (!uploadedSet.has(i)) {
      pendingChunks.push(i)
    }
  }

  // 所有分片已上传，直接合并
  if (pendingChunks.length === 0) {
    reportProgress(totalChunks, 'merging')
    const result = await mergeChunks(uploadId, file.name, projectId)
    reportProgress(totalChunks, 'done')
    onSuccess?.(result)
    return result
  }

  // 4. 并行上传分片
  let nextIndex = 0
  let hasError = false

  const uploadOne = async (): Promise<void> => {
    while (nextIndex < pendingChunks.length && !hasError) {
      const idx = nextIndex++
      const chunkIndex = pendingChunks[idx]
      await uploadChunkWithRetry(uploadId, file, chunkIndex, totalChunks, MAX_RETRIES)
      loadedBytes += getChunkSize(file, chunkIndex)
      reportProgress(completedFromResume + idx + 1, 'uploading')
    }
  }

  const workers = Math.min(MAX_CONCURRENT, pendingChunks.length)
  try {
    await Promise.all(Array.from({ length: workers }, () => uploadOne()))
  } catch (error) {
    hasError = true
    const err = error instanceof Error ? error : new Error('分片上传失败')
    onError?.(err)
    throw err
  }

  // 5. 合并分片
  reportProgress(totalChunks, 'merging')
  const result = await mergeChunks(uploadId, file.name, projectId)
  reportProgress(totalChunks, 'done')
  onSuccess?.(result)
  return result
}

/**
 * 上传单个分片（带重试）
 */
async function uploadChunkWithRetry(
  uploadId: string,
  file: File,
  chunkIndex: number,
  totalChunks: number,
  maxRetries: number
): Promise<void> {
  const start = chunkIndex * CHUNK_SIZE
  const end = Math.min(start + CHUNK_SIZE, file.size)
  const chunk = file.slice(start, end)

  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      const formData = new FormData()
      formData.append('chunk', chunk)
      formData.append('uploadId', uploadId)
      formData.append('chunkIndex', chunkIndex.toString())
      formData.append('totalChunks', totalChunks.toString())
      formData.append('fileName', file.name)

      await http.post('/api/admin/datafiles/upload-chunk', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 120000, // 单片超时 2 分钟
        silent: true
      } as any)
      return
    } catch (error) {
      if (attempt === maxRetries) {
        throw new Error(`分片 ${chunkIndex} 上传失败（已重试 ${maxRetries} 次）`)
      }
      // 等待后重试
      await new Promise(r => setTimeout(r, 1000 * (attempt + 1)))
    }
  }
}

/**
 * 获取单个分片的实际大小
 */
function getChunkSize(file: File, chunkIndex: number): number {
  const start = chunkIndex * CHUNK_SIZE
  const end = Math.min(start + CHUNK_SIZE, file.size)
  return end - start
}

/**
 * 调用后端合并分片
 */
async function mergeChunks(uploadId: string, fileName: string, projectId: number): Promise<DataFile> {
  const formData = new FormData()
  formData.append('uploadId', uploadId)
  formData.append('fileName', fileName)
  formData.append('projectId', projectId.toString())

  return http.post<DataFile>('/api/admin/datafiles/merge-chunks', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * 判断文件是否应该使用分片上传（大于 5MB）
 */
export function shouldUseChunkUpload(file: File): boolean {
  return file.size > 5 * 1024 * 1024
}

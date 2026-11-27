import http from '@/utils/http/axios'

/** 样本 meta 中单条文件引用的解析结果 */
export interface SampleMetaFileRef {
  rawPath: string
  matched: boolean
  fileId: number | null
  fileName: string | null
  /** 0=丢失 1=在库（未匹配为 null） */
  fileStatus: number | null
  /** rawPath 本身在磁盘上是否存在（仅绝对路径探测） */
  onDisk: boolean
}

export interface SampleMetaFilesResult {
  metaId: number
  refs: SampleMetaFileRef[]
  totalRefs: number
  matchedRefs: number
}

/** 解析 meta 的文件引用并匹配 data_files */
export function getMetaFiles(metaId: number) {
  return http.get<SampleMetaFilesResult>(`/api/admin/sample-meta/${metaId}/files`)
}

/** 将 meta 中未入库、但磁盘上存在的引用登记进 data_files */
export function registerMissingMetaFiles(metaId: number) {
  return http.post<{ count: number }>(`/api/admin/sample-meta/${metaId}/register-missing`)
}

/** 批量文件引用状态汇总(列表标签主动获取,2026-11-26) */
export interface SampleMetaFileSummary {
  metaId: number
  totalRefs: number
  matchedRefs: number
  /** 已入库但磁盘丢失 */
  lostRefs: number
  /** 未匹配到 data_files */
  unmatchedRefs: number
}

export function getMetaFilesSummary(ids: number[]) {
  return http.post<SampleMetaFileSummary[]>('/api/admin/sample-meta/files/summary', { ids })
}

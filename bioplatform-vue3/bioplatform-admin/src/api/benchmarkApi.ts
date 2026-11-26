import http from '@/utils/http/axios'

/** 规则聚合行(按 规则×版本) */
export interface SummaryRow {
  ruleName: string
  workflowVersion: string
  n: number
  nCensored: number
  rssP50Mb: number | null
  rssP90Mb: number | null
  rssMaxMb: number | null
  secP50: number | null
  secP90: number | null
  suggestMb: number | null
  declaredMb: number | null
  deltaPct: number | null
  note: string
}

export interface VersionInfo {
  version: string
  execCount: number
  pointCount: number
}

export interface BenchmarkRow {
  id: number
  executionId: number
  pipelineId: number
  workflow: string
  ruleName: string
  sample: string
  genome: string
  attempt: number
  censored: boolean
  seconds: number | null
  maxRssMb: number | null
  maxVmsMb: number | null
  benchmarkPath: string
  workflowVersion: string
  createdAt: string
  executionStartedAt?: string
}

export interface BenchPageResult {
  records: BenchmarkRow[]
  total: number
  page: number
  size: number
}

export interface SuggestRow {
  rule: string
  nOk: number
  nCensored: number
  observedP90Mb: string
  suggestMb: string
  prevDeclaredMb: number | null
  deltaPct: string
  note: string
}

export interface CalibrateResult {
  version: string
  applied: boolean
  historyRows: number
  rows: SuggestRow[]
  diff: string
  log: string
}

export interface BackfillResult {
  executions: number
  inferred: number
  unknown: number
  rowsInserted: number
}

/** 规则聚合(版本缺省=当前 HEAD,version='ALL' 不筛) */
export function getSummary(pipelineId: number, workflow?: string, version?: string) {
  return http.get<SummaryRow[]>('/api/admin/benchmarks/summary', { params: { pipelineId, workflow, version } })
}

export function getVersions(pipelineId: number) {
  return http.get<VersionInfo[]>('/api/admin/benchmarks/versions', { params: { pipelineId } })
}

export function getBenchmarkList(params: { pipelineId: number; rule?: string; executionId?: number; version?: string; page?: number; pageSize?: number }) {
  return http.get<BenchPageResult>('/api/admin/benchmarks/list', { params })
}

export function getTrend(pipelineId: number, rule?: string) {
  return http.get<BenchmarkRow[]>('/api/admin/benchmarks/trend', { params: { pipelineId, rule } })
}

/** 历史回填(终态执行收割+版本推断) */
export function backfillBenchmarks(pipelineId: number) {
  return http.post<BackfillResult>(`/api/admin/benchmarks/backfill?pipelineId=${pipelineId}`)
}

/** 资源校准两段式(apply=false 预览) */
export function calibrateResources(pipelineId: number, apply = false, force = false) {
  return http.post<CalibrateResult>(`/api/admin/pipelines/${pipelineId}/calibrate`, { apply, force })
}

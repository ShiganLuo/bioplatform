<template>
  <div class="performance-page">
    <el-card shadow="never">
      <div class="toolbar">
        <el-select v-model="pipelineId" placeholder="选择流水线" style="width: 260px" @change="onPipelineChange">
          <el-option v-for="p in pipelines" :key="p.id" :label="`${p.name} (#${p.id})`" :value="p.id" />
        </el-select>
        <el-select v-model="version" placeholder="流程版本" style="width: 220px" @change="loadSummary">
          <el-option label="当前版本" value="" />
          <el-option label="全部版本" value="ALL" />
          <el-option v-for="v in versions" :key="v.version" :value="v.version">
            {{ shortSha(v.version) }} (执行 {{ v.execCount }} / 数据点 {{ v.pointCount }})
          </el-option>
        </el-select>
        <el-button type="primary" :loading="loading" @click="loadAll">刷新</el-button>
        <el-button type="warning" :loading="backfilling" @click="runBackfill">回填历史</el-button>
        <el-tooltip content="回填:扫终态执行收割 benchmark 文件并按 git log 推断历史版本(幂等)" placement="top">
          <el-icon style="margin-left: 4px"><QuestionFilled /></el-icon>
        </el-tooltip>
        <template v-if="canCalibrate">
          <el-button type="success" :loading="calibLoading" @click="openCalibrate">资源校准</el-button>
          <el-tooltip content="校准:按当前版本 benchmark 历史生成 mem_mb 建议(±30% 护栏),预览确认后写回 Omics config 并留 git 痕迹(仅管理员)" placement="top">
            <el-icon style="margin-left: 4px"><QuestionFilled /></el-icon>
          </el-tooltip>
        </template>
      </div>
      <div v-if="backfillMsg" class="backfill-msg">{{ backfillMsg }}</div>

      <!-- 规则聚合 -->
      <el-table :data="summary" v-loading="loading" size="small" style="width: 100%; margin-top: 12px" border>
        <el-table-column type="expand" width="34">
          <template #default="{ row }">
            <div class="res-design">
              <div class="res-design-title">资源完整声明(config resources.<b>{{ row.ruleName }}</b>,展开行)</div>
              <div class="res-design-grid">
                <div class="res-item"><span class="res-k">threads</span><span class="res-v">{{ resField(row, 'threads') }}</span></div>
                <div class="res-item"><span class="res-k">mem_mb (基准)</span><span class="res-v">{{ resField(row, 'mem_mb') }}</span></div>
                <div class="res-item"><span class="res-k">io_bandwidth_mb_s</span><span class="res-v">{{ resField(row, 'io_bandwidth_mb_s') }}</span></div>
                <div class="res-item"><span class="res-k">scale.size_metric</span><span class="res-v">{{ resField(row, 'scale.size_metric') }}</span></div>
                <div class="res-item"><span class="res-k">scale.ref_input_size</span><span class="res-v">{{ resField(row, 'scale.ref_input_size') }}</span></div>
                <div class="res-item"><span class="res-k">scale.fixed_frac</span><span class="res-v">{{ resField(row, 'scale.fixed_frac') }}</span></div>
                <div class="res-item"><span class="res-k">scale.min_mem_mb</span><span class="res-v">{{ resField(row, 'scale.min_mem_mb') }}</span></div>
                <div class="res-item"><span class="res-k">scale.max_mem_mb</span><span class="res-v">{{ resField(row, 'scale.max_mem_mb') }}</span></div>
              </div>
              <div class="res-design-note">
                缩放公式(两段式):实际内存 = mem_mb × (fixed_frac + (1−fixed_frac) × 实际规模 / ref_input_size),截断于 [min_mem_mb, max_mem_mb];
                未配 scale 的规则按固定 mem_mb 执行。size_metric 取值 fastq_mb / bam_mb / sample_count。
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="ruleName" label="规则" min-width="150" show-overflow-tooltip />
        <el-table-column label="版本" width="110">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ shortSha(row.workflowVersion) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="n" label="n" width="55" align="center" />
        <el-table-column prop="nCensored" label="删失" width="55" align="center" />
        <el-table-column label="max_rss p50 (MB)" width="130" align="right">
          <template #default="{ row }">{{ fmt(row.rssP50Mb) }}</template>
        </el-table-column>
        <el-table-column label="max_rss p90 (MB)" width="130" align="right">
          <template #default="{ row }">{{ fmt(row.rssP90Mb) }}</template>
        </el-table-column>
        <el-table-column label="max_rss 峰值 (MB)" width="135" align="right">
          <template #default="{ row }">{{ fmt(row.rssMaxMb) }}</template>
        </el-table-column>
        <el-table-column label="耗时 p50 (s)" width="110" align="right">
          <template #default="{ row }">{{ fmt(row.secP50) }}</template>
        </el-table-column>
        <el-table-column label="耗时 p90 (s)" width="110" align="right">
          <template #default="{ row }">{{ fmt(row.secP90) }}</template>
        </el-table-column>
        <el-table-column label="建议 mem_mb" width="110" align="right">
          <template #default="{ row }">
            <b v-if="row.suggestMb != null">{{ fmt(row.suggestMb) }}</b>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="现声明值" width="95" align="right">
          <template #default="{ row }">{{ row.declaredMb != null ? row.declaredMb : '-' }}</template>
        </el-table-column>
        <el-table-column label="delta" width="80" align="right">
          <template #default="{ row }">
            <span v-if="row.deltaPct != null" :style="{ color: row.deltaPct > 0 ? '#e6a23c' : '#67c23a' }">
              {{ row.deltaPct > 0 ? '+' : '' }}{{ row.deltaPct.toFixed(0) }}%
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="对齐状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.deltaPct != null && Math.abs(row.deltaPct) <= 5" size="small" type="success">已对齐</el-tag>
            <el-tag v-else-if="row.deltaPct != null" size="small" type="warning">待更新</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="note" label="备注" min-width="120" show-overflow-tooltip />
        <template #empty>
          <div class="empty-hint">该流水线暂无 benchmark 数据。<br />执行过带 benchmark 的运行后终态自动收割入库；历史运行可点「回填历史」补录。</div>
        </template>
      </el-table>

      <!-- 趋势(无聚合行时不渲染,不留 320px 空洞) -->
      <template v-if="ruleOptions.length > 0">
        <div class="trend-bar">
          <span class="trend-title">趋势(max_rss,按执行时序,按版本分段)</span>
          <el-select v-model="trendRule" placeholder="选择规则" style="width: 240px" @change="loadTrend">
            <el-option v-for="r in ruleOptions" :key="r" :label="r" :value="r" />
          </el-select>
        </div>
        <div ref="trendEl" class="trend-chart" />
      </template>

      <!-- 明细 -->
      <el-table :data="detail" size="small" border style="width: 100%; margin-top: 8px" v-loading="detailLoading">
        <el-table-column prop="executionId" label="执行" width="70" align="center" />
        <el-table-column label="版本" width="100">
          <template #default="{ row }">{{ shortSha(row.workflowVersion) }}</template>
        </el-table-column>
        <el-table-column prop="ruleName" label="规则" min-width="140" show-overflow-tooltip />
        <el-table-column prop="sample" label="样本" width="110" show-overflow-tooltip />
        <el-table-column prop="genome" label="基因组" width="90" show-overflow-tooltip />
        <el-table-column prop="attempt" label="attempt" width="75" align="center" />
        <el-table-column label="删失" width="60" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.censored" size="small" type="danger">OOM</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="seconds" width="90" align="right">
          <template #default="{ row }">{{ fmt(row.seconds) }}</template>
        </el-table-column>
        <el-table-column label="max_rss (MB)" width="115" align="right">
          <template #default="{ row }">{{ fmt(row.maxRssMb) }}</template>
        </el-table-column>
        <el-table-column prop="benchmarkPath" label="来源文件" min-width="220" show-overflow-tooltip />
        <template #empty>
          <div class="empty-hint">该流水线暂无 benchmark 明细。先在左上角选择执行过 benchmark 的流水线（如 DR-分析），或点「回填历史」补录。</div>
        </template>
      </el-table>
      <el-pagination
        v-model:current-page="detailPage"
        :page-size="20"
        :total="detailTotal"
        layout="total, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="loadDetail"
      />
    </el-card>

    <!-- 资源校准(仅管理员):预览建议表→应用写回 config -->
    <el-dialog v-model="calibVisible" title="资源校准" width="880px" top="6vh">
      <div class="backfill-msg" style="margin-bottom: 10px">
        按<b>当前版本</b>的 benchmark 历史生成 mem_mb 建议(p90×1.3 后 ±30% 限幅,n≥3 才给值),
        应用时经护栏写回 Omics config 并留 git diff 痕迹(可回滚)。仅管理员可操作。
      </div>
      <el-table :data="calibRows" size="small" border v-loading="calibLoading">
        <el-table-column type="expand" width="34">
          <template #default="{ row }">
            <div class="res-design">
              <div class="res-design-title">资源完整声明(config resources.<b>{{ row.rule }}</b>,展开行)</div>
              <div class="res-design-grid">
                <div class="res-item"><span class="res-k">threads</span><span class="res-v">{{ resField(row, 'threads') }}</span></div>
                <div class="res-item"><span class="res-k">mem_mb (基准)</span><span class="res-v">{{ resField(row, 'mem_mb') }}</span></div>
                <div class="res-item"><span class="res-k">io_bandwidth_mb_s</span><span class="res-v">{{ resField(row, 'io_bandwidth_mb_s') }}</span></div>
                <div class="res-item"><span class="res-k">scale.size_metric</span><span class="res-v">{{ resField(row, 'scale.size_metric') }}</span></div>
                <div class="res-item"><span class="res-k">scale.ref_input_size</span><span class="res-v">{{ resField(row, 'scale.ref_input_size') }}</span></div>
                <div class="res-item"><span class="res-k">scale.fixed_frac</span><span class="res-v">{{ resField(row, 'scale.fixed_frac') }}</span></div>
                <div class="res-item"><span class="res-k">scale.min_mem_mb</span><span class="res-v">{{ resField(row, 'scale.min_mem_mb') }}</span></div>
                <div class="res-item"><span class="res-k">scale.max_mem_mb</span><span class="res-v">{{ resField(row, 'scale.max_mem_mb') }}</span></div>
              </div>
              <div class="res-design-note">
                缩放公式(两段式):实际内存 = mem_mb × (fixed_frac + (1−fixed_frac) × 实际规模 / ref_input_size),截断于 [min_mem_mb, max_mem_mb];
                未配 scale 的规则按固定 mem_mb 执行。size_metric 取值 fastq_mb / bam_mb / sample_count。
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="rule" label="规则" min-width="150" show-overflow-tooltip />
        <el-table-column prop="nOk" label="n" width="55" align="center" />
        <el-table-column prop="nCensored" label="删失" width="55" align="center" />
        <el-table-column label="观测 p90 (MB)" width="120" align="right">
          <template #default="{ row }">{{ row.observedP90Mb }}</template>
        </el-table-column>
        <el-table-column label="建议 mem_mb" width="110" align="right">
          <template #default="{ row }"><b>{{ row.suggestMb }}</b></template>
        </el-table-column>
        <el-table-column label="现声明值" width="95" align="right">
          <template #default="{ row }">{{ row.prevDeclaredMb != null ? row.prevDeclaredMb : '-' }}</template>
        </el-table-column>
        <el-table-column label="delta" width="80" align="right">
          <template #default="{ row }">{{ row.deltaPct ? (Number(row.deltaPct) > 0 ? '+' : '') + Number(row.deltaPct).toFixed(0) + '%' : '-' }}</template>
        </el-table-column>
        <el-table-column prop="note" label="备注" min-width="130" show-overflow-tooltip />
        <template #empty>
          <div class="empty-hint">没有可校准的规则(需当前版本 ≥3 个有效数据点的 benchmark 历史;历史多为旧版本时可先在 Omics 仓库切回对应版本再校准)。</div>
        </template>
      </el-table>
      <div v-if="calibMsg" class="backfill-msg" style="margin-top: 8px">{{ calibMsg }}</div>
      <template #footer>
        <el-button @click="calibVisible = false">关闭</el-button>
        <el-button type="success" :loading="calibApplying" :disabled="!calibRows.length" @click="applyCalibration">应用写回 config</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { useUserStore } from '@/stores/user'
import { listPipelines, type Pipeline } from '@/api/pipelineApi'
import {
  getSummary, getVersions, getBenchmarkList, getTrend, backfillBenchmarks, calibrateResources,
  type SummaryRow, type VersionInfo, type BenchmarkRow, type SuggestRow
} from '@/api/benchmarkApi'

const pipelines = ref<Pipeline[]>([])
const pipelineId = ref<number | null>(null)
const versions = ref<VersionInfo[]>([])
// 默认全部版本:历史执行的 benchmark 多为旧版本,默认"当前版本"会把聚合表滤成空表
// 而明细表不筛(口径不一致,2026-11-26 用户报"中间一段空白"实测定位)
const version = ref('ALL')
const summary = ref<SummaryRow[]>([])
const loading = ref(false)

const trendRule = ref('')
const trendEl = ref<HTMLElement | null>(null)
let chart: ReturnType<typeof echarts.init> | null = null

const detail = ref<BenchmarkRow[]>([])
const detailLoading = ref(false)
const detailPage = ref(1)
const detailTotal = ref(0)

const backfilling = ref(false)
const backfillMsg = ref('')
const userStore = useUserStore()
const canCalibrate = computed(() => userStore.hasPermission('pipeline:calibrate'))
const calibVisible = ref(false)
const calibLoading = ref(false)
const calibApplying = ref(false)
const calibRows = ref<SuggestRow[]>([])
const calibMsg = ref('')
const calibVersion = ref('')

// 展开行:取资源声明字段(支持 scale.xxx 深层路径)
const resField = (row: any, path: string): string => {
  const r = row?.declaredResource
  if (!r) return '-'
  let v: any = r
  for (const k of path.split('.')) {
    if (v == null || typeof v !== 'object') return '-'
    v = v[k]
  }
  return v === null || v === undefined ? '-' : String(v)
}

const openCalibrate = async () => {
  if (!pipelineId.value) { ElMessage.warning('请先选择流水线'); return }
  calibVisible.value = true
  calibMsg.value = ''
  calibRows.value = []
  calibLoading.value = true
  try {
    const res = await calibrateResources(pipelineId.value, false, false)
    calibRows.value = res.rows || []
    calibVersion.value = res.version || ''
    calibMsg.value = `版本 ${calibVersion.value.substring(0, 8)} · 历史 ${res.historyRows} 行`
  } catch (e: any) {
    calibMsg.value = '校准预览失败: ' + (e?.message || e)
  } finally {
    calibLoading.value = false
  }
}

const applyCalibration = async () => {
  const pid = pipelineId.value
  if (!pid) return
  try {
    await ElMessageBox.confirm(
      `将按护栏把建议值写回 Omics config(版本 ${calibVersion.value.substring(0, 8)}),mem_mb 以外的声明(threads/scale)不动。确认写回?`,
      '资源校准写回', { type: 'warning', confirmButtonText: '写回', cancelButtonText: '取消' })
  } catch { return }
  calibApplying.value = true
  try {
    const res = await calibrateResources(pid, true, false)
    calibRows.value = res.rows || []
    calibMsg.value = '写回完成(改动已留痕,git 可回滚)\n' + (res.diff || '')
    ElMessage.success('写回完成')
    loadSummary()
  } catch (e: any) {
    const msg = String(e?.message || e)
    if (msg.includes('force') || msg.includes('未提交')) {
      try {
        await ElMessageBox.confirm('Omics 工作区有未提交改动,写回会与手头编辑混在一起。仍要继续吗?', '带脏写回确认',
          { type: 'warning', confirmButtonText: '继续写回', cancelButtonText: '取消' })
      } catch { calibApplying.value = false; return }
      try {
        const res = await calibrateResources(pid, true, true)
        calibRows.value = res.rows || []
        calibMsg.value = '写回完成(改动已留痕,git 可回滚)\n' + (res.diff || '')
        ElMessage.success('写回完成')
        loadSummary()
      } catch (e2: any) {
        calibMsg.value = '写回失败: ' + (e2?.message || e2)
      }
    } else {
      calibMsg.value = '写回失败: ' + msg
    }
  } finally {
    calibApplying.value = false
  }
}

const ruleOptions = computed(() => summary.value.map(r => r.ruleName))

function shortSha(v: string | null | undefined): string {
  if (!v) return 'unknown'
  return v === 'ALL' ? 'all' : v.substring(0, 8)
}

function fmt(v: number | null | undefined): string {
  if (v == null) return '-'
  return Number.isInteger(v) ? String(v) : v.toFixed(1)
}

async function loadPipelines() {
  const res = await listPipelines({ page: 1, size: 200 })
  pipelines.value = res.records || []
  if (pipelines.value.length > 0 && pipelineId.value == null) {
    pipelineId.value = pipelines.value[0].id
    await onPipelineChange()
  }
}

async function onPipelineChange() {
  version.value = 'ALL'
  trendRule.value = ''
  detailPage.value = 1
  // 回填提示是流水线级结果,切换流水线即失效——不清会误导成新选中流水线的状态
  backfillMsg.value = ''
  await loadAll()
}

async function loadAll() {
  if (pipelineId.value == null) return
  await loadVersions()
  await loadSummary()
  await loadDetail()
}

async function loadVersions() {
  if (pipelineId.value == null) return
  try {
    versions.value = await getVersions(pipelineId.value)
  } catch {
    versions.value = []
  }
}

/** 聚合行变化后校正趋势规则并重画(版本切换/切管线都要走,漏了图表永不初始化) */
function syncTrendRuleAndLoad() {
  if (!trendRule.value || !ruleOptions.value.includes(trendRule.value)) {
    trendRule.value = ruleOptions.value[0] || ''
  }
  return loadTrend()
}

async function loadSummary() {
  if (pipelineId.value == null) return
  loading.value = true
  try {
    summary.value = await getSummary(pipelineId.value, undefined, version.value || undefined)
    await syncTrendRuleAndLoad()
  } finally {
    loading.value = false
  }
}

async function loadTrend() {
  if (pipelineId.value == null || !trendRule.value) return
  const rows = await getTrend(pipelineId.value, trendRule.value)
  renderTrend(rows)
}

/** 版本分段:每版本一个系列(分色),版本切换处标竖线 */
function renderTrend(rows: BenchmarkRow[]) {
  if (!trendEl.value) return
  if (chart && !chart.getDom().isConnected) {
    chart.dispose()
    chart = null
  }
  if (!chart) {
    chart = echarts.init(trendEl.value)
    window.addEventListener('resize', () => chart?.resize())
  }
  const byVersion = new Map<string, BenchmarkRow[]>()
  for (const r of rows) {
    const v = r.workflowVersion || 'unknown'
    if (!byVersion.has(v)) byVersion.set(v, [])
    byVersion.get(v)!.push(r)
  }
  const series: any[] = []
  const markLineData: any[] = []
  let prevLastTime: string | number | null = null
  for (const [v, list] of byVersion) {
    const data = list
      .filter(r => r.maxRssMb != null)
      .map(r => [r.executionStartedAt || r.createdAt, r.maxRssMb, r.executionId])
    series.push({
      name: shortSha(v),
      type: 'scatter',
      symbolSize: 8,
      data,
      emphasis: { scale: 1.4 }
    })
    const firstTime = data.length > 0 ? data[0][0] : null
    if (prevLastTime && firstTime) {
      markLineData.push({ xAxis: firstTime })
    }
    if (data.length > 0) prevLastTime = data[data.length - 1][0]
  }
  chart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (p: any) =>
        `执行 #${p.data[2]}<br/>${p.seriesName}<br/>${p.data[0]}<br/>max_rss: ${p.data[1]} MB`
    },
    legend: { top: 0 },
    grid: { left: 60, right: 20, top: 36, bottom: 40 },
    xAxis: { type: 'time', name: '执行时间' },
    yAxis: { type: 'value', name: 'max_rss (MB)' },
    series: series.map((s, i) =>
      i === 0
        ? { ...s, markLine: { symbol: 'none', lineStyle: { type: 'dashed', color: '#909399' }, label: { formatter: '版本切换' }, data: markLineData } }
        : s
    )
  }, true)
}

async function loadDetail() {
  if (pipelineId.value == null) return
  detailLoading.value = true
  try {
    const res = await getBenchmarkList({
      pipelineId: pipelineId.value,
      version: version.value || undefined,
      page: detailPage.value,
      pageSize: 20
    })
    detail.value = res.records || []
    detailTotal.value = res.total || 0
  } finally {
    detailLoading.value = false
  }
}

async function runBackfill() {
  if (pipelineId.value == null) return
  backfilling.value = true
  backfillMsg.value = ''
  try {
    const res = await backfillBenchmarks(pipelineId.value)
    backfillMsg.value = `回填完成(#${pipelineId.value}):终态执行 ${res.executions} 个,版本推断 ${res.inferred} / unknown ${res.unknown},新入库 ${res.rowsInserted} 行`
    ElMessage.success('回填完成')
    await loadAll()
  } catch (e: any) {
    backfillMsg.value = '回填失败: ' + (e?.message || e)
  } finally {
    backfilling.value = false
  }
}

onMounted(() => {
  loadPipelines()
})

onBeforeUnmount(() => {
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.performance-page {
  padding: 4px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.backfill-msg {
  margin-top: 10px;
  padding: 8px 12px;
  background: #f0f9eb;
  border-radius: 4px;
  color: #67c23a;
  font-size: 13px;
}
.trend-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 20px;
}
.trend-title {
  font-weight: 600;
  font-size: 14px;
}
.trend-chart {
  width: 100%;
  height: 320px;
  margin-top: 8px;
}
.empty-hint {
  padding: 18px 12px;
  color: #909399;
  font-size: 13px;
  line-height: 1.8;
}

/* 资源完整声明展开行 */
.res-design {
  padding: 8px 16px;
  background: #fafcff;
}
.res-design-title {
  font-size: 12.5px;
  color: #606266;
  margin-bottom: 6px;
}
.res-design-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
}
.res-item {
  display: inline-flex;
  align-items: center;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fff;
  font-size: 12px;
}
.res-k {
  padding: 2px 8px;
  color: #909399;
  background: #f5f7fa;
  border-right: 1px solid #e4e7ed;
}
.res-v {
  padding: 2px 10px;
  color: #303133;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.res-design-note {
  margin-top: 6px;
  font-size: 11.5px;
  color: #909399;
  line-height: 1.7;
}
</style>

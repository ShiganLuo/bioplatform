<template>
  <div class="dashboard-container">
    <!-- Stat Cards -->
    <el-row :gutter="20" class="stat-cards">
      <el-col :span="6" v-for="card in statCards" :key="card.title">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <span class="stat-title">{{ card.title }}</span>
              <span class="stat-value">{{ card.value }}</span>
            </div>
            <div class="stat-icon-wrapper" :style="{ background: card.bg }">
              <el-icon class="stat-icon" :style="{ color: card.color }">
                <component :is="card.icon" />
              </el-icon>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="content-row">
      <!-- Recent Executions -->
      <el-col :span="16">
        <el-card class="table-card">
          <template #header>
            <div class="card-header">
              <span>最近执行记录</span>
            </div>
          </template>

          <el-table :data="dashboardData.recentExecutions" style="width: 100%">
            <el-table-column prop="pipelineName" label="流程名称" />
            <el-table-column prop="projectName" label="项目名称" />
            <el-table-column prop="status" label="状态">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" effect="light" round>
                  {{ getStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="startTime" label="开始时间" />
            <el-table-column prop="executor" label="执行人" />
          </el-table>
        </el-card>
      </el-col>

      <!-- Node Status（1 应用节点 + N 计算节点，逐节点资源水位） -->
      <el-col :span="8">
        <el-card class="info-card">
          <template #header>
            <div class="card-header">
              <span>节点状态</span>
            </div>
          </template>

          <div class="node-summary">
            <span>计算节点 {{ computeOnlineCount }}/{{ computeTotalCount }} 在线</span>
            <span :class="['disk-summary', diskSummaryClass]">磁盘最满 {{ maxDiskText }}</span>
          </div>

          <div class="node-list">
            <div v-for="node in dashboardData.nodes" :key="node.nodeId" class="node-card">
              <div class="node-head">
                <el-tag size="small" :type="node.role === 'APP' ? 'primary' : 'warning'" effect="plain">
                  {{ node.role === 'APP' ? '应用' : '计算' }}
                </el-tag>
                <span class="node-host">{{ node.hostname }}</span>
                <el-tag size="small" :type="node.online ? 'success' : 'info'" effect="light" round>
                  {{ node.online ? '在线' : '离线' }}
                </el-tag>
              </div>

              <div class="info-item">
                <span class="info-label">CPU 使用率</span>
                <el-progress
                  v-if="node.cpuUsage != null"
                  :percentage="node.cpuUsage"
                  :color="getProgressColor(node.cpuUsage)"
                  :stroke-width="8"
                />
                <span v-else class="info-na">N/A</span>
              </div>
              <div class="info-item">
                <span class="info-label">内存使用率</span>
                <el-progress
                  v-if="node.memoryUsage != null"
                  :percentage="node.memoryUsage"
                  :color="getProgressColor(node.memoryUsage)"
                  :stroke-width="8"
                />
                <span v-else class="info-na">N/A</span>
              </div>
              <div class="info-item">
                <span class="info-label">磁盘使用率</span>
                <el-progress
                  v-if="node.diskUsage != null"
                  :percentage="node.diskUsage"
                  :color="getProgressColor(node.diskUsage)"
                  :stroke-width="8"
                />
                <span v-else class="info-na">N/A</span>
              </div>

              <div class="node-meta">
                <span v-if="node.role === 'COMPUTE'">运行任务 {{ node.runningTasks ?? 'N/A' }}</span>
                <span v-if="node.role === 'APP' && node.version">版本 {{ node.version }}</span>
                <span v-if="node.uptime">运行 {{ node.uptime }}</span>
              </div>
              <div v-if="node.metricNote" class="metric-note">{{ node.metricNote }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { User, Folder, Connection, Monitor } from '@element-plus/icons-vue'
import { getDashboard, type NodeMetrics } from '@/api/systemApi'

const dashboardData = ref<{
  totalUsers: number
  totalProjects: number
  totalPipelines: number
  totalExecutions: number
  recentExecutions: any[]
  nodes: NodeMetrics[]
}>({
  totalUsers: 0,
  totalProjects: 0,
  totalPipelines: 0,
  totalExecutions: 0,
  recentExecutions: [],
  nodes: []
})

const statCards = computed(() => [
  { title: '用户总数', value: dashboardData.value.totalUsers, icon: User, color: '#409eff', bg: 'linear-gradient(135deg, #ecf5ff, #d9ecff)' },
  { title: '项目总数', value: dashboardData.value.totalProjects, icon: Folder, color: '#67c23a', bg: 'linear-gradient(135deg, #f0f9eb, #e1f3d8)' },
  { title: '流程总数', value: dashboardData.value.totalPipelines, icon: Connection, color: '#e6a23c', bg: 'linear-gradient(135deg, #fdf6ec, #faecd8)' },
  { title: '执行总数', value: dashboardData.value.totalExecutions, icon: Monitor, color: '#f56c6c', bg: 'linear-gradient(135deg, #fef0f0, #fde2e2)' },
])

const computeNodes = computed(() => dashboardData.value.nodes.filter(n => n.role === 'COMPUTE'))
const computeTotalCount = computed(() => computeNodes.value.length)
const computeOnlineCount = computed(() => computeNodes.value.filter(n => n.online).length)

const maxDisk = computed(() => {
  const values = dashboardData.value.nodes
    .map(n => n.diskUsage)
    .filter((v): v is number => v != null)
  return values.length ? Math.max(...values) : null
})
const maxDiskText = computed(() => (maxDisk.value != null ? `${maxDisk.value}%` : 'N/A'))
const diskSummaryClass = computed(() => {
  if (maxDisk.value == null) return ''
  if (maxDisk.value < 60) return 'disk-ok'
  if (maxDisk.value < 80) return 'disk-warn'
  return 'disk-danger'
})

const getStatusType = (status: string): 'success' | 'warning' | 'info' | 'danger' => {
  const map: Record<string, 'success' | 'warning' | 'info' | 'danger'> = {
    SUCCESS: 'success',
    RUNNING: 'warning',
    FAILED: 'danger',
    PENDING: 'info',
    CANCELLED: 'info'
  }
  return map[status] || 'info'
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    SUCCESS: '成功',
    RUNNING: '运行中',
    FAILED: '失败',
    PENDING: '等待中',
    CANCELLED: '已取消'
  }
  return map[status] || status
}

const getProgressColor = (percentage: number) => {
  if (percentage < 60) return '#67c23a'
  if (percentage < 80) return '#e6a23c'
  return '#f56c6c'
}

onMounted(async () => {
  try {
    const res = await getDashboard()
    const data = res as any
    dashboardData.value = {
      totalUsers: data.userCount || 0,
      totalProjects: data.projectCount || 0,
      totalPipelines: data.pipelineCount || 0,
      totalExecutions: data.executionCount || 0,
      recentExecutions: data.recentExecutions || [],
      nodes: data.nodes || []
    }
  } catch (error) {
    console.error('Failed to load dashboard:', error)
  }
})
</script>

<style scoped>
.dashboard-container {
  padding: 0;
}

/* ===== Stat Cards ===== */
.stat-card {
  border-radius: 12px;
  border: none;
  transition: transform 0.2s, box-shadow 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.stat-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-title {
  font-size: 14px;
  color: #909399;
  font-weight: 500;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  margin-top: 8px;
}

.stat-icon-wrapper {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-icon {
  font-size: 28px;
}

/* ===== Content Row ===== */
.content-row {
  margin-top: 20px;
}

.table-card,
.info-card {
  border-radius: 12px;
  border: none;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.card-header {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

/* ===== Node Status ===== */
.node-summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #606266;
  font-weight: 500;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 12px;
}

.disk-summary.disk-ok {
  color: #67c23a;
}

.disk-summary.disk-warn {
  color: #e6a23c;
}

.disk-summary.disk-danger {
  color: #f56c6c;
  font-weight: 700;
}

.node-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.node-card {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.node-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-host {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-label {
  font-size: 12px;
  color: #909399;
  font-weight: 500;
}

.info-na {
  font-size: 13px;
  color: #c0c4cc;
}

.node-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: #606266;
}

.metric-note {
  font-size: 11px;
  color: #c0c4cc;
}
</style>

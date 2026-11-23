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

    <!-- 两卡等高对齐：左执行记录、右节点状态（列表内部滚动） -->
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
            <NodeCard v-for="node in dashboardData.nodes" :key="node.nodeId" :node="node" />
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
import NodeCard from '@/components/NodeCard.vue'

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

/* ===== Content Row（两卡等高对齐） ===== */
.content-row {
  margin-top: 20px;
}

.content-row .el-card {
  height: 600px;
  border-radius: 12px;
  border: none;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.content-row .el-card :deep(.el-card__body) {
  height: calc(100% - 56px);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.card-header {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

/* 左卡：表格区域自适应，超出滚动 */
.table-card :deep(.el-table) {
  flex: 1;
  overflow: auto;
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
  flex-shrink: 0;
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

/* 右卡：节点卡片列表固定区域，内部滑动 */
.node-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-right: 4px;
}

.node-list::-webkit-scrollbar {
  width: 6px;
}

.node-list::-webkit-scrollbar-thumb {
  background: #dcdfe6;
  border-radius: 3px;
}

.node-list::-webkit-scrollbar-thumb:hover {
  background: #c0c4cc;
}

.node-list::-webkit-scrollbar-track {
  background: transparent;
}
</style>

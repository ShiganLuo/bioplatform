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

      <!-- System Info -->
      <el-col :span="8">
        <el-card class="info-card">
          <template #header>
            <div class="card-header">
              <span>系统信息</span>
            </div>
          </template>

          <div class="system-info">
            <div class="info-item">
              <span class="info-label">系统版本</span>
              <span class="info-value">{{ dashboardData.systemInfo?.version || 'v1.0.0' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">运行时间</span>
              <span class="info-value">{{ dashboardData.systemInfo?.uptime || '-' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">CPU 使用率</span>
              <el-progress
                :percentage="dashboardData.systemInfo?.cpuUsage || 0"
                :color="getProgressColor(dashboardData.systemInfo?.cpuUsage || 0)"
                :stroke-width="8"
              />
            </div>
            <div class="info-item">
              <span class="info-label">内存使用率</span>
              <el-progress
                :percentage="dashboardData.systemInfo?.memoryUsage || 0"
                :color="getProgressColor(dashboardData.systemInfo?.memoryUsage || 0)"
                :stroke-width="8"
              />
            </div>
            <div class="info-item">
              <span class="info-label">磁盘使用率</span>
              <el-progress
                :percentage="dashboardData.systemInfo?.diskUsage || 0"
                :color="getProgressColor(dashboardData.systemInfo?.diskUsage || 0)"
                :stroke-width="8"
              />
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
import { getDashboard } from '@/api/systemApi'

const dashboardData = ref({
  totalUsers: 0,
  totalProjects: 0,
  totalPipelines: 0,
  totalExecutions: 0,
  recentExecutions: [],
  systemInfo: {
    version: 'v1.0.0',
    uptime: '-',
    cpuUsage: 0,
    memoryUsage: 0,
    diskUsage: 0
  }
})

const statCards = computed(() => [
  { title: '用户总数', value: dashboardData.value.totalUsers, icon: User, color: '#409eff', bg: 'linear-gradient(135deg, #ecf5ff, #d9ecff)' },
  { title: '项目总数', value: dashboardData.value.totalProjects, icon: Folder, color: '#67c23a', bg: 'linear-gradient(135deg, #f0f9eb, #e1f3d8)' },
  { title: '流程总数', value: dashboardData.value.totalPipelines, icon: Connection, color: '#e6a23c', bg: 'linear-gradient(135deg, #fdf6ec, #faecd8)' },
  { title: '执行总数', value: dashboardData.value.totalExecutions, icon: Monitor, color: '#f56c6c', bg: 'linear-gradient(135deg, #fef0f0, #fde2e2)' },
])

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
      systemInfo: {
        version: 'v1.0.0',
        uptime: '-',
        cpuUsage: 0,
        memoryUsage: 0,
        diskUsage: 0
      }
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

/* ===== System Info ===== */
.system-info {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-label {
  font-size: 13px;
  color: #909399;
  font-weight: 500;
}

.info-value {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}
</style>

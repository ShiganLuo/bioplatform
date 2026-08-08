<template>
  <div class="dashboard-container">
    <!-- Stat Cards -->
    <el-row :gutter="20" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <span class="stat-title">用户总数</span>
              <span class="stat-value">{{ dashboardData.totalUsers }}</span>
            </div>
            <el-icon class="stat-icon" :style="{ color: '#409eff' }">
              <User />
            </el-icon>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <span class="stat-title">项目总数</span>
              <span class="stat-value">{{ dashboardData.totalProjects }}</span>
            </div>
            <el-icon class="stat-icon" :style="{ color: '#67c23a' }">
              <Folder />
            </el-icon>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <span class="stat-title">流程总数</span>
              <span class="stat-value">{{ dashboardData.totalPipelines }}</span>
            </div>
            <el-icon class="stat-icon" :style="{ color: '#e6a23c' }">
              <Connection />
            </el-icon>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <span class="stat-title">执行总数</span>
              <span class="stat-value">{{ dashboardData.totalExecutions }}</span>
            </div>
            <el-icon class="stat-icon" :style="{ color: '#f56c6c' }">
              <Monitor />
            </el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- Recent Executions -->
      <el-col :span="16">
        <el-card>
          <template #header>
            <span>最近执行记录</span>
          </template>

          <el-table :data="dashboardData.recentExecutions" style="width: 100%">
            <el-table-column prop="pipelineName" label="流程名称" />
            <el-table-column prop="projectName" label="项目名称" />
            <el-table-column prop="status" label="状态">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)">
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
        <el-card>
          <template #header>
            <span>系统信息</span>
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
              />
            </div>
            <div class="info-item">
              <span class="info-label">内存使用率</span>
              <el-progress
                :percentage="dashboardData.systemInfo?.memoryUsage || 0"
                :color="getProgressColor(dashboardData.systemInfo?.memoryUsage || 0)"
              />
            </div>
            <div class="info-item">
              <span class="info-label">磁盘使用率</span>
              <el-progress
                :percentage="dashboardData.systemInfo?.diskUsage || 0"
                :color="getProgressColor(dashboardData.systemInfo?.diskUsage || 0)"
              />
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
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

const getStatusType = (status: string) => {
  const map: Record<string, string> = {
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
    // API returns {code, message, result: {userCount, projectCount, ...}}
    const data = res.result || res
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

.stat-card {
  height: 100px;
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
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
  margin-top: 8px;
}

.stat-icon {
  font-size: 48px;
  opacity: 0.8;
}

.system-info {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-label {
  font-size: 14px;
  color: #909399;
}

.info-value {
  font-size: 14px;
  color: #303133;
}
</style>

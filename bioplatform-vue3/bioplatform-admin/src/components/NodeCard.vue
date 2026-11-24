<template>
  <div class="node-card">
    <div class="node-head">
      <el-tag size="small" :type="node.role === 'APP' ? 'primary' : 'warning'" effect="light">
        {{ node.role === 'APP' ? '应用节点' : '计算节点' }}
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
</template>

<script setup lang="ts">
import type { NodeMetrics } from '@/api/systemApi'

defineProps<{ node: NodeMetrics }>()

const getProgressColor = (percentage: number) => {
  if (percentage < 60) return '#67c23a'
  if (percentage < 80) return '#e6a23c'
  return '#f56c6c'
}
</script>

<style scoped>
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

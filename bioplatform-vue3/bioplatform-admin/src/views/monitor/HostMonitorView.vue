<template>
  <div class="host-monitor">
    <el-card class="monitor-card">
      <template #header>
        <div class="card-header">
          <span>主机监控</span>
          <el-button size="small" type="primary" @click="load">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </template>

      <div class="node-summary">
        <span>计算节点 {{ computeOnlineCount }}/{{ computeTotalCount }} 在线</span>
        <span :class="['disk-summary', diskSummaryClass]">磁盘最满 {{ maxDiskText }}</span>
      </div>

      <el-row :gutter="16">
        <el-col :span="8" v-for="node in nodes" :key="node.nodeId" class="node-col">
          <NodeCard :node="node" />
        </el-col>
      </el-row>

      <el-empty v-if="nodes.length === 0" description="暂无节点数据" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import http from '@/utils/http/axios'
import type { NodeMetrics } from '@/api/systemApi'
import NodeCard from '@/components/NodeCard.vue'

const nodes = ref<NodeMetrics[]>([])

const computeNodes = computed(() => nodes.value.filter(n => n.role === 'COMPUTE'))
const computeTotalCount = computed(() => computeNodes.value.length)
const computeOnlineCount = computed(() => computeNodes.value.filter(n => n.online).length)

const maxDisk = computed(() => {
  const values = nodes.value
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

async function load() {
  try {
    const res = await http.get('/api/admin/workers/metrics') as any
    nodes.value = Array.isArray(res) ? res : []
  } catch {
    nodes.value = []
  }
}

onMounted(load)
</script>

<style scoped>
.monitor-card {
  border-radius: 12px;
  border: none;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.node-summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #606266;
  font-weight: 500;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 16px;
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

.node-col {
  margin-bottom: 16px;
}
</style>

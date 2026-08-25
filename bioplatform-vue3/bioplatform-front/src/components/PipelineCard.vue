<template>
  <div class="pipeline-card" @click="$emit('click')">
    <div class="card-icon">
      <el-icon :size="24" color="#409eff"><Operation /></el-icon>
    </div>
    <div class="card-body">
      <div class="card-header">
        <h3 class="pipeline-name">{{ pipeline.name }}</h3>
        <el-tag size="small" effect="plain" type="info">
          {{ pipeline.type }}
        </el-tag>
      </div>
      <p class="pipeline-desc">{{ pipeline.description || '暂无描述' }}</p>
      <div class="card-meta">
        <el-tag size="small" effect="plain">
          {{ pipeline.category }}
        </el-tag>
      </div>
    </div>
    <div class="card-arrow">
      <el-icon><ArrowRight /></el-icon>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Operation, ArrowRight } from '@element-plus/icons-vue'
import type { Pipeline } from '@/api/pipelineApi'

defineProps<{
  pipeline: Pipeline
}>()

defineEmits<{
  (e: 'click'): void
}>()
</script>

<style scoped>
.pipeline-card {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #ebeef5;
  cursor: pointer;
  transition: all 0.3s ease;
}

.pipeline-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  border-color: #409eff;
}

.card-icon {
  width: 48px;
  height: 48px;
  background: #ecf5ff;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.card-body {
  flex: 1;
  min-width: 0;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.pipeline-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pipeline-desc {
  font-size: 14px;
  color: #909399;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 12px;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-arrow {
  flex-shrink: 0;
  color: #c0c4cc;
  transition: all 0.2s;
}

.pipeline-card:hover .card-arrow {
  color: #409eff;
  transform: translateX(4px);
}
</style>

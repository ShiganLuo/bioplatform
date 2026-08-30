<template>
  <div class="project-card" @click="goToDetail">
    <div class="card-header">
      <el-tag :type="statusTagType" size="small" effect="plain">
        {{ statusLabel }}
      </el-tag>
      <span class="project-date">{{ formatDate(project.createdAt) }}</span>
    </div>
    <h3 class="project-name">{{ project.name }}</h3>
    <p class="project-desc">{{ project.description || '暂无描述' }}</p>
    <div class="card-tags">
      <template v-if="project.organism">
        <el-tag v-for="org in project.organism.split(',').filter(Boolean)" :key="org" size="small" type="info" effect="plain">
          {{ org.trim() }}
        </el-tag>
      </template>
      <el-tag v-if="project.genomeVersion" size="small" type="warning" effect="plain">
        {{ project.genomeVersion }}
      </el-tag>
    </div>
    <div class="card-footer">
      <span class="owner">
        <el-icon><User /></el-icon>
        {{ project.ownerName }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { User } from '@element-plus/icons-vue'
import type { Project } from '@/api/projectApi'

const props = defineProps<{
  project: Project
}>()

const router = useRouter()

const statusLabel = computed(() => {
  const map: Record<number, string> = {
    0: '草稿',
    1: '活跃',
    2: '归档',
  }
  return map[props.project.status] || '未知'
})

const statusTagType = computed(() => {
  const map: Record<number, string> = {
    0: 'info',
    1: 'success',
    2: 'warning',
  }
  return (map[props.project.status] || 'info') as any
})

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  return dateStr.split('T')[0]
}

function goToDetail() {
  router.push(`/projects/${props.project.id}`)
}
</script>

<style scoped>
.project-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #ebeef5;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.project-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  border-color: #409eff;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.project-date {
  font-size: 12px;
  color: #c0c4cc;
}

.project-name {
  font-size: 17px;
  font-weight: 600;
  color: #303133;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.project-desc {
  font-size: 14px;
  color: #909399;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
}

.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 10px;
  border-top: 1px solid #f5f5f5;
}

.owner {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #909399;
}
</style>

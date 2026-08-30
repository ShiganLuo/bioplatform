<template>
  <div class="project-detail" v-loading="loading">
    <template v-if="project">
      <div class="detail-header">
        <el-button text @click="router.back()">
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
      </div>

      <div class="detail-card">
        <div class="detail-title-row">
          <h1 class="detail-title">{{ project.name }}</h1>
          <el-tag :type="statusTagType" effect="plain">{{ statusLabel }}</el-tag>
        </div>

        <p class="detail-desc">{{ project.description || '暂无描述' }}</p>

        <el-divider />

        <div class="detail-meta">
          <div class="meta-item" v-if="project.organism">
            <span class="meta-label">物种</span>
            <el-tag type="info" effect="plain" size="small">{{ project.organism }}</el-tag>
          </div>
          <div class="meta-item" v-if="project.genomeVersion">
            <span class="meta-label">基因组版本</span>
            <el-tag type="warning" effect="plain" size="small">{{ project.genomeVersion }}</el-tag>
          </div>
          <div class="meta-item">
            <span class="meta-label">负责人</span>
            <span class="meta-value">
              <el-icon><User /></el-icon> {{ project.ownerName || '-' }}
            </span>
          </div>
          <div class="meta-item">
            <span class="meta-label">创建时间</span>
            <span class="meta-value">{{ formatDate(project.createdAt) }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">更新时间</span>
            <span class="meta-value">{{ formatDate(project.updatedAt) }}</span>
          </div>
        </div>
      </div>
    </template>

    <el-empty v-if="!loading && !project" description="项目不存在或已删除">
      <el-button type="primary" @click="router.push('/projects')">返回项目列表</el-button>
    </el-empty>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ArrowLeft, User } from '@element-plus/icons-vue'
import { getProjectDetail } from '@/api/projectApi'
import type { Project } from '@/api/projectApi'

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const project = ref<Project | null>(null)

const statusLabel = computed(() => {
  const map: Record<number, string> = { 0: '草稿', 1: '活跃', 2: '归档' }
  return map[project.value?.status ?? -1] || '未知'
})

const statusTagType = computed(() => {
  const map: Record<number, string> = { 0: 'info', 1: 'success', 2: 'warning' }
  return (map[project.value?.status ?? -1] || 'info') as any
})

function formatDate(dateStr: string) {
  if (!dateStr) return '-'
  return dateStr.replace('T', ' ').substring(0, 19)
}

async function fetchDetail() {
  const id = Number(route.params.id)
  if (!id) return
  loading.value = true
  try {
    const res = await getProjectDetail(id)
    project.value = res as any
  } catch {
    project.value = null
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchDetail()
})
</script>

<style scoped>
.project-detail {
  padding: 20px 0;
  max-width: 800px;
  margin: 0 auto;
}

.detail-header {
  margin-bottom: 20px;
}

.detail-card {
  background: #fff;
  border-radius: 12px;
  padding: 32px;
  border: 1px solid #ebeef5;
}

.detail-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.detail-title {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  margin: 0;
}

.detail-desc {
  font-size: 15px;
  color: #606266;
  line-height: 1.8;
  margin: 0;
}

.detail-meta {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.meta-label {
  font-size: 13px;
  color: #909399;
}

.meta-value {
  font-size: 14px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 4px;
}

@media (max-width: 640px) {
  .detail-meta {
    grid-template-columns: 1fr;
  }
}
</style>

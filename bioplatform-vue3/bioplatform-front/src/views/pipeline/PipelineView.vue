<template>
  <div class="pipeline-view">
    <div class="page-header">
      <h1 class="page-title">分析流程</h1>
      <p class="page-desc">浏览和了解可用的生物信息学分析流程</p>
    </div>

    <!-- Category Tabs -->
    <div class="category-tabs">
      <el-tabs v-model="activeCategory" @tab-change="handleCategoryChange">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane
          v-for="cat in categories"
          :key="cat"
          :label="cat"
          :name="cat"
        />
      </el-tabs>
    </div>

    <!-- Pipeline Grid -->
    <div class="pipelines-container" v-loading="loading">
      <div class="pipelines-grid">
        <PipelineCard
          v-for="pipeline in pipelines"
          :key="pipeline.id"
          :pipeline="pipeline"
          @click="openDetail(pipeline)"
        />
      </div>

      <el-empty v-if="!loading && pipelines.length === 0" description="暂无分析流程" />
    </div>

    <!-- Detail Drawer -->
    <el-drawer
      v-model="drawerVisible"
      :title="selectedPipeline?.name"
      size="500px"
      direction="rtl"
    >
      <template v-if="selectedPipeline">
        <div class="drawer-content">
          <div class="drawer-section">
            <h4>基本信息</h4>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="名称">{{ selectedPipeline.name }}</el-descriptions-item>
              <el-descriptions-item label="分类">{{ selectedPipeline.category }}</el-descriptions-item>
              <el-descriptions-item label="类型">{{ selectedPipeline.type }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ selectedPipeline.createdAt }}</el-descriptions-item>
            </el-descriptions>
          </div>
          <div class="drawer-section">
            <h4>描述</h4>
            <p class="pipeline-description">{{ selectedPipeline.description || '暂无描述' }}</p>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listPipelines, getCategories } from '@/api/pipelineApi'
import type { Pipeline } from '@/api/pipelineApi'
import PipelineCard from '@/components/PipelineCard.vue'

const loading = ref(false)
const pipelines = ref<Pipeline[]>([])
const categories = ref<string[]>([])
const activeCategory = ref('all')
const drawerVisible = ref(false)
const selectedPipeline = ref<Pipeline | null>(null)

async function fetchPipelines() {
  loading.value = true
  try {
    const params: any = {
      page: 1,
      size: 50,
    }
    if (activeCategory.value !== 'all') {
      params.category = activeCategory.value
    }
    const res = await listPipelines(params)
    const data = res as any
    pipelines.value = data.records || []
  } catch {
    pipelines.value = []
  } finally {
    loading.value = false
  }
}

async function fetchCategories() {
  try {
    const res = await getCategories()
    const data = res as any
    categories.value = Array.isArray(data) ? data : []
  } catch {
    categories.value = []
  }
}

function handleCategoryChange() {
  fetchPipelines()
}

function openDetail(pipeline: Pipeline) {
  selectedPipeline.value = pipeline
  drawerVisible.value = true
}

onMounted(() => {
  fetchCategories()
  fetchPipelines()
})
</script>

<style scoped>
.pipeline-view {
  padding: 20px 0;
}

.page-header {
  margin-bottom: 24px;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 8px;
}

.page-desc {
  color: #909399;
  font-size: 15px;
}

.category-tabs {
  margin-bottom: 16px;
}

.pipelines-container {
  min-height: 300px;
}

.pipelines-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.drawer-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.drawer-section h4 {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}

.pipeline-description {
  color: #606266;
  line-height: 1.7;
}

@media (max-width: 1024px) {
  .pipelines-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .pipelines-grid {
    grid-template-columns: 1fr;
  }
}
</style

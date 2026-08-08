<template>
  <div class="project-view">
    <div class="page-header">
      <h1 class="page-title">公开项目</h1>
      <p class="page-desc">浏览社区公开的生物信息学研究项目</p>
    </div>

    <!-- Search Bar -->
    <div class="search-bar">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索项目名称、物种、描述..."
        size="large"
        clearable
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
        <template #append>
          <el-button @click="handleSearch">
            <el-icon><Search /></el-icon> 搜索
          </el-button>
        </template>
      </el-input>
    </div>

    <!-- Projects Grid -->
    <div class="projects-container" v-loading="loading">
      <div class="projects-grid">
        <ProjectCard
          v-for="project in projects"
          :key="project.id"
          :project="project"
        />
      </div>

      <el-empty v-if="!loading && projects.length === 0" description="暂无匹配的项目">
        <el-button type="primary" @click="resetSearch">重置搜索</el-button>
      </el-empty>

      <!-- Pagination -->
      <div v-if="total > pageSize" class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next, jumper, ->, total"
          background
          @current-change="fetchProjects"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { listPublicProjects, searchProjects } from '@/api/projectApi'
import type { Project } from '@/api/projectApi'
import ProjectCard from '@/components/ProjectCard.vue'

const loading = ref(false)
const projects = ref<Project[]>([])
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(12)
const total = ref(0)

async function fetchProjects() {
  loading.value = true
  try {
    let res
    if (searchKeyword.value.trim()) {
      res = await searchProjects({
        keyword: searchKeyword.value.trim(),
        pageNum: currentPage.value,
        pageSize: pageSize.value,
      })
    } else {
      res = await listPublicProjects({
        pageNum: currentPage.value,
        pageSize: pageSize.value,
      })
    }
    const data = res as any
    projects.value = data.records || data.data?.records || []
    total.value = data.total || data.data?.total || 0
  } catch {
    projects.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  fetchProjects()
}

function resetSearch() {
  searchKeyword.value = ''
  currentPage.value = 1
  fetchProjects()
}

onMounted(() => {
  fetchProjects()
})
</script>

<style scoped>
.project-view {
  padding: 20px 0;
}

.page-header {
  margin-bottom: 32px;
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

.search-bar {
  margin-bottom: 32px;
  max-width: 640px;
}

.projects-container {
  min-height: 300px;
}

.projects-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.pagination-wrapper {
  margin-top: 40px;
  display: flex;
  justify-content: center;
}

@media (max-width: 1024px) {
  .projects-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .projects-grid {
    grid-template-columns: 1fr;
  }
}
</style>

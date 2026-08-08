<template>
  <div class="home-view">
    <!-- Hero Section -->
    <section class="hero-section">
      <div class="hero-bg"></div>
      <div class="hero-content">
        <h1 class="hero-title">
          <span class="gradient-text">生信云平台</span>
        </h1>
        <p class="hero-subtitle">
          一站式生物信息学分析云平台<br />
          集成项目管理、分析流程、AI 智能助手，让生物信息分析更简单、更高效
        </p>
        <div class="hero-actions">
          <el-button type="primary" size="large" @click="router.push('/projects')">
            <el-icon><FolderOpened /></el-icon>
            浏览项目
          </el-button>
          <el-button size="large" @click="router.push('/agent')">
            <el-icon><ChatDotRound /></el-icon>
            AI 助手
          </el-button>
        </div>
        <div class="hero-stats">
          <div class="stat-item">
            <span class="stat-number">100+</span>
            <span class="stat-label">公开项目</span>
          </div>
          <div class="stat-item">
            <span class="stat-number">50+</span>
            <span class="stat-label">分析流程</span>
          </div>
          <div class="stat-item">
            <span class="stat-number">24/7</span>
            <span class="stat-label">在线服务</span>
          </div>
        </div>
      </div>
    </section>

    <!-- Features Section -->
    <section class="features-section">
      <h2 class="section-title">平台特色</h2>
      <p class="section-desc">全面的生物信息学工具和资源</p>
      <div class="features-grid">
        <div
          v-for="feature in features"
          :key="feature.title"
          class="feature-card"
        >
          <div class="feature-icon" :style="{ background: feature.bgColor }">
            <el-icon :size="32" :color="feature.iconColor">
              <component :is="feature.icon" />
            </el-icon>
          </div>
          <h3 class="feature-title">{{ feature.title }}</h3>
          <p class="feature-desc">{{ feature.description }}</p>
        </div>
      </div>
    </section>

    <!-- Recent Projects Section -->
    <section class="projects-section">
      <div class="section-header">
        <div>
          <h2 class="section-title">近期公开项目</h2>
          <p class="section-desc">浏览社区最新的生物信息学研究项目</p>
        </div>
        <el-button type="primary" link @click="router.push('/projects')">
          查看全部 <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>
      <div class="projects-grid" v-loading="loadingProjects">
        <ProjectCard
          v-for="project in recentProjects"
          :key="project.id"
          :project="project"
        />
        <el-empty v-if="!loadingProjects && recentProjects.length === 0" description="暂无公开项目" />
      </div>
    </section>

    <!-- CTA Section -->
    <section class="cta-section">
      <div class="cta-content">
        <h2>准备好开始了吗？</h2>
        <p>立即注册账号，开启您的生物信息学分析之旅</p>
        <el-button type="primary" size="large" @click="showLogin">
          免费注册
        </el-button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { FolderOpened, ChatDotRound, DataBoard, Document, ArrowRight } from '@element-plus/icons-vue'
import { listPublicProjects } from '@/api/projectApi'
import type { Project } from '@/api/projectApi'
import ProjectCard from '@/components/ProjectCard.vue'

const router = useRouter()
const loadingProjects = ref(false)
const recentProjects = ref<Project[]>([])

const features = [
  {
    title: '数据分析',
    description: '支持多种生物信息学分析任务，从原始数据到可视化结果一站式完成',
    icon: 'DataBoard',
    bgColor: 'rgba(64, 158, 255, 0.1)',
    iconColor: '#409eff',
  },
  {
    title: '分析流程',
    description: '预置多种标准化分析流程，支持自定义参数，一键运行分析任务',
    icon: 'Operation',
    bgColor: 'rgba(103, 194, 58, 0.1)',
    iconColor: '#67c23a',
  },
  {
    title: 'AI 智能助手',
    description: '集成 AI 大模型，智能回答生物信息学问题，辅助分析决策',
    icon: 'ChatDotRound',
    bgColor: 'rgba(230, 162, 60, 0.1)',
    iconColor: '#e6a23c',
  },
  {
    title: '数据管理',
    description: '安全可靠的数据存储与管理，支持多格式数据导入导出和版本控制',
    icon: 'FolderOpened',
    bgColor: 'rgba(245, 108, 108, 0.1)',
    iconColor: '#f56c6c',
  },
]

function showLogin() {
  window.dispatchEvent(new Event('show-login-modal'))
}

onMounted(async () => {
  loadingProjects.value = true
  try {
    const res = await listPublicProjects({ pageNum: 1, pageSize: 6 })
    const data = res as any
    recentProjects.value = data.records || data.data?.records || []
  } catch {
    // silent
  } finally {
    loadingProjects.value = false
  }
})
</script>

<style scoped>
.home-view {
  width: 100%;
}

/* Hero Section */
.hero-section {
  position: relative;
  padding: 80px 0 60px;
  text-align: center;
  overflow: hidden;
}

.hero-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #ecf5ff 0%, #f0f9ff 50%, #f5fff0 100%);
  z-index: -1;
}

.hero-content {
  position: relative;
  z-index: 1;
}

.hero-title {
  font-size: 52px;
  font-weight: 800;
  margin-bottom: 20px;
  letter-spacing: -1px;
}

.gradient-text {
  background: linear-gradient(135deg, #409eff, #67c23a);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero-subtitle {
  font-size: 18px;
  color: #606266;
  line-height: 1.8;
  max-width: 600px;
  margin: 0 auto 36px;
}

.hero-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-bottom: 48px;
}

.hero-stats {
  display: flex;
  justify-content: center;
  gap: 64px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-number {
  font-size: 32px;
  font-weight: 700;
  color: #409eff;
}

.stat-label {
  font-size: 14px;
  color: #909399;
}

/* Features Section */
.features-section {
  padding: 80px 0;
}

.section-title {
  font-size: 32px;
  font-weight: 700;
  text-align: center;
  color: #303133;
  margin-bottom: 8px;
}

.section-desc {
  text-align: center;
  color: #909399;
  margin-bottom: 48px;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

.feature-card {
  background: #fff;
  border-radius: 12px;
  padding: 32px 24px;
  text-align: center;
  border: 1px solid #ebeef5;
  transition: all 0.3s ease;
  cursor: default;
}

.feature-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.08);
  border-color: transparent;
}

.feature-icon {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
}

.feature-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.feature-desc {
  font-size: 14px;
  color: #909399;
  line-height: 1.6;
}

/* Projects Section */
.projects-section {
  padding: 40px 0 80px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 32px;
}

.section-header .section-title,
.section-header .section-desc {
  text-align: left;
  margin-bottom: 0;
}

.section-header .section-title {
  margin-bottom: 4px;
}

.projects-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
  min-height: 200px;
}

/* CTA Section */
.cta-section {
  padding: 80px 0;
  background: linear-gradient(135deg, #409eff, #67c23a);
  text-align: center;
}

.cta-content h2 {
  font-size: 36px;
  font-weight: 700;
  color: #fff;
  margin-bottom: 12px;
}

.cta-content p {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.85);
  margin-bottom: 32px;
}

@media (max-width: 1024px) {
  .features-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .projects-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .hero-title {
    font-size: 32px;
  }
  .hero-subtitle {
    font-size: 16px;
  }
  .hero-stats {
    gap: 32px;
  }
  .features-grid {
    grid-template-columns: 1fr;
  }
  .projects-grid {
    grid-template-columns: 1fr;
  }
  .section-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>

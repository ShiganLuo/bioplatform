<template>
  <div class="about-view">
    <!-- Banner -->
    <section class="about-banner">
      <h1>关于我们</h1>
      <p>致力于打造最易用的生物信息学分析云平台</p>
    </section>

    <!-- Mission Section -->
    <section class="about-section">
      <div class="section-inner">
        <h2>平台愿景</h2>
        <p>
          生信云平台旨在为生物信息学研究人员和生物学家提供一站式的数据分析服务。
          我们将复杂的生物信息学分析流程封装为直观易用的在线工具，让研究者无需编写代码
          即可完成从原始数据到分析结果的全流程操作。
        </p>
        <p>
          通过集成 AI 智能助手，我们进一步降低了生信分析的技术门槛，让每一位科研工作者
          都能轻松驾驭海量基因组数据，加速科学发现。
        </p>
      </div>
    </section>

    <!-- Features Section -->
    <section class="about-section alt-bg">
      <div class="section-inner">
        <h2>核心能力</h2>
        <div class="capabilities-grid">
          <div class="capability-item" v-for="cap in capabilities" :key="cap.title">
            <el-icon :size="28" :color="cap.color"><component :is="cap.icon" /></el-icon>
            <h3>{{ cap.title }}</h3>
            <p>{{ cap.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Tech Stack -->
    <section class="about-section">
      <div class="section-inner">
        <h2>技术架构</h2>
        <div class="tech-grid">
          <div class="tech-item" v-for="tech in techStack" :key="tech.name">
            <div class="tech-badge" :style="{ background: tech.color }">
              {{ tech.name }}
            </div>
            <p>{{ tech.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Contact Section -->
    <section class="about-section alt-bg">
      <div class="section-inner">
        <h2>联系我们</h2>
        <div class="contact-grid">
          <div class="contact-item">
            <el-icon :size="24" color="#409eff"><Message /></el-icon>
            <h4>邮箱</h4>
            <p>{{ siteConfig.contactEmail || 'support@bioplatform.com' }}</p>
          </div>
          <div class="contact-item">
            <el-icon :size="24" color="#67c23a"><Link /></el-icon>
            <h4>GitHub</h4>
            <p>
              <a v-if="siteConfig.githubUrl" :href="siteConfig.githubUrl" target="_blank" style="color: inherit;">
                {{ siteConfig.githubUrl.replace('https://', '') }}
              </a>
              <span v-else>github.com/bioplatform</span>
            </p>
          </div>
          <div class="contact-item" style="cursor: pointer;" @click="openFeedback">
            <el-icon :size="24" color="#e6a23c"><ChatDotRound /></el-icon>
            <h4>在线反馈</h4>
            <p>点击打开客服对话</p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Message, Link, ChatDotRound } from '@element-plus/icons-vue'
import { getSiteConfig } from '@/api/siteApi'

const siteConfig = ref({
  siteName: '',
  siteDescription: '',
  contactEmail: '',
  githubUrl: ''
})

function openFeedback() {
  window.dispatchEvent(new CustomEvent('open-feedback-chat'))
}

onMounted(async () => {
  try {
    const res = await getSiteConfig() as any
    if (res) siteConfig.value = res
  } catch {}
})

const capabilities = [
  {
    title: '项目管理',
    desc: '支持多物种、多基因组版本的项目组织管理，灵活的权限控制和数据共享',
    icon: 'FolderOpened',
    color: '#409eff',
  },
  {
    title: '分析流程',
    desc: '内置 RNA-seq、ChIP-seq、WGS 等多种标准化分析流程，支持自定义参数配置',
    icon: 'Operation',
    color: '#67c23a',
  },
  {
    title: 'AI 助手',
    desc: '基于大语言模型的智能问答助手，支持生信知识咨询和分析建议',
    icon: 'ChatDotRound',
    color: '#e6a23c',
  },
  {
    title: '数据安全',
    desc: '企业级数据安全保障，支持私有化部署，数据完全自主可控',
    icon: 'Lock',
    color: '#f56c6c',
  },
]

const techStack = [
  { name: 'Vue 3', desc: '前端框架', color: '#42b883' },
  { name: 'Element Plus', desc: 'UI 组件库', color: '#409eff' },
  { name: 'Spring Boot', desc: '后端框架', color: '#6db33f' },
  { name: 'Docker', desc: '容器化部署', color: '#2496ed' },
  { name: 'PostgreSQL', desc: '关系型数据库', color: '#336791' },
  { name: 'Redis', desc: '缓存和消息队列', color: '#dc382d' },
]
</script>

<style scoped>
.about-view {
  padding: 20px 0;
}

.about-banner {
  text-align: center;
  padding: 60px 0;
  background: linear-gradient(135deg, #ecf5ff, #f0f9ff);
  border-radius: 12px;
  margin-bottom: 48px;
}

.about-banner h1 {
  font-size: 36px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 12px;
}

.about-banner p {
  font-size: 16px;
  color: #606266;
}

.about-section {
  padding: 48px 0;
}

.about-section.alt-bg {
  background: #f5f7fa;
  margin: 0 -24px;
  padding-left: 24px;
  padding-right: 24px;
}

.section-inner {
  max-width: 960px;
  margin: 0 auto;
}

.section-inner h2 {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 24px;
  text-align: center;
}

.section-inner > p {
  font-size: 15px;
  color: #606266;
  line-height: 1.8;
  margin-bottom: 16px;
}

.capabilities-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
  margin-top: 32px;
}

.capability-item {
  background: #fff;
  border-radius: 12px;
  padding: 28px 24px;
  border: 1px solid #ebeef5;
  transition: all 0.3s;
}

.capability-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
}

.capability-item h3 {
  font-size: 16px;
  color: #303133;
  margin: 12px 0 8px;
}

.capability-item p {
  font-size: 14px;
  color: #909399;
  line-height: 1.6;
}

.tech-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-top: 32px;
}

.tech-item {
  text-align: center;
}

.tech-badge {
  display: inline-block;
  padding: 8px 20px;
  border-radius: 20px;
  color: #fff;
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 8px;
}

.tech-item p {
  font-size: 13px;
  color: #909399;
}

.contact-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
  margin-top: 32px;
}

.contact-item {
  text-align: center;
  background: #fff;
  border-radius: 12px;
  padding: 28px 24px;
  border: 1px solid #ebeef5;
}

.contact-item h4 {
  font-size: 16px;
  color: #303133;
  margin: 12px 0 6px;
}

.contact-item p {
  font-size: 14px;
  color: #909399;
}

@media (max-width: 768px) {
  .capabilities-grid,
  .tech-grid,
  .contact-grid {
    grid-template-columns: 1fr;
  }
}
</style>

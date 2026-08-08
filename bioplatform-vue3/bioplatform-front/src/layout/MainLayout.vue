<template>
  <div class="main-layout">
    <!-- Navigation Header -->
    <header class="layout-header">
      <div class="header-container">
        <div class="logo-section" @click="router.push('/')">
          <el-icon :size="28" color="#409eff"><DataBoard /></el-icon>
          <span class="logo-text">生信云平台</span>
        </div>

        <!-- Desktop Menu -->
        <nav class="desktop-menu">
          <router-link
            v-for="item in menuItems"
            :key="item.path"
            :to="item.path"
            class="menu-item"
            :class="{ active: currentPath === item.path }"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </router-link>
        </nav>

        <div class="header-actions">
          <template v-if="userStore.isLoggedIn">
            <el-dropdown trigger="click" @command="handleUserCommand">
              <div class="user-avatar">
                <el-avatar :size="32" :src="userStore.userInfo?.avatar">
                  {{ userStore.nickname.charAt(0).toUpperCase() }}
                </el-avatar>
                <span class="user-name">{{ userStore.nickname }}</span>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="userStore.userInfo?.role === 'admin'" command="admin">
                    <el-icon><Setting /></el-icon>管理后台
                  </el-dropdown-item>
                  <el-dropdown-item command="logout" divided>
                    <el-icon><SwitchButton /></el-icon>退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <el-button type="primary" @click="showLoginModal = true">登录</el-button>
          </template>
        </div>

        <!-- Mobile Menu Button -->
        <div class="mobile-menu-btn" @click="mobileMenuVisible = !mobileMenuVisible">
          <el-icon :size="24"><Menu /></el-icon>
        </div>
      </div>

      <!-- Mobile Menu -->
      <transition name="slide-down">
        <div v-if="mobileMenuVisible" class="mobile-menu">
          <router-link
            v-for="item in menuItems"
            :key="item.path"
            :to="item.path"
            class="mobile-menu-item"
            :class="{ active: currentPath === item.path }"
            @click="mobileMenuVisible = false"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </router-link>
          <div v-if="!userStore.isLoggedIn" class="mobile-menu-item" @click="showLoginModal = true; mobileMenuVisible = false">
            <el-icon><User /></el-icon>
            <span>登录/注册</span>
          </div>
        </div>
      </transition>
    </header>

    <!-- Main Content -->
    <main class="layout-main">
      <router-view />
    </main>

    <!-- Footer -->
    <footer class="layout-footer">
      <div class="footer-container">
        <div class="footer-content">
          <div class="footer-brand">
            <div class="footer-logo">
              <el-icon :size="24" color="#409eff"><DataBoard /></el-icon>
              <span>生信云平台</span>
            </div>
            <p class="footer-desc">一站式生物信息学分析云平台</p>
          </div>
          <div class="footer-links">
            <div class="footer-col">
              <h4>快速导航</h4>
              <router-link to="/">首页</router-link>
              <router-link to="/projects">公开项目</router-link>
              <router-link to="/pipelines">分析流程</router-link>
              <router-link to="/agent">AI 助手</router-link>
            </div>
            <div class="footer-col">
              <h4>帮助支持</h4>
              <a href="#">使用文档</a>
              <a href="#">常见问题</a>
              <a href="#">意见反馈</a>
            </div>
          </div>
        </div>
        <div class="footer-bottom">
          <p>© {{ currentYear }} 生信云平台 Bioinformatics Cloud Platform. All rights reserved.</p>
        </div>
      </div>
    </footer>

    <!-- Login Modal -->
    <LoginModal v-model:visible="showLoginModal" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { DataBoard, Setting, SwitchButton, Menu, User, TrendCharts, Cpu, ChatDotRound, Document } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import LoginModal from '@/components/LoginModal.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const showLoginModal = ref(false)
const mobileMenuVisible = ref(false)

const currentPath = computed(() => route.path)
const currentYear = new Date().getFullYear()

const menuItems = [
  { path: '/', label: '首页', icon: 'HomeFilled' },
  { path: '/projects', label: '公开项目', icon: 'FolderOpened' },
  { path: '/pipelines', label: '分析流程', icon: 'Operation' },
  { path: '/agent', label: 'AI 助手', icon: 'ChatDotRound' },
  { path: '/about', label: '关于我们', icon: 'InfoFilled' },
]

function handleUserCommand(command: string) {
  if (command === 'admin') {
    window.location.href = '/admin'
  } else if (command === 'logout') {
    userStore.logout()
    router.push('/')
  }
}
</script>

<style scoped>
.main-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.layout-header {
  position: sticky;
  top: 0;
  z-index: 1000;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

.header-container {
  max-width: 1400px;
  margin: 0 auto;
  height: 64px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.logo-section {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.logo-section:hover {
  opacity: 0.8;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  color: #303133;
  background: linear-gradient(135deg, #409eff, #67c23a);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.desktop-menu {
  display: flex;
  gap: 4px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 8px;
  color: #606266;
  text-decoration: none;
  font-size: 15px;
  transition: all 0.2s;
}

.menu-item:hover {
  color: #409eff;
  background: #ecf5ff;
}

.menu-item.active {
  color: #409eff;
  background: #ecf5ff;
  font-weight: 500;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-avatar {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.user-name {
  font-size: 14px;
  color: #303133;
}

.mobile-menu-btn {
  display: none;
  cursor: pointer;
  padding: 8px;
}

.mobile-menu {
  display: none;
  flex-direction: column;
  padding: 8px 24px 16px;
  border-top: 1px solid #ebeef5;
}

.mobile-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
  color: #606266;
  text-decoration: none;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
}

.mobile-menu-item.active {
  color: #409eff;
}

.layout-main {
  flex: 1;
  width: 100%;
  max-width: 1400px;
  margin: 0 auto;
  padding: 24px;
}

.layout-footer {
  background: #1d1e2c;
  color: #a0a1b2;
  margin-top: auto;
}

.footer-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 24px;
}

.footer-content {
  display: flex;
  justify-content: space-between;
  padding: 48px 0 32px;
}

.footer-brand {
  max-width: 320px;
}

.footer-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  color: #fff;
  margin-bottom: 12px;
}

.footer-desc {
  font-size: 14px;
  line-height: 1.6;
}

.footer-links {
  display: flex;
  gap: 64px;
}

.footer-col {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.footer-col h4 {
  color: #fff;
  font-size: 15px;
  margin-bottom: 4px;
}

.footer-col a {
  color: #a0a1b2;
  text-decoration: none;
  font-size: 14px;
  transition: color 0.2s;
}

.footer-col a:hover {
  color: #409eff;
}

.footer-bottom {
  border-top: 1px solid #2d2e3e;
  padding: 20px 0;
  text-align: center;
  font-size: 13px;
}

/* Slide down transition */
.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.3s ease;
}
.slide-down-enter-from,
.slide-down-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

@media (max-width: 768px) {
  .desktop-menu {
    display: none;
  }
  .header-actions {
    display: none;
  }
  .mobile-menu-btn {
    display: block;
  }
  .mobile-menu {
    display: flex;
  }
  .footer-content {
    flex-direction: column;
    gap: 32px;
  }
  .footer-links {
    gap: 32px;
  }
}
</style>

<template>
  <el-container class="admin-layout">
    <!-- Sidebar -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="sidebar">
      <div class="logo" @click="router.push('/dashboard')">
        <el-icon :size="28" color="#409eff"><DataBoard /></el-icon>
        <span v-if="!isCollapse" class="logo-text">生信云平台</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :collapse-transition="false"
        router
        class="sidebar-menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>仪表盘</template>
        </el-menu-item>

        <el-menu-item index="/projects">
          <el-icon><Folder /></el-icon>
          <template #title>项目管理</template>
        </el-menu-item>

        <el-menu-item index="/pipelines">
          <el-icon><Connection /></el-icon>
          <template #title>流程管理</template>
        </el-menu-item>

        <el-menu-item index="/executions">
          <el-icon><Monitor /></el-icon>
          <template #title>执行监控</template>
        </el-menu-item>

        <el-menu-item index="/data">
          <el-icon><Document /></el-icon>
          <template #title>数据管理</template>
        </el-menu-item>

        <el-menu-item index="/agent">
          <el-icon><ChatDotRound /></el-icon>
          <template #title>AI 助手</template>
        </el-menu-item>

        <el-sub-menu index="system">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/system/users">
            <el-icon><User /></el-icon>
            <template #title>用户管理</template>
          </el-menu-item>
          <el-menu-item index="/system/config">
            <el-icon><Tools /></el-icon>
            <template #title>系统配置</template>
          </el-menu-item>
          <el-menu-item index="/system/templates">
            <el-icon><Menu /></el-icon>
            <template #title>流程模板</template>
          </el-menu-item>
          <el-menu-item index="/feedback">
            <el-icon><Comment /></el-icon>
            <template #title>用户反馈</template>
          </el-menu-item>
          <el-menu-item index="/workers">
            <el-icon><Cpu /></el-icon>
            <template #title>计算节点</template>
          </el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="monitor">
          <template #title>
            <el-icon><DataLine /></el-icon>
            <span>系统监控</span>
          </template>
          <el-menu-item index="/monitor/logs">
            <el-icon><Tickets /></el-icon>
            <template #title>操作日志</template>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <!-- Main Content -->
    <el-container class="main-wrapper">
      <!-- Header -->
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <el-icon class="refresh-btn" @click="reload()"><Refresh /></el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentRoute.meta.title">
              {{ currentRoute.meta.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" :src="userStore.userInfo?.avatar">
                {{ userStore.userInfo?.nickname?.charAt(0) || 'A' }}
              </el-avatar>
              <span class="username">{{ userStore.userInfo?.nickname || userStore.userInfo?.username || '管理员' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>
                  个人中心
                </el-dropdown-item>
                <el-dropdown-item command="settings">
                  <el-icon><Setting /></el-icon>
                  系统设置
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- Main Content Area -->
      <WorkTab />
      <el-main class="main-content">
        <router-view v-if="isRefresh" v-slot="{ Component }">
          <keep-alive>
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useWorktabStore } from '@/stores/worktab'
import WorkTab from '@/components/WorkTab.vue'
import {
  Odometer, Folder, Connection, Monitor, Document,
  ChatDotRound, Setting, User, Tools, DataLine, Tickets,
  Fold, Expand, ArrowDown, SwitchButton, DataBoard, Refresh
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const worktabStore = useWorktabStore()

const isCollapse = ref(false)
const isRefresh = ref(true)

const activeMenu = computed(() => route.path)
const currentRoute = computed(() => route)

function reload() {
  isRefresh.value = false
  nextTick(() => {
    isRefresh.value = true
  })
}

// Track tabs on route change
watch(
  () => route.path,
  (path) => {
    if (route.meta.requiresAuth !== false && path !== '/login') {
      worktabStore.openTab({
        title: (route.meta.title as string) || '未命名',
        path,
        name: route.name as string
      })
    }
  },
  { immediate: true }
)

const handleCommand = (command: string) => {
  switch (command) {
    case 'profile':
      router.push('/profile')
      break
    case 'settings':
      router.push('/system/config')
      break
    case 'logout':
      userStore.logout()
      break
  }
}
</script>

<style scoped>
.admin-layout {
  height: 100vh;
  background: #f5f7fa;
}

/* ===== Sidebar ===== */
.sidebar {
  background: #fff;
  border-right: 1px solid #e4e7ed;
  transition: width 0.3s;
  overflow: hidden;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.03);
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  cursor: pointer;
  border-bottom: 1px solid #f0f0f0;
  transition: opacity 0.2s;
}

.logo:hover {
  opacity: 0.8;
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  white-space: nowrap;
  background: linear-gradient(135deg, #409eff, #67c23a);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.sidebar-menu {
  border-right: none;
  padding: 8px;
}

.sidebar-menu:not(.el-menu--collapse) {
  width: 212px;
}

/* Menu item styling */
:deep(.el-menu-item) {
  border-radius: 8px;
  margin-bottom: 2px;
  height: 44px;
  line-height: 44px;
  color: #606266;
  transition: all 0.2s;
}

:deep(.el-menu-item:hover) {
  background: #ecf5ff;
  color: #409eff;
}

:deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, #409eff, #66b1ff);
  color: #fff;
  font-weight: 500;
}

:deep(.el-menu-item.is-active .el-icon) {
  color: #fff;
}

:deep(.el-sub-menu__title) {
  border-radius: 8px;
  margin-bottom: 2px;
  height: 44px;
  line-height: 44px;
  color: #606266;
  transition: all 0.2s;
}

:deep(.el-sub-menu__title:hover) {
  background: #ecf5ff;
  color: #409eff;
}

:deep(.el-sub-menu .el-menu-item) {
  padding-left: 52px !important;
  min-width: auto;
}

/* ===== Header ===== */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  padding: 0 24px;
  height: 64px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #606266;
  transition: color 0.2s;
}

.collapse-btn:hover {
  color: #409eff;
}

.refresh-btn {
  font-size: 18px;
  cursor: pointer;
  color: #606266;
  transition: color 0.2s;
}

.refresh-btn:hover {
  color: #409eff;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  gap: 8px;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.2s;
}

.user-info:hover {
  background: #f5f7fa;
}

.username {
  font-size: 14px;
  color: #303133;
}

/* ===== Main Content ===== */
.main-wrapper {
  background: #f5f7fa;
}

.main-content {
  background: #f5f7fa;
  padding: 24px;
  overflow-y: auto;
}
</style>

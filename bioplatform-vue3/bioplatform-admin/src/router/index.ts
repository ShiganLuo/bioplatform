import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/register/RegisterView.vue'),
    meta: { title: '注册', requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('@/layout/AdminLayout.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '仪表盘', icon: 'Odometer' }
      },
      {
        path: 'projects',
        name: 'Projects',
        component: () => import('@/views/project/ProjectView.vue'),
        meta: { title: '项目管理', icon: 'Folder' }
      },
      {
        path: 'projects/:id',
        name: 'ProjectDetail',
        component: () => import('@/views/project/ProjectDetailView.vue'),
        meta: { title: '项目详情', icon: 'Folder' }
      },
      {
        path: 'pipelines',
        name: 'Pipelines',
        component: () => import('@/views/pipeline/PipelineView.vue'),
        meta: { title: '流程管理', icon: 'Connection' }
      },
      {
        path: 'executions',
        name: 'Executions',
        component: () => import('@/views/pipeline/ExecutionView.vue'),
        meta: { title: '执行监控', icon: 'Monitor' }
      },
      {
        path: 'data',
        name: 'Data',
        component: () => import('@/views/data/DataView.vue'),
        meta: { title: '数据管理', icon: 'Document' }
      },
      {
        path: 'agent',
        name: 'Agent',
        component: () => import('@/views/agent/AgentView.vue'),
        meta: { title: 'AI 助手', icon: 'ChatDotRound' }
      },
      {
        path: 'system/users',
        name: 'SystemUsers',
        component: () => import('@/views/system/user/UserView.vue'),
        meta: { title: '用户管理', icon: 'User', parent: 'system' }
      },
      {
        path: 'system/config',
        name: 'SystemConfig',
        component: () => import('@/views/system/config/ConfigView.vue'),
        meta: { title: '系统配置', icon: 'Setting', parent: 'system' }
      },
      {
        path: 'system/templates',
        name: 'SystemTemplates',
        component: () => import('@/views/system/template/TemplateView.vue'),
        meta: { title: '流程模板', icon: 'Menu', parent: 'system' }
      },
      {
        path: 'monitor/logs',
        name: 'MonitorLogs',
        component: () => import('@/views/monitor/LogView.vue'),
        meta: { title: '操作日志', icon: 'Tickets', parent: 'monitor' }
      },
      {
        path: 'feedback',
        name: 'Feedback',
        component: () => import('@/views/feedback/FeedbackView.vue'),
        meta: { title: '用户反馈', icon: 'Comment', parent: 'system' }
      },
      {
        path: 'workers',
        name: 'Workers',
        component: () => import('@/views/worker/WorkerView.vue'),
        meta: { title: '计算节点', icon: 'Cpu', parent: 'system' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Navigation guard
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()

  // Set page title
  document.title = `${to.meta.title || '首页'} - 生物信息学云平台`

  // Check if route requires authentication
  if (to.meta.requiresAuth !== false) {
    if (!userStore.isAuthenticated()) {
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }
  }

  // If logged in and going to login page, redirect to dashboard
  if (to.path === '/login' && userStore.isAuthenticated()) {
    next({ path: '/dashboard' })
    return
  }

  next()
})

export { router }
export default router

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
        meta: { title: '用户管理', icon: 'User', parent: 'system', permissions: ['system:user:list'] }
      },
      {
        path: 'system/roles',
        name: 'SystemRoles',
        component: () => import('@/views/system/role/RoleView.vue'),
        meta: { title: '权限管理', icon: 'Lock', parent: 'system', permissions: ['system:role:list'] }
      },
      {
        path: 'system/config',
        name: 'SystemConfig',
        component: () => import('@/views/system/config/ConfigView.vue'),
        meta: { title: '系统配置', icon: 'Setting', parent: 'system', permissions: ['system:config:list'] }
      },
      {
        path: 'system/templates',
        name: 'SystemTemplates',
        component: () => import('@/views/system/template/TemplateView.vue'),
        meta: { title: '分析模板', icon: 'Menu', parent: 'system', permissions: ['template:list'] }
      },
      {
        path: 'monitor/logs',
        name: 'MonitorLogs',
        component: () => import('@/views/monitor/LogView.vue'),
        meta: { title: '操作日志', icon: 'Tickets', parent: 'monitor', permissions: ['log:operation:list'] }
      },
      {
        path: 'monitor/hosts',
        name: 'MonitorHosts',
        component: () => import('@/views/monitor/HostMonitorView.vue'),
        meta: { title: '主机监控', icon: 'Monitor', parent: 'monitor', permissions: ['worker:list'] }
      },
      {
        path: 'feedback',
        name: 'Feedback',
        component: () => import('@/views/feedback/FeedbackView.vue'),
        meta: { title: '用户反馈', icon: 'Comment', parent: 'system', permissions: ['feedback:list'] }
      },
      {
        path: 'workers',
        name: 'Workers',
        component: () => import('@/views/worker/WorkerView.vue'),
        meta: { title: '计算节点', icon: 'Cpu', parent: 'system', permissions: ['worker:list'] }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/ProfileView.vue'),
        meta: { title: '个人中心', icon: 'User' }
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
router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()

  // Set page title
  document.title = `${to.meta.title || '首页'} - 生物信息学云平台`

  // Check if route requires authentication
  if (to.meta.requiresAuth !== false) {
    if (!userStore.isAuthenticated()) {
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }

    // Page refresh: token exists in localStorage but Pinia userInfo is null.
    // Restore from localStorage first, then fetch fresh data from server.
    if (!userStore.userInfo) {
      userStore.initUserInfo()
      // Fire-and-forget: fetch latest user info from server (roles may change)
      userStore.fetchUserInfo()
    }
  }

  // Role / permission based route guard（角色名或权限标识任一命中即放行）
  const requiredRoles = to.meta.roles as string[] | undefined
  if (requiredRoles && requiredRoles.length > 0) {
    const hasRole = requiredRoles.some(role => userStore.hasRole(role))
    if (!hasRole) {
      next({ path: '/dashboard' })
      return
    }
  }
  const requiredPerms = to.meta.permissions as string[] | undefined
  if (requiredPerms && requiredPerms.length > 0) {
    const hasPerm = requiredPerms.some(perm => userStore.hasPermission(perm))
    if (!hasPerm) {
      next({ path: '/dashboard' })
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

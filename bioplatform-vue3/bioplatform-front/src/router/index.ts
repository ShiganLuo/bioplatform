import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/home',
      redirect: '/',
    },
    {
      path: '/',
      component: () => import('@/layout/MainLayout.vue'),
      children: [
        {
          path: '',
          name: 'Home',
          component: () => import('@/views/home/HomeView.vue'),
          meta: { title: '首页' },
        },
        {
          path: 'projects',
          name: 'Projects',
          component: () => import('@/views/project/ProjectView.vue'),
          meta: { title: '公开项目' },
        },
        {
          path: 'pipelines',
          name: 'Pipelines',
          component: () => import('@/views/pipeline/PipelineView.vue'),
          meta: { title: '分析流程' },
        },
        {
          path: 'agent',
          name: 'Agent',
          component: () => import('@/views/agent/AgentView.vue'),
          meta: { title: 'AI 助手' },
        },
        {
          path: 'about',
          name: 'About',
          component: () => import('@/views/about/AboutView.vue'),
          meta: { title: '关于我们' },
        },
        {
          path: 'docs',
          name: 'Docs',
          component: () => import('@/views/docs/DocsView.vue'),
          meta: { title: '使用文档' },
        },
        {
          path: 'faq',
          name: 'FAQ',
          component: () => import('@/views/docs/FAQView.vue'),
          meta: { title: '常见问题' },
        },
      ],
    },
  ],
  scrollBehavior(_to, _from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    }
    return { top: 0 }
  },
})

// Global title guard
router.afterEach((to) => {
  const title = to.meta.title as string
  document.title = title ? `${title} - 生信云平台` : '生信云平台'
})

export default router

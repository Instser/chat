import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory('/admin/'),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/forbidden',
      name: 'forbidden',
      component: () => import('@/views/ForbiddenView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/AdminLayout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '概览' },
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/UsersView.vue'),
          meta: { title: '用户管理' },
        },
        {
          path: 'packages',
          name: 'packages',
          component: () => import('@/views/PackagesView.vue'),
          meta: { title: '套餐管理' },
        },
        {
          path: 'models',
          name: 'models',
          component: () => import('@/views/ModelsView.vue'),
          meta: { title: '模型管理' },
        },
        {
          path: 'orders',
          name: 'orders',
          component: () => import('@/views/OrdersView.vue'),
          meta: { title: '订单支付' },
        },
        {
          path: 'audits',
          name: 'audits',
          component: () => import('@/views/AuditsView.vue'),
          meta: { title: '审计中心' },
        },
        {
          path: 'monitoring',
          name: 'monitoring',
          component: () => import('@/views/MonitoringView.vue'),
          meta: { title: '运维监控' },
        },
        {
          path: 'ops',
          name: 'ops',
          component: () => import('@/views/OpsView.vue'),
          meta: { title: '运维中心' },
        },
        {
          path: 'logs',
          name: 'logs',
          component: () => import('@/views/LogsView.vue'),
          meta: { title: '日志查看' },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!auth.loaded) {
    await auth.restoreSession()
  }

  if (to.meta.public) {
    if (to.name === 'login' && auth.isAdmin) {
      return { path: '/dashboard' }
    }
    return true
  }

  if (!auth.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (!auth.isAdmin) {
    return { path: '/forbidden' }
  }

  return true
})

export default router

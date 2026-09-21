import { createRouter, createWebHistory } from 'vue-router'
import Layout from '../layout/Layout.vue'
import { isLoggedIn } from '../api/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/email',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '控制台' }
      },
      {
        path: 'email',
        name: 'EmailManage',
        component: () => import('../views/EmailManage.vue'),
        meta: { title: '邮箱管理' }
      },
      {
        path: 'mail-reader',
        name: 'MailReader',
        component: () => import('../views/MailReader.vue'),
        meta: { title: '邮件取件' }
      },
      {
        path: 'apple',
        name: 'AppleManage',
        component: () => import('../views/AppleManage.vue'),
        meta: { title: 'Apple ID 录入' }
      },
      {
        path: 'stats',
        name: 'StatsEntry',
        component: () => import('../views/StatsEntry.vue'),
        meta: { title: '录入统计' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：未登录跳登录页；已登录访问登录页则回主页
router.beforeEach((to) => {
  const authed = isLoggedIn()
  if (!to.meta.public && !authed) {
    return { path: '/login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  }
  if (to.path === '/login' && authed) {
    return { path: '/email' }
  }
  return true
})

export default router

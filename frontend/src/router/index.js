import { createRouter, createWebHistory } from 'vue-router'
import Layout from '../layout/Layout.vue'

const routes = [
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

export default router

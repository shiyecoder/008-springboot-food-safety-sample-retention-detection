import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from '@/utils/request'
import { loadProfile, userStore } from '@/store/user'

const routes = [
  { path: '/login', name: 'login', component: () => import('@/views/Login.vue'), meta: { public: true, title: '登录' } },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '食品安全看板' } },
      { path: 'tasks', name: 'tasks', component: () => import('@/views/TaskToday.vue'), meta: { title: '当日留样任务' } },
      { path: 'samples', name: 'samples', component: () => import('@/views/SampleList.vue'), meta: { title: '留样台账' } },
      { path: 'samples/create', name: 'sample-create', component: () => import('@/views/SampleRegister.vue'), meta: { title: '留样登记', active: '/samples' } },
      { path: 'cabinet', name: 'cabinet', component: () => import('@/views/Cabinet.vue'), meta: { title: '留样柜位图' } },
      { path: 'inspection', name: 'inspection', component: () => import('@/views/Inspection.vue'), meta: { title: '检测登记' } },
      { path: 'incident', name: 'incident', component: () => import('@/views/Incident.vue'), meta: { title: '不合格事件' } },
      { path: 'expiry', name: 'expiry', component: () => import('@/views/Expiry.vue'), meta: { title: '到期处置中心' } },
      { path: 'rule', name: 'rule', component: () => import('@/views/Rule.vue'), meta: { title: '留样与校验规则' } },
      { path: 'base', name: 'base', component: () => import('@/views/BaseData.vue'), meta: { title: '基础数据' } },
      { path: 'system', name: 'system', component: () => import('@/views/System.vue'), meta: { title: '系统管理' } },
      { path: 'profile', name: 'profile', component: () => import('@/views/Profile.vue'), meta: { title: '个人中心' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({ history: createWebHashHistory(), routes })

router.beforeEach(async (to) => {
  document.title = to.meta.title ? `${to.meta.title} · 食品安全留样与检测管理系统` : '食品安全留样与检测管理系统'
  if (to.meta.public) return true
  if (!getToken()) return { path: '/login', query: { redirect: to.fullPath } }
  if (!userStore.loaded) {
    try {
      await loadProfile()
    } catch (e) {
      return { path: '/login' }
    }
  }
  return true
})

export default router

import { createRouter, createWebHistory } from 'vue-router'
import { consoleModules } from './consoleModules'

const adminModule = consoleModules.find((item) => item.name === 'admin')
const normalModules = consoleModules.filter((item) => item.name !== 'admin')

if (!adminModule) {
  throw new Error('Admin module metadata is missing in consoleModules.')
}

const lazyPages = {
  connection: () => import('../components/console/ConnectionPanel.vue'),
  questions: () => import('../components/console/QuestionTab.vue'),
  papers: () => import('../components/console/PaperTab.vue'),
  exams: () => import('../components/console/ExamTab.vue'),
  grading: () => import('../components/console/GradingTab.vue'),
  reports: () => import('../components/console/ReportTab.vue'),
  admin: () => import('../components/console/AdminTab.vue'),
  'admin-users': () => import('../components/console/admin/AdminUsersPage.vue'),
  'admin-roles': () => import('../components/console/admin/AdminRolesPage.vue'),
  'admin-configs': () => import('../components/console/admin/AdminConfigsPage.vue'),
  'admin-audits': () => import('../components/console/admin/AdminAuditsPage.vue'),
  'admin-risks': () => import('../components/console/admin/AdminRiskPage.vue'),
}

const resolveLazyPage = (name) => {
  const loader = lazyPages[name]
  if (!loader) {
    throw new Error(`Missing lazy route loader for "${name}".`)
  }
  return loader
}

const routes = [
  { path: '/', redirect: consoleModules[0].path },
  ...normalModules.map((module) => ({
    path: module.path,
    name: module.name,
    component: resolveLazyPage(module.name),
    meta: {
      label: module.label,
      tagline: module.tagline,
      description: module.description,
      tips: module.tips,
    },
  })),
  {
    path: adminModule.path,
    name: adminModule.name,
    component: resolveLazyPage('admin'),
    meta: {
      label: adminModule.label,
      tagline: adminModule.tagline,
      description: adminModule.description,
      tips: adminModule.tips,
    },
    redirect: '/admin/users',
    children: [
      { path: 'users', name: 'admin-users', component: resolveLazyPage('admin-users') },
      { path: 'roles', name: 'admin-roles', component: resolveLazyPage('admin-roles') },
      { path: 'configs', name: 'admin-configs', component: resolveLazyPage('admin-configs') },
      { path: 'audits', name: 'admin-audits', component: resolveLazyPage('admin-audits') },
      { path: 'risks', name: 'admin-risks', component: resolveLazyPage('admin-risks') },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: consoleModules[0].path },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ left: 0, top: 0 }),
})

export default router

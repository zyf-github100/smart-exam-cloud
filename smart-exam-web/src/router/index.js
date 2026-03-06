import { createRouter, createWebHistory } from 'vue-router'
import { consoleModules } from './consoleModules'
import { getSavedUser } from '../api/client'
import {
  canAccessModule,
  canAccessRouteMeta,
  getDefaultAccessiblePath,
  hasAnyPermission,
} from '../composables/accessControl'

const adminModule = consoleModules.find((item) => item.name === 'admin')
const questionModule = consoleModules.find((item) => item.name === 'questions')
const normalModules = consoleModules.filter((item) => !['admin', 'questions'].includes(item.name))

if (!adminModule) {
  throw new Error('Admin module metadata is missing in consoleModules.')
}
if (!questionModule) {
  throw new Error('Question module metadata is missing in consoleModules.')
}

const lazyPages = {
  login: () => import('../components/auth/LoginWindow.vue'),
  connection: () => import('../components/console/ConnectionPanel.vue'),
  questions: () => import('../components/console/QuestionTab.vue'),
  'questions-create': () => import('../components/console/question/QuestionCreatePage.vue'),
  'questions-library': () => import('../components/console/question/QuestionLibraryPage.vue'),
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

const resolveQuestionHomePath = (user) => {
  if (hasAnyPermission(user, ['QUESTION_CREATE'])) return '/questions/create'
  if (hasAnyPermission(user, ['QUESTION_LIST', 'QUESTION_DETAIL'])) return '/questions/library'
  return getDefaultAccessiblePath(user)
}

const routes = [
  { path: '/', redirect: () => getDefaultAccessiblePath(getSavedUser()) },
  {
    path: '/login',
    name: 'login',
    component: resolveLazyPage('login'),
    meta: { public: true },
  },
  ...normalModules.map((module) => ({
    path: module.path,
    name: module.name,
    component: resolveLazyPage(module.name),
    meta: {
      moduleName: module.name,
      public: module.public,
      roles: module.roles,
      permissionsAny: module.permissionsAny,
      label: module.label,
      tagline: module.tagline,
      description: module.description,
      tips: module.tips,
    },
  })),
  {
    path: questionModule.path,
    name: questionModule.name,
    component: resolveLazyPage('questions'),
    meta: {
      moduleName: questionModule.name,
      public: questionModule.public,
      roles: questionModule.roles,
      permissionsAny: questionModule.permissionsAny,
      label: questionModule.label,
      tagline: questionModule.tagline,
      description: questionModule.description,
      tips: questionModule.tips,
    },
    redirect: () => resolveQuestionHomePath(getSavedUser()),
    children: [
      {
        path: 'create',
        name: 'questions-create',
        component: resolveLazyPage('questions-create'),
        meta: { moduleName: questionModule.name, roles: ['ADMIN', 'TEACHER'], permissionsAny: ['QUESTION_CREATE'] },
      },
      {
        path: 'library',
        name: 'questions-library',
        component: resolveLazyPage('questions-library'),
        meta: {
          moduleName: questionModule.name,
          roles: ['ADMIN', 'TEACHER'],
          permissionsAny: ['QUESTION_LIST', 'QUESTION_DETAIL'],
        },
      },
    ],
  },
  {
    path: adminModule.path,
    name: adminModule.name,
    component: resolveLazyPage('admin'),
    meta: {
      moduleName: adminModule.name,
      roles: adminModule.roles,
      label: adminModule.label,
      tagline: adminModule.tagline,
      description: adminModule.description,
      tips: adminModule.tips,
    },
    redirect: '/admin/users',
    children: [
      { path: 'users', name: 'admin-users', component: resolveLazyPage('admin-users'), meta: { moduleName: adminModule.name } },
      { path: 'roles', name: 'admin-roles', component: resolveLazyPage('admin-roles'), meta: { moduleName: adminModule.name } },
      { path: 'configs', name: 'admin-configs', component: resolveLazyPage('admin-configs'), meta: { moduleName: adminModule.name } },
      { path: 'audits', name: 'admin-audits', component: resolveLazyPage('admin-audits'), meta: { moduleName: adminModule.name } },
      { path: 'risks', name: 'admin-risks', component: resolveLazyPage('admin-risks'), meta: { moduleName: adminModule.name } },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ left: 0, top: 0 }),
})

router.beforeEach((to) => {
  const user = getSavedUser()
  if (to.path === '/login') {
    if (user) {
      return { path: getDefaultAccessiblePath(user), replace: true }
    }
    return true
  }
  if (!user) {
    const redirect = String(to.fullPath || '').trim()
    return {
      path: '/login',
      query: redirect && redirect !== '/login' ? { redirect } : undefined,
      replace: true,
    }
  }
  if (!canAccessRouteMeta(user, to.meta)) {
    if (String(to.path || '').startsWith('/questions/')) {
      const questionPath = resolveQuestionHomePath(user)
      if (questionPath !== to.path) {
        return { path: questionPath, replace: true }
      }
    }
    const fallbackPath = getDefaultAccessiblePath(user)
    if (to.path !== fallbackPath) {
      return { path: fallbackPath, replace: true }
    }
    return true
  }

  const moduleName = to.meta?.moduleName
  if (!moduleName) return true
  if (canAccessModule(user, moduleName)) return true
  const fallbackPath = getDefaultAccessiblePath(user)
  if (to.path === fallbackPath) return true
  return { path: fallbackPath, replace: true }
})

export default router

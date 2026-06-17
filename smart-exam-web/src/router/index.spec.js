import { describe, expect, it, vi } from 'vitest'

const authState = {
  user: null,
}

const loadRouterModule = async (user) => {
  authState.user = user
  vi.resetModules()
  vi.doMock('../api/client', () => ({
    getSessionUser: () => authState.user,
  }))
  return import('./index.js')
}

const teacherUser = (permissions = []) => ({
  id: 1,
  role: 'TEACHER',
  permissions,
})

describe('route guard resolution', () => {
  it('redirects anonymous users to login and preserves target path', async () => {
    const { resolveRouteGuard } = await loadRouterModule(null)

    const result = resolveRouteGuard({
      path: '/reports',
      fullPath: '/reports',
      meta: {},
    })

    expect(result).toEqual({
      path: '/login',
      query: { redirect: '/reports' },
      replace: true,
    })
  })

  it('redirects logged-in users away from the login page', async () => {
    const { resolveRouteGuard } = await loadRouterModule(teacherUser(['QUESTION_LIST']))

    const result = resolveRouteGuard({
      path: '/login',
      fullPath: '/login',
      meta: { public: true },
    })

    expect(result).toEqual({ path: '/connection', replace: true })
  })

  it('falls back to the question library when create permission is missing', async () => {
    const { resolveRouteGuard } = await loadRouterModule(teacherUser(['QUESTION_LIST']))

    const result = resolveRouteGuard({
      path: '/questions/create',
      fullPath: '/questions/create',
      meta: {
        moduleName: 'questions',
        roles: ['ADMIN', 'TEACHER'],
        permissionsAny: ['QUESTION_CREATE'],
      },
    })

    expect(result).toEqual({ path: '/questions/library', replace: true })
  })

  it('falls back to the default accessible module when a protected module is denied', async () => {
    const { resolveRouteGuard } = await loadRouterModule(teacherUser(['QUESTION_LIST']))

    const result = resolveRouteGuard({
      path: '/admin/users',
      fullPath: '/admin/users',
      meta: {
        moduleName: 'admin',
        roles: ['ADMIN'],
      },
    })

    expect(result).toEqual({ path: '/connection', replace: true })
  })
})

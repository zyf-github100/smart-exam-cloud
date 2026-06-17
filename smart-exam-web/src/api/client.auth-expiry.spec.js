import { beforeEach, describe, expect, it, vi } from 'vitest'

let responseFulfilled
let responseRejected

vi.mock('axios', () => ({
  default: {
    create: () => ({
      defaults: { baseURL: '/api/v1' },
      interceptors: {
        request: {
          use: vi.fn(),
        },
        response: {
          use: vi.fn((fulfilled, rejected) => {
            responseFulfilled = fulfilled
            responseRejected = rejected
          }),
        },
      },
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
    }),
  },
}))

const loadClientModule = async () => {
  vi.resetModules()
  responseFulfilled = undefined
  responseRejected = undefined
  return import('./client.js')
}

describe('api client auth expiration handling', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
  })

  it('clears session when business response returns auth-expired code', async () => {
    const { getSessionUser, getToken, setSavedUser, setToken } = await loadClientModule()

    setToken('token-123')
    setSavedUser({ id: 7, username: 'teacher001' })

    await expect(
      responseFulfilled({
        data: { code: 40100, message: 'session expired' },
        config: { url: '/users/me' },
      })
    ).rejects.toMatchObject({ authExpired: true })

    expect(getToken()).toBe('')
    expect(getSessionUser()).toBeNull()
  })

  it('clears session when transport layer returns unauthorized', async () => {
    const { getSessionUser, getToken, setSavedUser, setToken } = await loadClientModule()

    setToken('token-456')
    setSavedUser({ id: 9, username: 'student001' })

    await expect(
      responseRejected({
        response: {
          status: 401,
          data: { message: 'token invalid' },
        },
        config: { url: '/questions' },
        message: 'Request failed with status code 401',
      })
    ).rejects.toMatchObject({ authExpired: true })

    expect(getToken()).toBe('')
    expect(getSessionUser()).toBeNull()
  })
})

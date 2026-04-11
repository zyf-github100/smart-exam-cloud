import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const loadClientModule = async () => {
  vi.resetModules()
  return import('./client.js')
}

describe('api client auth helpers', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('persists auth session and exposes current user', async () => {
    const listener = vi.fn()
    const { AUTH_CHANGED_EVENT, clearAuth, getSessionUser, getToken, hasAuthSession, setSavedUser, setToken } =
      await loadClientModule()

    window.addEventListener(AUTH_CHANGED_EVENT, listener)

    setToken('token-123')
    setSavedUser({ id: 7, username: 'teacher001' })

    expect(getToken()).toBe('token-123')
    expect(getSessionUser()).toEqual({ id: 7, username: 'teacher001' })
    expect(hasAuthSession()).toBe(true)

    clearAuth()

    expect(getToken()).toBe('')
    expect(getSessionUser()).toBeNull()
    expect(hasAuthSession()).toBe(false)
    expect(listener).toHaveBeenCalledTimes(4)

    window.removeEventListener(AUTH_CHANGED_EVENT, listener)
  })

  it('falls back to the default api base when custom base is cleared', async () => {
    const { getApiBase, setApiBase } = await loadClientModule()

    expect(getApiBase()).toBe('/api/v1')

    setApiBase('https://example.com/gateway')
    expect(getApiBase()).toBe('https://example.com/gateway')
    expect(localStorage.getItem('smart_exam_api_base')).toBe('https://example.com/gateway')

    setApiBase('   ')
    expect(getApiBase()).toBe('/api/v1')
    expect(localStorage.getItem('smart_exam_api_base')).toBe('/api/v1')
  })
})

import { api, getSavedUser } from '../api/client'

const cacheStore = new Map()

const ttlByResource = {
  users: 60 * 1000,
  'published-exams': 30 * 1000,
  papers: 60 * 1000,
}

const normalizeText = (value) => String(value || '').trim()
const normalizeRole = (value) => normalizeText(value).toUpperCase()

const stableStringify = (value) => {
  if (value === null || value === undefined) return 'null'
  if (Array.isArray(value)) {
    return `[${value.map((item) => stableStringify(item)).join(',')}]`
  }
  if (typeof value === 'object') {
    return `{${Object.keys(value)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${stableStringify(value[key])}`)
      .join(',')}}`
  }
  return JSON.stringify(value)
}

const getScopeKey = () => {
  const user = getSavedUser()
  const role = normalizeText(user?.role).toUpperCase() || 'GUEST'
  const identifier = normalizeText(user?.id || user?.username) || 'ANON'
  return `${role}:${identifier}`
}

const buildCacheKey = (resource, params = {}) => `${getScopeKey()}::${resource}::${stableStringify(params)}`

const loadCachedResource = async (resource, loader, options = {}) => {
  const { force = false, params = {} } = options
  const cacheKey = buildCacheKey(resource, params)
  const ttlMs = ttlByResource[resource] || 30 * 1000
  const now = Date.now()
  const current = cacheStore.get(cacheKey)

  if (!force && current?.value !== undefined && now - current.updatedAt < ttlMs) {
    return current.value
  }

  if (current?.promise) {
    return current.promise
  }

  const requestPromise = Promise.resolve()
    .then(loader)
    .then((value) => {
      cacheStore.set(cacheKey, {
        value,
        updatedAt: Date.now(),
        promise: null,
      })
      return value
    })
    .catch((error) => {
      if (current?.value !== undefined) {
        cacheStore.set(cacheKey, {
          value: current.value,
          updatedAt: current.updatedAt,
          promise: null,
        })
      } else {
        cacheStore.delete(cacheKey)
      }
      throw error
    })

  cacheStore.set(cacheKey, {
    value: current?.value,
    updatedAt: current?.updatedAt || 0,
    promise: requestPromise,
  })

  return requestPromise
}

export const invalidateReferenceData = (resource) => {
  for (const key of cacheStore.keys()) {
    if (!resource || key.includes(`::${resource}::`)) {
      cacheStore.delete(key)
    }
  }
}

const filterVisibleUsers = (users) => {
  if (!Array.isArray(users)) return []
  const currentRole = normalizeRole(getSavedUser()?.role)
  if (currentRole === 'TEACHER') {
    return users.filter((item) => normalizeRole(item?.role) === 'STUDENT')
  }
  if (currentRole === 'STUDENT') {
    return []
  }
  return users
}

export const getUserDirectory = (options = {}) =>
  loadCachedResource('users', () => api.listUsers(), options).then(filterVisibleUsers)

export const getPublishedExamDirectory = (options = {}) =>
  loadCachedResource('published-exams', () => api.listPublishedExams(), options)

export const getPaperDirectory = (options = {}) => {
  const keyword = normalizeText(options.keyword)
  const page = Number(options.page) || 1
  const size = Number(options.size) || 200

  return loadCachedResource(
    'papers',
    () =>
      api.listPapers({
        keyword: keyword || undefined,
        page,
        size,
      }),
    {
      force: Boolean(options.force),
      params: {
        keyword,
        page,
        size,
      },
    }
  )
}

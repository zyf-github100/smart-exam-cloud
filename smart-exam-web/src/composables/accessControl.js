import { getSavedUser } from '../api/client'
import { consoleModules } from '../router/consoleModules'

const normalizeRole = (role) => String(role || '').trim().toUpperCase()

const permissionSetOf = (user) =>
  new Set((Array.isArray(user?.permissions) ? user.permissions : []).map((item) => String(item || '').trim().toUpperCase()))

export const hasAnyPermission = (user, codes = []) => {
  if (!Array.isArray(codes) || !codes.length) return true
  const role = normalizeRole(user?.role)
  if (role === 'ADMIN') return true
  const set = permissionSetOf(user)
  return codes.some((code) => set.has(String(code || '').trim().toUpperCase()))
}

const hasRole = (user, roles = []) => {
  if (!Array.isArray(roles) || !roles.length) return true
  const role = normalizeRole(user?.role)
  return roles.includes(role)
}

const resolveModule = (moduleOrName) => {
  if (!moduleOrName) return null
  if (typeof moduleOrName === 'string') {
    return consoleModules.find((item) => item.name === moduleOrName) || null
  }
  return moduleOrName
}

export const canAccessRouteMeta = (user, meta = {}) => {
  if (!meta || typeof meta !== 'object') return true
  if (meta.public) return true

  const hasRoleConstraint = Array.isArray(meta.roles) && meta.roles.length > 0
  const hasPermissionConstraint = Array.isArray(meta.permissionsAny) && meta.permissionsAny.length > 0

  if ((hasRoleConstraint || hasPermissionConstraint) && !user) return false
  if (hasRoleConstraint && !hasRole(user, meta.roles)) return false
  if (hasPermissionConstraint && !hasAnyPermission(user, meta.permissionsAny)) return false
  return true
}

export const canAccessModule = (user, moduleOrName) => {
  const module = resolveModule(moduleOrName)
  if (!module) return false
  return canAccessRouteMeta(user, module)
}

export const getAccessibleModules = (user = getSavedUser()) => consoleModules.filter((module) => canAccessModule(user, module))

export const getDefaultAccessiblePath = (user = getSavedUser()) => {
  if (!user) return '/login'
  return getAccessibleModules(user)[0]?.path || '/connection'
}

export const canAccessPath = (path, user = getSavedUser()) => {
  const module = consoleModules.find((item) => path === item.path || path.startsWith(`${item.path}/`))
  if (!module) return true
  return canAccessModule(user, module)
}

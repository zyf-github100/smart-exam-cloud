import { computed, proxyRefs, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/client'
import { useAsyncAction } from './useAsyncAction'

export const ADMIN_CONSOLE_KEY = Symbol('ADMIN_CONSOLE_KEY')

export const useAdminConsole = () => {
  const { loading, run } = useAsyncAction()

  const roleOptions = ['ADMIN', 'TEACHER', 'STUDENT']
  const riskLevelOptions = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
  const overview = ref(null)

  const userQuery = reactive({
    keyword: '',
    roleCode: '',
    status: '',
    page: 1,
    size: 10,
  })
  const userPage = ref({
    page: 1,
    size: 10,
    total: 0,
    records: [],
  })
  const selectedUserId = ref('')
  const userMutation = reactive({
    status: 1,
    roleCode: 'TEACHER',
    newPassword: '',
    reason: '',
  })

  const roles = ref([])
  const permissions = ref([])
  const selectedRoleCode = ref('')
  const rolePermissionSelection = ref([])

  const configQuery = reactive({
    groupKey: '',
    keyword: '',
  })
  const configList = ref([])
  const configForm = reactive({
    configKey: '',
    configValue: '',
    groupKey: 'SYSTEM',
    description: '',
  })

  const auditQuery = reactive({
    operatorId: '',
    action: '',
    targetType: '',
    page: 1,
    size: 10,
  })
  const auditRange = ref([])
  const auditPage = ref({
    page: 1,
    size: 10,
    total: 0,
    records: [],
  })

  const riskQuery = reactive({
    examId: '',
    riskLevel: '',
    page: 1,
    size: 10,
  })
  const riskPage = ref({
    page: 1,
    size: 10,
    total: 0,
    records: [],
  })
  const selectedRiskSessionId = ref('')
  const sessionRiskDetail = ref(null)

  const selectedUser = computed(() => userPage.value.records.find((item) => item.id === selectedUserId.value) || null)
  const permissionGroups = computed(() => {
    const groupMap = new Map()
    permissions.value.forEach((item) => {
      const key = item.moduleKey || 'GENERAL'
      if (!groupMap.has(key)) {
        groupMap.set(key, [])
      }
      groupMap.get(key).push(item)
    })
    return Array.from(groupMap.entries()).map(([moduleKey, items]) => ({ moduleKey, items }))
  })
  const selectedRoleDetail = computed(() => roles.value.find((item) => item.roleCode === selectedRoleCode.value) || null)

  const normalizeText = (value) => {
    const next = (value || '').trim()
    return next || undefined
  }

  const runMutation = async (key, action, successMessage) => {
    const result = await run(
      key,
      async () => {
        await action()
        return true
      },
      { successMessage }
    )
    return Boolean(result)
  }

  const syncRolePermissionSelection = () => {
    const role = roles.value.find((item) => item.roleCode === selectedRoleCode.value)
    if (!role) {
      rolePermissionSelection.value = []
      return
    }
    rolePermissionSelection.value = Array.isArray(role.permissions)
      ? role.permissions.map((item) => item.permissionCode)
      : []
  }

  watch(selectedRoleCode, () => {
    syncRolePermissionSelection()
  })

  const loadOverview = async () => {
    const data = await run('overview', () => api.adminOverview())
    if (data) {
      overview.value = data
    }
  }

  const loadUsers = async () => {
    const params = {
      keyword: normalizeText(userQuery.keyword),
      roleCode: normalizeText(userQuery.roleCode),
      status:
        userQuery.status === '' || userQuery.status === null || userQuery.status === undefined
          ? undefined
          : Number(userQuery.status),
      page: userQuery.page,
      size: userQuery.size,
    }
    const data = await run('users', () => api.adminUsers(params))
    if (data) {
      userPage.value = {
        page: data.page || userQuery.page,
        size: data.size || userQuery.size,
        total: data.total || 0,
        records: Array.isArray(data.records) ? data.records : [],
      }
      if (selectedUserId.value) {
        const stillExists = userPage.value.records.some((item) => item.id === selectedUserId.value)
        if (!stillExists) {
          selectedUserId.value = ''
        }
      }
    }
  }

  const selectUser = (user) => {
    selectedUserId.value = user.id
    userMutation.status = Number(user.status ?? 1)
    userMutation.roleCode = user.role || 'TEACHER'
    userMutation.reason = ''
  }

  const applyUserStatus = async () => {
    if (!selectedUser.value) {
      ElMessage.warning('请先在列表中选择用户')
      return
    }
    const ok = await runMutation(
      'userStatus',
      () =>
        api.adminUpdateUserStatus(selectedUser.value.id, {
          status: Number(userMutation.status),
          reason: userMutation.reason.trim(),
        }),
      '用户状态已更新'
    )
    if (!ok) return
    await Promise.all([loadOverview(), loadUsers()])
  }

  const applyUserRole = async () => {
    if (!selectedUser.value) {
      ElMessage.warning('请先在列表中选择用户')
      return
    }
    const nextRole = normalizeText(userMutation.roleCode)
    if (!nextRole) {
      ElMessage.warning('请选择目标角色')
      return
    }
    const ok = await runMutation(
      'userRole',
      () => api.adminUpdateUserRole(selectedUser.value.id, { roleCode: nextRole }),
      '用户角色已更新'
    )
    if (!ok) return
    await loadUsers()
  }

  const resetUserPassword = async () => {
    if (!selectedUser.value) {
      ElMessage.warning('请先在列表中选择用户')
      return
    }
    const password = userMutation.newPassword.trim()
    if (password.length < 6) {
      ElMessage.warning('新密码至少 6 位')
      return
    }
    try {
      await ElMessageBox.confirm(`确认重置用户 ${selectedUser.value.username} 的密码？`, '高风险操作', {
        type: 'warning',
      })
    } catch {
      return
    }
    const ok = await runMutation(
      'userPassword',
      () =>
        api.adminResetUserPassword(selectedUser.value.id, {
          newPassword: password,
          reason: userMutation.reason.trim(),
        }),
      '密码重置完成'
    )
    if (ok) {
      userMutation.newPassword = ''
    }
  }

  const loadRolesAndPermissions = async () => {
    const [roleData, permissionData] = await Promise.all([
      run('roles', () => api.adminRoles()),
      run('permissions', () => api.adminPermissions()),
    ])
    if (roleData) {
      roles.value = Array.isArray(roleData) ? roleData : []
    }
    if (permissionData) {
      permissions.value = Array.isArray(permissionData) ? permissionData : []
    }
    if (!selectedRoleCode.value && roles.value.length) {
      selectedRoleCode.value = roles.value[0].roleCode
    } else {
      syncRolePermissionSelection()
    }
  }

  const applyRolePermissions = async () => {
    if (!selectedRoleCode.value) {
      ElMessage.warning('请先选择角色')
      return
    }
    if (!rolePermissionSelection.value.length) {
      ElMessage.warning('至少保留一个权限')
      return
    }
    const ok = await runMutation(
      'rolePermissions',
      () =>
        api.adminUpdateRolePermissions(selectedRoleCode.value, {
          permissionCodes: rolePermissionSelection.value,
        }),
      '角色权限已更新'
    )
    if (!ok) return
    await loadRolesAndPermissions()
  }

  const loadConfigs = async () => {
    const params = {
      groupKey: normalizeText(configQuery.groupKey),
      keyword: normalizeText(configQuery.keyword),
    }
    const data = await run('configs', () => api.adminConfigs(params))
    if (data) {
      configList.value = Array.isArray(data.records) ? data.records : []
    }
  }

  const submitConfig = async () => {
    const configKey = configForm.configKey.trim().toUpperCase()
    if (!configKey) {
      ElMessage.warning('请输入配置键')
      return
    }
    if (!configForm.configValue.trim()) {
      ElMessage.warning('配置值不能为空')
      return
    }
    const ok = await runMutation(
      'configUpsert',
      () =>
        api.adminUpsertConfig(configKey, {
          configValue: configForm.configValue,
          groupKey: configForm.groupKey.trim().toUpperCase(),
          description: configForm.description.trim(),
        }),
      '系统配置已保存'
    )
    if (!ok) return
    await loadConfigs()
  }

  const loadAudits = async () => {
    const params = {
      operatorId: normalizeText(auditQuery.operatorId),
      action: normalizeText(auditQuery.action),
      targetType: normalizeText(auditQuery.targetType),
      startTime: auditRange.value?.[0] || undefined,
      endTime: auditRange.value?.[1] || undefined,
      page: auditQuery.page,
      size: auditQuery.size,
    }
    const data = await run('audits', () => api.adminAudits(params))
    if (data) {
      auditPage.value = {
        page: data.page || auditQuery.page,
        size: data.size || auditQuery.size,
        total: data.total || 0,
        records: Array.isArray(data.records) ? data.records : [],
      }
    }
  }

  const loadExamRisks = async () => {
    const examId = normalizeText(riskQuery.examId)
    if (!examId) {
      ElMessage.warning('请输入考试ID')
      return
    }

    const params = {
      riskLevel: normalizeText(riskQuery.riskLevel),
      page: riskQuery.page,
      size: riskQuery.size,
    }
    const data = await run('examRisks', () => api.examAntiCheatRisks(examId, params))
    if (data) {
      riskPage.value = {
        page: data.page || riskQuery.page,
        size: data.size || riskQuery.size,
        total: data.total || 0,
        records: Array.isArray(data.records) ? data.records : [],
      }
      if (selectedRiskSessionId.value) {
        const stillExists = riskPage.value.records.some((item) => item.sessionId === selectedRiskSessionId.value)
        if (!stillExists) {
          selectedRiskSessionId.value = ''
          sessionRiskDetail.value = null
        }
      }
    }
  }

  const loadSessionRisk = async () => {
    const sessionId = normalizeText(selectedRiskSessionId.value)
    if (!sessionId) {
      sessionRiskDetail.value = null
      return
    }
    const data = await run('sessionRisk', () => api.sessionAntiCheatRisk(sessionId))
    if (data) {
      sessionRiskDetail.value = data
    }
  }

  const selectRiskRecord = async (record) => {
    if (!record || !record.sessionId) return
    selectedRiskSessionId.value = record.sessionId
    await loadSessionRisk()
  }

  const init = async () => {
    await Promise.all([loadOverview(), loadUsers(), loadRolesAndPermissions(), loadConfigs(), loadAudits()])
  }

  init()

  return proxyRefs({
    loading,
    roleOptions,
    riskLevelOptions,
    overview,
    userQuery,
    userPage,
    selectedUserId,
    userMutation,
    roles,
    permissions,
    selectedRoleCode,
    rolePermissionSelection,
    configQuery,
    configList,
    configForm,
    auditQuery,
    auditRange,
    auditPage,
    riskQuery,
    riskPage,
    selectedRiskSessionId,
    sessionRiskDetail,
    selectedUser,
    permissionGroups,
    selectedRoleDetail,
    loadOverview,
    loadUsers,
    selectUser,
    applyUserStatus,
    applyUserRole,
    resetUserPassword,
    loadRolesAndPermissions,
    applyRolePermissions,
    loadConfigs,
    submitConfig,
    loadAudits,
    loadExamRisks,
    loadSessionRisk,
    selectRiskRecord,
  })
}

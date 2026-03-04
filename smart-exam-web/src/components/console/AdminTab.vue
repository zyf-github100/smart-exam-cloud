<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../api/client'
import { prettyJson, useAsyncAction } from '../../composables/useAsyncAction'

const { loading, run } = useAsyncAction()

const roleOptions = ['ADMIN', 'TEACHER', 'STUDENT']
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

watch(selectedRoleCode, () => {
  syncRolePermissionSelection()
})

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

const normalizeText = (value) => {
  const next = (value || '').trim()
  return next || undefined
}

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

const init = async () => {
  await Promise.all([loadOverview(), loadUsers(), loadRolesAndPermissions(), loadConfigs(), loadAudits()])
}

init()
</script>

<template>
  <div class="console-stage stack-gap">
    <section class="console-block">
      <div class="block-head compact">
        <h3 class="block-title">管理员总览</h3>
        <el-button :loading="loading.overview" @click="loadOverview">刷新概览</el-button>
      </div>
      <div class="metrics-grid cols-3">
        <article class="metric-card">
          <span>平台用户</span>
          <strong>{{ overview?.totalUsers || 0 }}</strong>
        </article>
        <article class="metric-card">
          <span>启用账号</span>
          <strong>{{ overview?.enabledUsers || 0 }}</strong>
        </article>
        <article class="metric-card">
          <span>停用账号</span>
          <strong>{{ overview?.disabledUsers || 0 }}</strong>
        </article>
        <article class="metric-card">
          <span>考试总量</span>
          <strong>{{ overview?.totalExams || 0 }}</strong>
        </article>
        <article class="metric-card">
          <span>进行中考试</span>
          <strong>{{ overview?.runningExams || 0 }}</strong>
        </article>
        <article class="metric-card">
          <span>24h 管理操作</span>
          <strong>{{ overview?.operationsInLast24Hours || 0 }}</strong>
        </article>
      </div>
      <p class="hint-text">概览生成时间：{{ overview?.generatedAt || '-' }}</p>
    </section>

    <section class="stage-layout two-panels">
      <section class="console-block">
        <div class="block-head">
          <div>
            <h3 class="block-title">用户治理</h3>
            <p class="block-sub">支持账号检索、启停用、角色切换、密码重置。</p>
          </div>
        </div>

        <div class="form-grid cols-3">
          <el-input v-model="userQuery.keyword" placeholder="用户名/姓名" />
          <el-select v-model="userQuery.roleCode" clearable placeholder="角色">
            <el-option label="ADMIN" value="ADMIN" />
            <el-option label="TEACHER" value="TEACHER" />
            <el-option label="STUDENT" value="STUDENT" />
          </el-select>
          <el-select v-model="userQuery.status" clearable placeholder="状态">
            <el-option label="ENABLED" :value="1" />
            <el-option label="DISABLED" :value="0" />
          </el-select>
        </div>

        <div class="action-row">
          <el-button type="primary" :loading="loading.users" @click="loadUsers">查询用户</el-button>
        </div>

        <el-table :data="userPage.records" size="small" max-height="230" @row-click="selectUser">
          <el-table-column prop="id" label="ID" min-width="120" />
          <el-table-column prop="username" label="用户名" min-width="110" />
          <el-table-column prop="realName" label="姓名" min-width="110" />
          <el-table-column prop="role" label="角色" width="100" />
          <el-table-column prop="statusLabel" label="状态" width="100" />
        </el-table>

        <el-pagination
          class="pagination-row"
          layout="prev, pager, next, total"
          :total="userPage.total"
          :page-size="userQuery.size"
          :current-page="userQuery.page"
          @current-change="
            (value) => {
              userQuery.page = value
              loadUsers()
            }
          "
        />

        <div class="mutate-panel">
          <p class="hint-text">当前选中：{{ selectedUser ? `${selectedUser.username} (${selectedUser.id})` : '未选择' }}</p>
          <div class="form-grid cols-2">
            <el-form-item label="状态">
              <el-select v-model="userMutation.status">
                <el-option label="ENABLED" :value="1" />
                <el-option label="DISABLED" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item label="角色">
              <el-select v-model="userMutation.roleCode">
                <el-option v-for="role in roleOptions" :key="role" :label="role" :value="role" />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item label="变更说明">
            <el-input v-model="userMutation.reason" placeholder="建议填写操作原因，便于审计追踪" />
          </el-form-item>
          <div class="action-row">
            <el-button type="primary" :loading="loading.userStatus" @click="applyUserStatus">更新状态</el-button>
            <el-button type="primary" plain :loading="loading.userRole" @click="applyUserRole">更新角色</el-button>
          </div>
          <el-form-item label="重置密码">
            <div class="password-row">
              <el-input
                v-model="userMutation.newPassword"
                type="password"
                show-password
                placeholder="输入新密码（至少 6 位）"
              />
              <el-button type="danger" :loading="loading.userPassword" @click="resetUserPassword">执行重置</el-button>
            </div>
          </el-form-item>
        </div>
      </section>

      <section class="stack-gap">
        <section class="console-block">
          <div class="block-head">
            <div>
              <h3 class="block-title">角色权限矩阵</h3>
              <p class="block-sub">按角色配置权限点，支持细粒度授权收敛。</p>
            </div>
            <el-button :loading="loading.roles || loading.permissions" @click="loadRolesAndPermissions">刷新</el-button>
          </div>

          <div class="query-row">
            <el-select v-model="selectedRoleCode" placeholder="选择角色">
              <el-option v-for="role in roles" :key="role.roleCode" :label="role.roleName" :value="role.roleCode" />
            </el-select>
            <el-button type="primary" :loading="loading.rolePermissions" @click="applyRolePermissions">保存权限</el-button>
          </div>

          <div class="permission-panel">
            <section v-for="group in permissionGroups" :key="group.moduleKey" class="permission-group">
              <p class="permission-group-title">{{ group.moduleKey }}</p>
              <el-checkbox-group v-model="rolePermissionSelection">
                <el-checkbox v-for="item in group.items" :key="item.permissionCode" :label="item.permissionCode">
                  {{ item.permissionName }}
                </el-checkbox>
              </el-checkbox-group>
            </section>
          </div>
          <pre class="json-block">{{ prettyJson(selectedRoleDetail) }}</pre>
        </section>

        <section class="console-block">
          <div class="block-head">
            <div>
              <h3 class="block-title">系统配置中心</h3>
              <p class="block-sub">统一管理管理员域配置并保留审计痕迹。</p>
            </div>
          </div>
          <div class="form-grid cols-2">
            <el-input v-model="configQuery.groupKey" placeholder="按分组过滤（如 SYSTEM）" />
            <el-input v-model="configQuery.keyword" placeholder="关键字检索" />
          </div>
          <div class="action-row">
            <el-button :loading="loading.configs" @click="loadConfigs">查询配置</el-button>
          </div>

          <el-table :data="configList" size="small" max-height="180">
            <el-table-column prop="groupKey" label="分组" width="110" />
            <el-table-column prop="configKey" label="键" min-width="170" />
            <el-table-column prop="configValue" label="值" min-width="200" show-overflow-tooltip />
          </el-table>

          <div class="config-form">
            <div class="form-grid cols-2">
              <el-input v-model="configForm.configKey" placeholder="配置键（大写）" />
              <el-input v-model="configForm.groupKey" placeholder="分组，默认 SYSTEM" />
            </div>
            <el-input
              v-model="configForm.configValue"
              type="textarea"
              :rows="3"
              placeholder="配置值，支持 JSON 字符串"
            />
            <el-input v-model="configForm.description" placeholder="描述（可选）" />
            <div class="action-row">
              <el-button type="primary" :loading="loading.configUpsert" @click="submitConfig">保存配置</el-button>
            </div>
          </div>
        </section>
      </section>
    </section>

    <section class="console-block">
      <div class="block-head">
        <div>
          <h3 class="block-title">审计日志检索</h3>
          <p class="block-sub">按操作人、行为、对象和时间窗查询管理员操作记录。</p>
        </div>
      </div>
      <div class="form-grid cols-3">
        <el-input v-model="auditQuery.operatorId" placeholder="操作人 ID" />
        <el-input v-model="auditQuery.action" placeholder="动作，如 USER_STATUS_UPDATED" />
        <el-input v-model="auditQuery.targetType" placeholder="对象类型，如 SYS_USER" />
      </div>
      <el-date-picker
        v-model="auditRange"
        type="datetimerange"
        range-separator="至"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        value-format="YYYY-MM-DDTHH:mm:ss"
      />
      <div class="action-row">
        <el-button type="primary" :loading="loading.audits" @click="loadAudits">查询日志</el-button>
      </div>
      <el-table :data="auditPage.records" size="small" max-height="260">
        <el-table-column prop="id" label="日志ID" min-width="130" />
        <el-table-column prop="operatorId" label="操作人" min-width="100" />
        <el-table-column prop="action" label="动作" min-width="180" />
        <el-table-column prop="targetType" label="对象" min-width="120" />
        <el-table-column prop="targetId" label="对象ID" min-width="120" />
        <el-table-column prop="createdAt" label="时间" min-width="160" />
      </el-table>
      <el-pagination
        class="pagination-row"
        layout="prev, pager, next, total"
        :total="auditPage.total"
        :page-size="auditQuery.size"
        :current-page="auditQuery.page"
        @current-change="
          (value) => {
            auditQuery.page = value
            loadAudits()
          }
        "
      />
      <pre class="json-block">{{ prettyJson(auditPage.records[0] || null) }}</pre>
    </section>
  </div>
</template>

<style scoped>
.pagination-row {
  margin-top: 10px;
}

.mutate-panel {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed rgba(175, 192, 180, 0.9);
}

.password-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
}

.permission-panel {
  display: grid;
  gap: 10px;
  margin-top: 8px;
  margin-bottom: 8px;
  max-height: 220px;
  overflow: auto;
  padding-right: 6px;
}

.permission-group {
  border: 1px solid rgba(199, 216, 204, 0.92);
  border-radius: 10px;
  background: rgba(249, 253, 250, 0.95);
  padding: 10px;
}

.permission-group-title {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  color: #3b5547;
}

.config-form {
  margin-top: 10px;
  display: grid;
  gap: 8px;
}

@media (max-width: 900px) {
  .password-row {
    grid-template-columns: 1fr;
  }
}
</style>

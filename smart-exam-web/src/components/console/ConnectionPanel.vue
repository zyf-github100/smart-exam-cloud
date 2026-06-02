<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, SwitchButton } from '@element-plus/icons-vue'
import {
  AUTH_CHANGED_EVENT,
  getApiBase,
  getSessionUser,
  setApiBase,
  api,
  getToken,
  clearAuth,
  setSavedUser,
} from '../../api/client'
import { hasAnyPermission } from '../../composables/accessControl'
import { getUserDirectory } from '../../composables/useReferenceData'
import { prettyJson, useAsyncAction } from '../../composables/useAsyncAction'

const { loading, run } = useAsyncAction()
const router = useRouter()

const apiBaseInput = ref(getApiBase())
const currentApiBase = ref(getApiBase())
const debugPanels = ref([])

const authState = reactive({
  token: getToken(),
  user: getSessionUser(),
})

const meData = ref(null)
const userList = ref([])
const authVersion = ref(0)
const userFilters = reactive({
  keyword: '',
  role: '',
  status: '',
})

const roleLabelMap = {
  ADMIN: '管理员',
  TEACHER: '教师',
  STUDENT: '学生',
}

const statusLabelMap = {
  ENABLED: '启用',
  DISABLED: '禁用',
}

const basePresets = [
  { label: '网关代理', value: '/api/v1' },
  { label: '本地直连', value: 'http://localhost:9000/api/v1' },
]

const isAuthenticated = computed(() => Boolean(authState.token))
const authTagType = computed(() => (isAuthenticated.value ? 'success' : 'info'))

const displayUser = computed(() => authState.user?.username || '-')
const displayRoleCode = computed(() => String(authState.user?.role || '').trim().toUpperCase())
const displayRole = computed(() => roleLabelMap[displayRoleCode.value] || displayRoleCode.value || '-')
const isProfileRole = computed(() => ['TEACHER', 'STUDENT'].includes(displayRoleCode.value))
const envSwitchOverride = String(import.meta.env.VITE_ENABLE_ENV_CONFIG || '').trim().toLowerCase() === 'true'
const canManageEnvConfig = computed(() => {
  if (isProfileRole.value) return false
  if (envSwitchOverride) return true
  if (import.meta.env.DEV) return true
  return displayRoleCode.value === 'ADMIN'
})
const showTechnicalPanel = computed(() => displayRoleCode.value === 'ADMIN')
const pageTitle = computed(() => (isProfileRole.value ? '个人信息' : '环境与登录工作台'))
const pageSubtitle = computed(() =>
  isProfileRole.value
    ? '查看当前登录账号、角色、姓名和账号状态。'
    : '把网关配置、登录动作和会话状态放在同一视图，减少来回切换。'
)
const actionCardTitle = computed(() => (isProfileRole.value ? '账号操作' : canManageEnvConfig.value ? '环境连接与会话' : '会话操作'))
const profileCardTitle = computed(() => (isProfileRole.value ? '个人资料' : '会话状态'))

const profileData = computed(() => meData.value?.profile || {})
const profileName = computed(() => profileData.value?.realName || authState.user?.realName || '-')
const profileId = computed(() => profileData.value?.id || authState.user?.id || '-')
const profileStatus = computed(() => {
  const code = String(profileData.value?.status || '').trim().toUpperCase()
  return statusLabelMap[code] || code || '-'
})
const profileInitial = computed(() => {
  const value = String(profileName.value !== '-' ? profileName.value : displayUser.value || '').trim()
  if (value) return value.slice(0, 1).toUpperCase()
  if (displayRoleCode.value === 'TEACHER') return '师'
  if (displayRoleCode.value === 'STUDENT') return '学'
  return '我'
})
const profileKicker = computed(() => (displayRole.value && displayRole.value !== '-' ? `${displayRole.value}账号` : '账号资料'))

const maskedToken = computed(() => {
  if (!authState.token) return '-'
  const value = authState.token
  if (value.length <= 32) return value
  return `${value.slice(0, 14)}...${value.slice(-12)}`
})

const sessionSummary = computed(() => [
  { label: '当前账号', value: displayUser.value },
  { label: '账号角色', value: displayRole.value },
  { label: '用户姓名', value: profileName.value },
  { label: '账号状态', value: profileStatus.value },
])

const normalizeRoleCode = (value) => String(value || '').trim().toUpperCase()

const normalizeStatusCode = (value) => {
  const text = String(value ?? '').trim().toUpperCase()
  if (text === '1' || text === 'ENABLED' || text === 'ACTIVE') return 'ENABLED'
  if (text === '0' || text === 'DISABLED' || text === 'INACTIVE') return 'DISABLED'
  return text
}

const roleTagType = (roleCode) => {
  if (roleCode === 'ADMIN') return 'danger'
  if (roleCode === 'TEACHER') return 'warning'
  if (roleCode === 'STUDENT') return 'success'
  return 'info'
}

const statusTagType = (statusCode) => {
  if (statusCode === 'ENABLED') return 'success'
  if (statusCode === 'DISABLED') return 'danger'
  return 'info'
}

const normalizedUsers = computed(() =>
  (Array.isArray(userList.value) ? userList.value : []).map((item) => {
    const roleCode = normalizeRoleCode(item?.role)
    const statusCode = normalizeStatusCode(item?.status)
    return {
      ...item,
      roleCode,
      roleLabel: roleLabelMap[roleCode] || roleCode || '-',
      statusCode,
      statusLabel: statusLabelMap[statusCode] || statusCode || '-',
      displayName: item?.realName || item?.real_name || item?.nickname || '-',
    }
  })
)

const roleOptions = computed(() => {
  const set = new Set(normalizedUsers.value.map((item) => item.roleCode).filter(Boolean))
  return [...set].sort().map((roleCode) => ({
    value: roleCode,
    label: roleLabelMap[roleCode] || roleCode,
  }))
})

const filteredUsers = computed(() => {
  const keyword = userFilters.keyword.trim().toLowerCase()
  return normalizedUsers.value.filter((item) => {
    if (userFilters.role && item.roleCode !== userFilters.role) return false
    if (userFilters.status && item.statusCode !== userFilters.status) return false
    if (!keyword) return true
    const text = `${item.id || ''} ${item.username || ''} ${item.displayName || ''}`.toLowerCase()
    return text.includes(keyword)
  })
})

const clearUserFilters = () => {
  userFilters.keyword = ''
  userFilters.role = ''
  userFilters.status = ''
}

const canViewUserDirectory = computed(() => {
  authVersion.value
  return hasAnyPermission(getSessionUser(), ['USER_LIST_VIEW'])
})

const applyBase = () => {
  if (!canManageEnvConfig.value) return
  setApiBase(apiBaseInput.value)
  currentApiBase.value = getApiBase()
  ElMessage.success('API 地址已更新')
}

const usePreset = (preset) => {
  if (!canManageEnvConfig.value) return
  apiBaseInput.value = preset
  applyBase()
}

const copyToken = async () => {
  if (!showTechnicalPanel.value) return
  if (!authState.token) {
    ElMessage.warning('当前没有可复制的 Token')
    return
  }
  try {
    await navigator.clipboard.writeText(authState.token)
    ElMessage.success('Token 已复制')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

const fetchMe = async () => {
  const data = await run('me', () => api.getMe())
  if (!data) return

  meData.value = data
  const mergedUser = {
    ...(authState.user || {}),
    id: data.id || data.profile?.id,
    role: data.role || data.profile?.role,
    username: data.username || authState.user?.username,
    realName: data.profile?.realName || data.profile?.nickname || authState.user?.realName,
    status: data.profile?.status || authState.user?.status,
  }
  authState.user = mergedUser
  setSavedUser(mergedUser)
}

const fetchUsers = async (options = {}) => {
  if (!canViewUserDirectory.value) {
    userList.value = []
    return
  }
  const data = await run('users', () => getUserDirectory(options))
  userList.value = Array.isArray(data) ? data : []
}

const goLogin = () => {
  router.push({ path: '/login', query: { redirect: '/connection' } })
}

const logout = async () => {
  await run('logout', () => api.logout())
  authState.token = ''
  authState.user = null
  meData.value = null
  userList.value = []
  clearAuth()
  router.replace('/login')
}

const refreshContext = async (options = {}) => {
  if (!authState.token) {
    ElMessage.warning('请先登录后再刷新上下文')
    return
  }
  await fetchMe()
  if (canViewUserDirectory.value) {
    await fetchUsers(options)
  }
}

const syncAuthState = () => {
  authVersion.value += 1
  authState.token = getToken()
  authState.user = getSessionUser()
}

onMounted(async () => {
  window.addEventListener(AUTH_CHANGED_EVENT, syncAuthState)
  if (authState.token) {
    await refreshContext({ force: false })
  }
})

onBeforeUnmount(() => {
  window.removeEventListener(AUTH_CHANGED_EVENT, syncAuthState)
})
</script>

<template>
  <div class="console-stage stack-gap">
    <section class="console-block">
      <div class="block-head connection-head">
        <div>
          <h3 class="block-title">{{ pageTitle }}</h3>
          <p class="block-sub">{{ pageSubtitle }}</p>
        </div>
        <el-tag :type="authTagType">{{ isAuthenticated ? '在线会话' : '未登录' }}</el-tag>
      </div>

      <section v-if="isProfileRole" class="student-profile-panel">
        <div class="student-profile-main">
          <div class="student-avatar">{{ profileInitial }}</div>
          <div class="student-identity">
            <p class="student-kicker">{{ profileKicker }}</p>
            <h4>{{ profileName }}</h4>
            <p class="student-meta">{{ displayUser }} · {{ displayRole }}</p>
            <div class="student-badges">
              <el-tag :type="authTagType">{{ isAuthenticated ? '在线会话' : '未登录' }}</el-tag>
              <el-tag :type="statusTagType(normalizeStatusCode(profileData.status || authState.user?.status))" effect="plain">
                {{ profileStatus }}
              </el-tag>
            </div>
          </div>
          <div class="student-actions">
            <el-button
              type="primary"
              :icon="Refresh"
              :loading="loading.me"
              @click="refreshContext({ force: true })"
            >
              刷新
            </el-button>
            <el-button plain :icon="SwitchButton" :loading="loading.logout" @click="logout">
              退出
            </el-button>
          </div>
        </div>

        <div class="student-info-grid">
          <article v-for="item in sessionSummary" :key="item.label" class="student-info-item">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </article>
        </div>
      </section>

      <div v-else class="connection-grid">
        <article class="connection-card">
          <h4>{{ actionCardTitle }}</h4>
          <el-form label-position="top">
            <template v-if="canManageEnvConfig">
              <el-form-item label="API Base">
                <el-input v-model="apiBaseInput" placeholder="/api/v1 或 http://localhost:9000/api/v1">
                  <template #append>
                    <el-button @click="applyBase">应用</el-button>
                  </template>
                </el-input>
                <div class="preset-row">
                  <el-button v-for="preset in basePresets" :key="preset.value" text @click="usePreset(preset.value)">
                    {{ preset.label }}
                  </el-button>
                </div>
                <p class="hint-text">当前地址：{{ currentApiBase }}</p>
              </el-form-item>
            </template>

            <div class="action-row">
              <el-button v-if="!isAuthenticated" type="primary" @click="goLogin">前往登录页</el-button>
              <template v-else>
                <el-button :loading="loading.logout" @click="logout">退出</el-button>
                <el-button :loading="loading.me || loading.users" @click="refreshContext({ force: true })">
                  刷新上下文
                </el-button>
              </template>
            </div>
          </el-form>
        </article>

        <article class="connection-card session-card">
          <h4>{{ profileCardTitle }}</h4>
          <el-empty v-if="!isAuthenticated" description="当前未登录，请先登录账号" :image-size="86">
            <el-button type="primary" @click="goLogin">前往登录页</el-button>
          </el-empty>

          <template v-else>
            <div class="metrics-grid cols-2">
              <article v-for="item in sessionSummary" :key="item.label" class="metric-card">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </article>
            </div>

            <template v-if="showTechnicalPanel">
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="用户ID">{{ profileId }}</el-descriptions-item>
                <el-descriptions-item label="角色代码">{{ displayRoleCode || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Token" :span="2">
                  <div class="token-row">
                    <span class="token-text">{{ maskedToken }}</span>
                    <el-button text @click="copyToken">复制</el-button>
                  </div>
                </el-descriptions-item>
              </el-descriptions>

              <el-collapse v-model="debugPanels" class="debug-collapse">
                <el-collapse-item name="debug" title="调试信息（JSON）">
                  <pre class="json-block">{{ prettyJson(meData || authState.user) }}</pre>
                </el-collapse-item>
              </el-collapse>
            </template>
          </template>
        </article>
      </div>
    </section>

    <section v-if="canViewUserDirectory" class="console-block">
      <div class="block-head compact">
        <div>
          <h3 class="block-title">用户目录</h3>
          <p class="block-sub">仅展示有权限可见的用户列表。</p>
        </div>
        <div class="action-row">
          <span class="hint-text">共 {{ filteredUsers.length }} / {{ normalizedUsers.length }} 人</span>
          <el-button text :loading="loading.users" @click="fetchUsers">刷新</el-button>
        </div>
      </div>

      <div class="directory-filters">
        <el-input
          v-model="userFilters.keyword"
          clearable
          placeholder="搜索 ID / 用户名 / 姓名"
        />
        <el-select v-model="userFilters.role" clearable placeholder="角色筛选">
          <el-option
            v-for="item in roleOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select v-model="userFilters.status" clearable placeholder="状态筛选">
          <el-option label="启用" value="ENABLED" />
          <el-option label="禁用" value="DISABLED" />
        </el-select>
        <el-button @click="clearUserFilters">清空筛选</el-button>
      </div>

      <el-table :data="filteredUsers" size="small" max-height="320">
        <el-table-column prop="id" label="ID" min-width="120" />
        <el-table-column prop="username" label="用户名" min-width="130" />
        <el-table-column prop="displayName" label="姓名" min-width="130" />
        <el-table-column label="角色" min-width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="roleTagType(row.roleCode)">{{ row.roleLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.statusCode)">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.connection-head {
  align-items: center;
}

.student-profile-panel {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--line-soft);
  border-radius: 16px;
  background:
    radial-gradient(360px 180px at 4% 0%, rgba(79, 147, 255, 0.12), transparent 68%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.84), rgba(247, 251, 255, 0.74));
}

.student-profile-main {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
}

.student-avatar {
  width: 84px;
  height: 84px;
  display: grid;
  place-items: center;
  border-radius: 24px;
  color: #fff;
  font-size: 34px;
  font-weight: 900;
  background: linear-gradient(140deg, #4f93ff, #67c8ff 58%, #ff8fad);
  box-shadow: 0 18px 32px rgba(79, 147, 255, 0.22);
}

.student-identity {
  min-width: 0;
}

.student-kicker {
  margin: 0 0 5px;
  color: var(--ink-muted);
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.student-identity h4 {
  margin: 0;
  color: var(--ink-main);
  font-size: clamp(24px, 2.1vw, 32px);
  line-height: 1.1;
}

.student-meta {
  margin: 7px 0 0;
  color: var(--ink-soft);
  font-size: 14px;
}

.student-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.student-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
}

.student-info-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.student-info-item {
  min-width: 0;
  display: grid;
  gap: 6px;
  padding: 14px;
  border-radius: 14px;
  border: 1px solid rgba(198, 217, 246, 0.88);
  background: linear-gradient(155deg, rgba(255, 255, 255, 0.92), rgba(241, 248, 255, 0.74));
  box-shadow: var(--inner-glow);
}

.student-info-item span {
  color: var(--ink-muted);
  font-size: 12px;
}

.student-info-item strong {
  min-width: 0;
  color: #355a9e;
  font-size: 18px;
  line-height: 1.2;
  overflow-wrap: anywhere;
}

.connection-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(0, 0.92fr);
  gap: 12px;
}

.connection-card {
  border: 1px solid var(--line-soft);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.72);
  padding: 12px;
}

.connection-card h4 {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 800;
  color: var(--ink-main);
}

.preset-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
}

.session-card {
  display: grid;
  gap: 10px;
  align-content: start;
}

.directory-filters {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 0.8fr) minmax(0, 0.8fr) auto;
  gap: 8px;
  margin-bottom: 10px;
}

.token-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.token-text {
  font-family: Consolas, Menlo, Monaco, monospace;
  font-size: 12px;
  color: var(--ink-soft);
  word-break: break-all;
}

.debug-collapse {
  border-top: 1px solid rgba(206, 220, 242, 0.9);
}

.debug-collapse :deep(.el-collapse-item__header) {
  font-size: 13px;
  color: var(--ink-soft);
}

.debug-collapse :deep(.el-collapse-item__content) {
  padding-bottom: 2px;
}

@media (max-width: 1100px) {
  .student-profile-main {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .student-actions {
    grid-column: 1 / -1;
    justify-content: flex-start;
  }

  .student-info-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .connection-grid {
    grid-template-columns: 1fr;
  }

  .directory-filters {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .student-profile-panel {
    padding: 12px;
  }

  .student-profile-main,
  .student-info-grid {
    grid-template-columns: 1fr;
  }

  .student-avatar {
    width: 72px;
    height: 72px;
    border-radius: 20px;
    font-size: 28px;
  }

  .student-actions {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>

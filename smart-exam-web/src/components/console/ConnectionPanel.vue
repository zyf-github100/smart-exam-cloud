<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { getApiBase, setApiBase, api, getToken, setToken, clearAuth, getSavedUser, setSavedUser } from '../../api/client'
import { prettyJson, useAsyncAction } from '../../composables/useAsyncAction'

const { loading, run } = useAsyncAction()

const apiBaseInput = ref(getApiBase())
const currentApiBase = ref(getApiBase())
const authForm = reactive({
  username: 'teacher1',
  password: '123456',
})
const authState = reactive({
  token: getToken(),
  user: getSavedUser(),
})
const meData = ref(null)
const userList = ref([])

const isAuthenticated = computed(() => Boolean(authState.token))
const authTagType = computed(() => (isAuthenticated.value ? 'success' : 'warning'))
const displayRole = computed(() => authState.user?.role || '-')
const displayUser = computed(() => authState.user?.username || '-')
const maskedToken = computed(() => {
  if (!authState.token) return '-'
  const value = authState.token
  if (value.length <= 24) return value
  return `${value.slice(0, 10)}...${value.slice(-10)}`
})

const basePresets = [
  { label: '网关代理', value: '/api/v1' },
  { label: '本地直连', value: 'http://localhost:9000/api/v1' },
]

const applyBase = () => {
  setApiBase(apiBaseInput.value)
  currentApiBase.value = getApiBase()
}

const usePreset = (preset) => {
  apiBaseInput.value = preset
  applyBase()
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
    nickname: data.profile?.nickname,
    status: data.profile?.status,
  }
  authState.user = mergedUser
  setSavedUser(mergedUser)
}

const fetchUsers = async () => {
  const data = await run('users', () => api.listUsers())
  if (data) {
    userList.value = Array.isArray(data) ? data : []
  }
}

const login = async () => {
  const data = await run('login', () => api.login(authForm), { successMessage: '登录成功' })
  if (!data) return
  authState.token = data.token
  authState.user = data.user
  setToken(data.token)
  setSavedUser(data.user)
  await fetchMe()
  await fetchUsers()
}

const logout = async () => {
  await run('logout', () => api.logout(), { successMessage: '已退出登录' })
  authState.token = ''
  authState.user = null
  meData.value = null
  userList.value = []
  clearAuth()
}

const refreshContext = async () => {
  await fetchMe()
  await fetchUsers()
}

onMounted(async () => {
  if (authState.token) {
    await refreshContext()
  }
})
</script>

<template>
  <div class="console-stage stack-gap">
    <section class="console-block">
      <div class="block-head">
        <div>
          <h3 class="block-title">环境与登录</h3>
          <p class="block-sub">配置网关地址与当前操作员身份。</p>
        </div>
        <el-tag :type="authTagType">{{ isAuthenticated ? '在线会话' : '未登录' }}</el-tag>
      </div>

      <el-form label-position="top">
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

        <div class="form-grid cols-2">
          <el-form-item label="用户名">
            <el-input v-model="authForm.username" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="authForm.password" type="password" show-password />
          </el-form-item>
        </div>

        <div class="action-row">
          <el-button type="primary" :loading="loading.login" @click="login">登录</el-button>
          <el-button :loading="loading.logout" @click="logout">退出</el-button>
          <el-button :loading="loading.me || loading.users" @click="refreshContext">刷新上下文</el-button>
        </div>
      </el-form>
    </section>

    <section class="console-block">
      <div class="block-head compact">
        <h3 class="block-title">会话摘要</h3>
      </div>
      <div class="metrics-grid cols-2">
        <article class="metric-card">
          <span>当前账号</span>
          <strong>{{ displayUser }}</strong>
        </article>
        <article class="metric-card">
          <span>当前角色</span>
          <strong>{{ displayRole }}</strong>
        </article>
      </div>
      <p class="hint-text">Token：{{ maskedToken }}</p>
      <pre class="json-block">{{ prettyJson(meData || authState.user) }}</pre>
    </section>

    <section class="console-block">
      <div class="block-head compact">
        <h3 class="block-title">用户目录</h3>
        <el-button text :loading="loading.users" @click="fetchUsers">刷新</el-button>
      </div>
      <el-table :data="userList" size="small" height="240">
        <el-table-column prop="id" label="ID" min-width="120" />
        <el-table-column prop="username" label="用户名" min-width="100" />
        <el-table-column prop="role" label="角色" min-width="90" />
        <el-table-column prop="status" label="状态" min-width="90" />
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.preset-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
}
</style>